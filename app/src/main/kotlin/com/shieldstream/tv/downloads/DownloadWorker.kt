package com.shieldstream.tv.downloads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.shieldstream.tv.R
import com.shieldstream.tv.domain.DownloadsRepository
import com.shieldstream.tv.model.Download
import com.shieldstream.tv.model.DownloadStatus
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Descarga un fichero al disco, retomando donde se quedó (cabecera Range) si se corta o se pausa.
 * De una en una: las demás esperan en cola.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val downloads: DownloadsRepository,
    private val resolveStream: com.shieldstream.tv.domain.ResolveStreamUseCase,
    baseClient: OkHttpClient,
    private val texts: Texts,
) : CoroutineWorker(context, params) {

    private val client = baseClient.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        return ONE_AT_A_TIME.withLock {
            val d = downloads.find(id) ?: return@withLock Result.success()
            if (d.status == DownloadStatus.PAUSED || d.isDone) return@withLock Result.success()
            runCatching { setForeground(foregroundInfo(d, 0)) }
            downloads.update(id) { it.copy(status = DownloadStatus.RUNNING, error = null) }
            try {
                // El enlace se pide ahora (no al ponerlo en cola) y, si caduca a medias, se pide otro una vez.
                var current = if (d.url.isBlank()) withFreshLink(d) else d
                try {
                    withContext(Dispatchers.IO) { transfer(current) }
                } catch (e: LinkExpired) {
                    if (current.source == null) throw e
                    current = withFreshLink(current)
                    withContext(Dispatchers.IO) { transfer(current) }
                }
                Result.success()
            } catch (e: LinkExpired) {
                downloads.update(id) { it.copy(status = DownloadStatus.FAILED, error = texts.get(StringsR.string.downloads_link_expired)) }
                Result.failure()
            } catch (e: IOException) {
                if (isStopped) return@withLock Result.success()
                Log.w(TAG, "Descarga interrumpida (${e.message}); se reintenta")
                if (runAttemptCount >= MAX_ATTEMPTS) {
                    downloads.update(id) { it.copy(status = DownloadStatus.FAILED, error = e.message) }
                    Result.failure()
                } else {
                    downloads.update(id) { it.copy(status = DownloadStatus.QUEUED) }
                    Result.retry()
                }
            }
        }
    }

    private suspend fun withFreshLink(d: Download): Download {
        val source = d.source ?: throw LinkExpired()
        val terminal = resolveStream(source).first {
            it is com.shieldstream.tv.domain.ResolveState.Ready || it is com.shieldstream.tv.domain.ResolveState.Failed
        }
        val url = (terminal as? com.shieldstream.tv.domain.ResolveState.Ready)?.url ?: throw LinkExpired()
        downloads.update(d.id) { it.copy(url = url) }
        return d.copy(url = url)
    }

    private suspend fun transfer(d: Download) {
        val file = File(d.filePath)
        file.parentFile?.mkdirs()
        var have = if (file.exists()) file.length() else 0L
        val request = Request.Builder().url(d.url).apply { if (have > 0) header("Range", "bytes=$have-") }.build()
        client.newCall(request).execute().use { response ->
            if (response.code == 416 && d.totalBytes > 0 && have >= d.totalBytes) {
                finish(d.id, have)
                return
            }
            if (response.code in setOf(401, 403, 404, 410)) throw LinkExpired()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Sin contenido")
            if (have > 0 && response.code != 206) {
                // El servidor no permite retomar: se empieza de cero.
                have = 0
            }
            val total = when {
                response.code == 206 -> response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                else -> body.contentLength().takeIf { it > 0 }
            } ?: d.totalBytes
            downloads.update(d.id) { it.copy(totalBytes = total, downloadedBytes = have) }
            FileOutputStream(file, have > 0).use { out ->
                val buffer = ByteArray(BUFFER)
                val input = body.byteStream()
                var lastUi = 0L
                var lastNotice = 0L
                while (true) {
                    if (isStopped || !currentCoroutineContext().isActive) return
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    have += read
                    val now = System.currentTimeMillis()
                    if (now - lastUi > UI_INTERVAL_MS) {
                        lastUi = now
                        val done = have
                        downloads.update(d.id) { it.copy(downloadedBytes = done) }
                    }
                    if (now - lastNotice > NOTIFICATION_INTERVAL_MS && total > 0) {
                        lastNotice = now
                        runCatching { setForeground(foregroundInfo(d, ((have * 100) / total).toInt())) }
                    }
                }
            }
            if (total > 0 && have < total) throw IOException("Incompleto: $have de $total")
            finish(d.id, have)
        }
    }

    @android.annotation.SuppressLint("MissingPermission") // runCatching cubre el permiso de notificaciones denegado.
    private fun finish(id: String, bytes: Long) {
        downloads.update(id) { it.copy(status = DownloadStatus.DONE, downloadedBytes = bytes, totalBytes = maxOf(it.totalBytes, bytes)) }
        val d = downloads.find(id) ?: return
        runCatching {
            NotificationManagerCompat.from(applicationContext).notify(
                id.hashCode(),
                NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle(texts.get(StringsR.string.downloads_finished))
                    .setContentText(listOfNotNull(d.title, d.episodeLabel).joinToString(" · "))
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }

    private fun foregroundInfo(d: Download, percent: Int): ForegroundInfo {
        createChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(texts.get(StringsR.string.downloads_in_progress))
            .setContentText(listOfNotNull(d.title, d.episodeLabel).joinToString(" · "))
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(CHANNEL_ID, texts.get(StringsR.string.downloads_title), NotificationManager.IMPORTANCE_LOW)
        NotificationManagerCompat.from(applicationContext).createNotificationChannel(channel)
    }

    private class LinkExpired : Exception()

    companion object {
        const val KEY_ID = "id"
        private const val TAG = "Downloads"
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 7301
        private const val BUFFER = 256 * 1024
        private const val UI_INTERVAL_MS = 1_000L
        private const val NOTIFICATION_INTERVAL_MS = 5_000L
        private const val MAX_ATTEMPTS = 6
        private val ONE_AT_A_TIME = Mutex()
    }
}
