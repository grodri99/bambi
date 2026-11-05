package com.shieldstream.tv.data.update

import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import com.shieldstream.tv.domain.AppUpdate
import com.shieldstream.tv.domain.AppVersion
import com.shieldstream.tv.domain.UpdateRepository
import com.shieldstream.tv.domain.UpdateState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@Singleton
class UpdateRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    base: OkHttpClient,
    private val json: Json,
    private val version: AppVersion,
    private val texts: Texts,
) : UpdateRepository {

    private val client = base.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<UpdateState>(if (version.isDebug) UpdateState.Unsupported else UpdateState.Idle)
    override val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _promptDismissed = MutableStateFlow(false)
    override val promptDismissed: StateFlow<Boolean> = _promptDismissed.asStateFlow()

    private var job: Job? = null

    @Volatile private var lastApk: File? = null
    @Volatile private var lastWithDm = false

    init {
        UpdateInstallReceiver.onFailure = { status, message ->
            val update = (_state.value as? UpdateState.Installing)?.update
            val apk = lastApk
            if (lastWithDm && status != PackageInstaller.STATUS_FAILURE_ABORTED && apk != null && apk.exists()) {
                Log.w(TAG, "Instalación con perfil fallida ($status): se reintenta sin él")
                scope.launch {
                    runCatching { install(apk, withProfile = false) }
                        .onFailure { _state.value = UpdateState.Failed(message, update) }
                }
            } else {
                _state.value = UpdateState.Failed(message, update)
            }
        }
    }

    override fun check(manual: Boolean) {
        if (version.isDebug) return
        if (job?.isActive == true) return
        val now = System.currentTimeMillis()
        if (!manual && now - prefs.getLong(KEY_LAST_CHECK, 0) < AUTO_CHECK_INTERVAL_MS) return
        job = scope.launch {
            if (manual) _state.value = UpdateState.Checking
            runCatching { fetchLatest() }
                .onSuccess { latest ->
                    prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
                    _state.value = if (latest != null && isNewer(latest.version, version.name)) {
                        if (manual) _promptDismissed.value = false
                        UpdateState.Available(version.name, latest)
                    } else {
                        UpdateState.UpToDate(version.name)
                    }
                }
                .onFailure {
                    Log.w(TAG, "No se pudo comprobar: ${it.message}")
                    _state.value = if (manual) {
                        UpdateState.Failed(texts.get(StringsR.string.data_couldn_t_check_for_a_new_version), null)
                    } else {
                        UpdateState.Idle
                    }
                }
        }
    }

    override fun downloadAndInstall() {
        val update = when (val s = _state.value) {
            is UpdateState.Available -> s.update
            is UpdateState.NeedsPermission -> s.update
            is UpdateState.Failed -> s.update ?: return
            else -> return
        }
        if (job?.isActive == true) return
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsPermission(update)
            return
        }
        job = scope.launch {
            runCatching {
                val apk = download(update)
                _state.value = UpdateState.Installing(update)
                install(apk)
            }.onFailure {
                Log.w(TAG, "Actualización fallida: ${it.message}")
                _state.value = UpdateState.Failed(
                    texts.get(StringsR.string.data_couldn_t_download_the_update_try_again),
                    update,
                )
            }
        }
    }

    override fun dismissPrompt() {
        _promptDismissed.value = true
    }

    // ------------------------------------------------------------------ GitHub

    private fun fetchLatest(): AppUpdate? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${version.updateRepo}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Franklix/${version.name}")
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            check(response.isSuccessful) { "GitHub HTTP ${response.code}" }
            response.body?.string().orEmpty()
        }
        val release = json.parseToJsonElement(body) as? JsonObject ?: return null
        if (release["draft"]?.jsonPrimitive?.contentOrNull == "true") return null
        val tag = release["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
        val assets = (release["assets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val apk = assets.firstOrNull { it.string("name") == APK_NAME }
            ?: assets.firstOrNull { it.string("name")?.endsWith(".apk", ignoreCase = true) == true }
            ?: return null
        return AppUpdate(
            version = tag.removePrefix("v"),
            notes = release["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            apkUrl = apk.string("browser_download_url") ?: return null,
            sizeBytes = apk["size"]?.jsonPrimitive?.longOrNull ?: 0L,
        )
    }

    private fun download(update: AppUpdate): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // APK de actualizaciones anteriores
        val file = File(dir, "Franklix-${update.version}.apk")
        _state.value = UpdateState.Downloading(update, 0f)
        val request = Request.Builder().url(update.apkUrl).header("User-Agent", "Franklix/${version.name}").build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Descarga HTTP ${response.code}" }
            val body = checkNotNull(response.body)
            val total = body.contentLength().takeIf { it > 0 } ?: update.sizeBytes
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    var lastShown = 0f
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        if (total > 0) {
                            val progress = (read.toFloat() / total).coerceIn(0f, 1f)
                            if (progress - lastShown >= 0.01f) {
                                lastShown = progress
                                _state.value = UpdateState.Downloading(update, progress)
                            }
                        }
                    }
                }
            }
        }
        return file
    }

    private fun install(apk: File, withProfile: Boolean = true) {
        val dm = if (withProfile) runCatching { dexMetadata(apk) }.getOrNull() else null
        lastApk = apk
        lastWithDm = dm != null
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(context.packageName) }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("franklix.apk", 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            if (dm != null) {
                session.openWrite("franklix.dm", 0, dm.size.toLong()).use { output ->
                    output.write(dm)
                    session.fsync(output)
                }
            }
            val intent = Intent(context, UpdateInstallReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
    }

    private fun dexMetadata(apk: File): ByteArray? {
        if (Build.VERSION.SDK_INT !in 28..30) return null
        val profile = ZipFile(apk).use { zip ->
            val entry = zip.getEntry("assets/dexopt/baseline.prof") ?: return null
            zip.getInputStream(entry).use { it.readBytes() }
        }
        val header = "pro\u0000010\u0000".toByteArray(Charsets.ISO_8859_1)
        if (profile.size <= header.size || !profile.copyOfRange(0, header.size).contentEquals(header)) return null
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            val entry = ZipEntry("primary.prof").apply {
                method = ZipEntry.STORED
                size = profile.size.toLong()
                compressedSize = profile.size.toLong()
                crc = CRC32().apply { update(profile) }.value
            }
            zip.putNextEntry(entry)
            zip.write(profile)
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

    companion object {
        private const val TAG = "Update"
        private const val KEY_LAST_CHECK = "last_check"
        private const val AUTO_CHECK_INTERVAL_MS = 6 * 3_600_000L
        const val APK_NAME = "Franklix.apk"

        /** "1.0.10" > "1.0.9"; ignora sufijos ("-pruebas"). */
        fun isNewer(candidate: String, current: String): Boolean {
            fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val a = parts(candidate)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
