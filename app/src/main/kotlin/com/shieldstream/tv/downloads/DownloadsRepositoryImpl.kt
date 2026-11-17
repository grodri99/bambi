package com.shieldstream.tv.downloads

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.shieldstream.tv.domain.DownloadEnqueueResult
import com.shieldstream.tv.domain.DownloadsRepository
import com.shieldstream.tv.model.Download
import com.shieldstream.tv.model.DownloadStatus
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.StorageOption
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Descargas para ver sin conexión. Se guardan en la carpeta de la app dentro del almacenamiento elegido en Ajustes
 * (memoria interna o un USB/tarjeta), en una subcarpeta "Bambi descargas". No hacen falta permisos de
 * almacenamiento; a cambio, Android borra esa carpeta si se desinstala la app.
 */
@Singleton
class DownloadsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val texts: Texts,
) : DownloadsRepository {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Any()
    private val _downloads = MutableStateFlow(load())
    override val downloads: StateFlow<List<Download>> = _downloads.asStateFlow()

    private val _storagePath = MutableStateFlow(resolveStorage(prefs.getString(KEY_STORAGE, null)))
    override val storagePath: StateFlow<String?> = _storagePath.asStateFlow()

    private var lastPersist = 0L

    init {
        // Lo que estaba en marcha cuando se cerró la app sigue en cola (WorkManager lo retoma).
        _downloads.value.filter { it.status == DownloadStatus.RUNNING }.forEach { d ->
            update(d.id) { it.copy(status = DownloadStatus.QUEUED) }
        }
    }

    override fun storageOptions(): List<StorageOption> {
        val dirs = runCatching { context.getExternalFilesDirs(null).toList() }.getOrDefault(emptyList())
            .filterNotNull()
            .filter { runCatching { Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }.getOrDefault(true) }
            .ifEmpty { listOf(context.filesDir) }
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        return dirs.mapIndexed { index, dir ->
            val removable = index > 0 && runCatching { Environment.isExternalStorageRemovable(dir) }.getOrDefault(true)
            val description = if (Build.VERSION.SDK_INT >= 24) {
                runCatching { storageManager?.getStorageVolume(dir)?.getDescription(context) }.getOrNull()
            } else {
                null
            }
            val label = when {
                index == 0 -> texts.get(StringsR.string.downloads_storage_internal)
                !description.isNullOrBlank() -> description
                else -> texts.get(StringsR.string.downloads_storage_usb)
            }
            val stat = runCatching { StatFs(dir.path) }.getOrNull()
            StorageOption(
                path = File(dir, FOLDER).path,
                label = label,
                removable = removable,
                freeBytes = stat?.availableBytes ?: 0L,
                totalBytes = stat?.totalBytes ?: 0L,
            )
        }
    }

    override fun selectStorage(path: String) {
        prefs.edit().putString(KEY_STORAGE, path).apply()
        _storagePath.value = resolveStorage(path)
    }

    /** La carpeta guardada si sigue montada; si no (USB quitado), la primera disponible. */
    private fun resolveStorage(saved: String?): String? {
        val options = storageOptions()
        return options.firstOrNull { it.path == saved }?.path ?: options.firstOrNull()?.path
    }

    override fun enqueue(download: Download, expectedBytes: Long): DownloadEnqueueResult {
        find(download.id)?.let { existing ->
            when {
                existing.isDone && File(existing.filePath).exists() -> return DownloadEnqueueResult.AlreadyDownloaded
                existing.isActive -> return DownloadEnqueueResult.AlreadyRunning
                else -> delete(existing.id)
            }
        }
        val folder = resolveStorage(prefs.getString(KEY_STORAGE, null)) ?: return DownloadEnqueueResult.NoStorage
        _storagePath.value = folder
        val dir = File(folder)
        if (!dir.exists() && !dir.mkdirs()) return DownloadEnqueueResult.NoStorage
        val free = runCatching { StatFs(dir.path).availableBytes }.getOrDefault(Long.MAX_VALUE)
        // Margen de 500 MB para no dejar la Shield sin sitio.
        if (expectedBytes > 0 && expectedBytes + SPACE_MARGIN > free) {
            return DownloadEnqueueResult.NoSpace(expectedBytes, free)
        }
        val file = uniqueFile(dir, download.filePath)
        val entry = download.copy(
            filePath = file.path,
            totalBytes = expectedBytes,
            downloadedBytes = 0,
            status = DownloadStatus.QUEUED,
            error = null,
            createdAt = System.currentTimeMillis(),
        )
        synchronized(lock) { store(_downloads.value.filterNot { it.id == entry.id } + entry, force = true) }
        schedule(entry.id)
        return DownloadEnqueueResult.Started
    }

    private fun uniqueFile(dir: File, name: String): File {
        var file = File(dir, name)
        var n = 2
        while (file.exists()) {
            file = File(dir, name.substringBeforeLast('.') + " ($n)." + name.substringAfterLast('.'))
            n++
        }
        return file
    }

    private fun schedule(id: String) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_ID to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, request)
    }

    override fun find(id: String): Download? = _downloads.value.firstOrNull { it.id == id }

    override fun localFileFor(id: String): String? =
        find(id)?.takeIf { it.isDone }?.filePath?.takeIf { File(it).exists() }

    override fun pause(id: String) {
        val d = find(id) ?: return
        if (!d.isActive) return
        update(id) { it.copy(status = DownloadStatus.PAUSED) }
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
    }

    override fun resume(id: String) {
        val d = find(id) ?: return
        if (d.isDone || d.isActive) return
        update(id) { it.copy(status = DownloadStatus.QUEUED, error = null) }
        schedule(id)
    }

    override fun delete(id: String) {
        val d = find(id) ?: return
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
        runCatching { File(d.filePath).delete() }
        synchronized(lock) { store(_downloads.value.filterNot { it.id == id }, force = true) }
    }

    override fun update(id: String, transform: (Download) -> Download) {
        synchronized(lock) {
            val current = _downloads.value
            if (current.none { it.id == id }) return
            val next = current.map { if (it.id == id) transform(it) else it }
            val statusChanged = current.firstOrNull { it.id == id }?.status != next.firstOrNull { it.id == id }?.status
            store(next, force = statusChanged)
        }
    }

    /** El progreso cambia muchas veces por segundo: a disco solo cada pocos segundos o al cambiar de estado. */
    private fun store(list: List<Download>, force: Boolean) {
        _downloads.value = list
        val now = System.currentTimeMillis()
        if (!force && now - lastPersist < PERSIST_INTERVAL_MS) return
        lastPersist = now
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_LIST, array.toString()).apply()
    }

    private fun load(): List<Download> = runCatching {
        val array = JSONArray(prefs.getString(KEY_LIST, null) ?: return emptyList())
        (0 until array.length()).mapNotNull { runCatching { array.getJSONObject(it).toDownload() }.getOrNull() }
    }.getOrDefault(emptyList())

    private fun Download.toJson() = JSONObject().apply {
        put("id", id); put("itemId", itemId); put("type", type.name); put("title", title)
        putOpt("episodeLabel", episodeLabel); putOpt("season", season); putOpt("episode", episode)
        putOpt("imdbId", imdbId); putOpt("year", year); putOpt("originalTitle", originalTitle)
        putOpt("posterUrl", posterUrl); putOpt("backdropUrl", backdropUrl)
        put("url", url); put("filePath", filePath); put("sourceTitle", sourceTitle)
        source?.let { put("source", it.toJson()) }
        put("totalBytes", totalBytes); put("downloadedBytes", downloadedBytes); put("status", status.name)
        putOpt("error", error); put("createdAt", createdAt)
    }

    private fun JSONObject.toDownload() = Download(
        id = getString("id"),
        itemId = getString("itemId"),
        type = MediaType.valueOf(getString("type")),
        title = getString("title"),
        episodeLabel = optStringOrNull("episodeLabel"),
        season = if (has("season")) getInt("season") else null,
        episode = if (has("episode")) getInt("episode") else null,
        imdbId = optStringOrNull("imdbId"),
        year = if (has("year")) getInt("year") else null,
        originalTitle = optStringOrNull("originalTitle"),
        posterUrl = optStringOrNull("posterUrl"),
        backdropUrl = optStringOrNull("backdropUrl"),
        url = optString("url"),
        source = optJSONObject("source")?.let { runCatching { it.toSource() }.getOrNull() },
        filePath = getString("filePath"),
        sourceTitle = optString("sourceTitle"),
        totalBytes = optLong("totalBytes"),
        downloadedBytes = optLong("downloadedBytes"),
        status = runCatching { DownloadStatus.valueOf(getString("status")) }.getOrDefault(DownloadStatus.FAILED),
        error = optStringOrNull("error"),
        createdAt = optLong("createdAt"),
    )

    private fun com.shieldstream.tv.model.StreamSource.toJson() = JSONObject().apply {
        put("id", id); put("title", title); put("infoHash", infoHash); putOpt("fileIdx", fileIdx); put("magnet", magnet)
        put("quality", quality.name); put("hdrType", hdrType.name); put("sizeBytes", sizeBytes); put("isCached", isCached)
        putOpt("directUrl", directUrl); putOpt("fileName", fileName); putOpt("resolveUrl", resolveUrl); putOpt("debrid", debrid)
        put("provider", provider); putOpt("season", season); putOpt("episode", episode)
    }

    private fun JSONObject.toSource() = com.shieldstream.tv.model.StreamSource(
        id = getString("id"),
        title = getString("title"),
        infoHash = optString("infoHash"),
        fileIdx = if (has("fileIdx")) getInt("fileIdx") else null,
        magnet = optString("magnet"),
        quality = runCatching { com.shieldstream.tv.model.Quality.valueOf(getString("quality")) }.getOrDefault(com.shieldstream.tv.model.Quality.UNKNOWN),
        hdrType = runCatching { com.shieldstream.tv.model.HdrType.valueOf(getString("hdrType")) }.getOrDefault(com.shieldstream.tv.model.HdrType.SDR),
        sizeBytes = optLong("sizeBytes"),
        seeders = null,
        isCached = optBoolean("isCached"),
        directUrl = optStringOrNull("directUrl"),
        fileName = optStringOrNull("fileName"),
        resolveUrl = optStringOrNull("resolveUrl"),
        debrid = optStringOrNull("debrid"),
        provider = optString("provider"),
        season = if (has("season")) getInt("season") else null,
        episode = if (has("episode")) getInt("episode") else null,
    )

    private fun JSONObject.optStringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null

    companion object {
        private const val PREFS = "downloads"
        private const val KEY_LIST = "list"
        private const val KEY_STORAGE = "storage_path"
        private const val FOLDER = "Bambi descargas"
        private const val SPACE_MARGIN = 500L * 1024 * 1024
        private const val PERSIST_INTERVAL_MS = 3_000L
        const val WORK_TAG = "downloads"
        fun workName(id: String) = "download_$id"
    }
}
