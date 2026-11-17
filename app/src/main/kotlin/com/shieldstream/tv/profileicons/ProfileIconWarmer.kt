package com.shieldstream.tv.profileicons

import android.content.Context
import android.util.Log
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.settings.profiles.ProfileIcons
import com.shieldstream.tv.ui.component.ProfileIconFiles
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deja los iconos de perfil en la memoria interna para que salgan al instante:
 * - [warmProfiles]: los de los perfiles que hay (pocos), durante la intro, para que "¿Quién está viendo?" ya los tenga.
 * - [warmAll]: todos los del selector, sin prisa y con pocas descargas a la vez, cuando ya ha terminado la intro.
 *   Solo baja los que falten, así que después de la primera vez no hace nada.
 * Solo descarga y guarda (no decodifica), así que apenas usa CPU.
 */
@Singleton
class ProfileIconWarmer @Inject constructor(
    @ApplicationContext private val context: Context,
    base: OkHttpClient,
    private val profiles: ProfileRepository,
) {
    private val client = base.newBuilder().build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var all: Job? = null

    fun warmProfiles() {
        scope.launch {
            profiles.profiles.value.mapNotNull { it.avatarImage }.filter { it.startsWith("http") }.distinct().forEach { ensure(it) }
        }
    }

    fun warmAll() {
        if (all?.isActive == true) return
        all = scope.launch {
            val urls = ProfileIcons.load(context).flatMap { group -> group.icons.map { it.url } }.distinct()
            val missing = urls.filter { !ProfileIconFiles.file(context, it).isFile }
            if (missing.isEmpty()) return@launch
            val started = System.currentTimeMillis()
            val gate = Semaphore(PARALLEL)
            val saved = coroutineScope {
                missing.map { url -> async { gate.withPermit { ensure(url) } } }.awaitAll().count { it }
            }
            Log.i(TAG, "Iconos de perfil: $saved de ${missing.size} guardados en ${System.currentTimeMillis() - started} ms")
        }
    }

    /** Descarga el icono si no está; true si queda guardado. */
    private fun ensure(url: String): Boolean {
        val target = ProfileIconFiles.file(context, url)
        if (target.isFile && target.length() > 0) return true
        return runCatching {
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, target.name + ".part")
            client.newCall(Request.Builder().url(url).header("User-Agent", "Bambi").build()).execute().use { response ->
                if (!response.isSuccessful) return@runCatching false
                val body = response.body ?: return@runCatching false
                tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            tmp.length() > 0 && tmp.renameTo(target)
        }.getOrDefault(false)
    }

    private companion object {
        const val TAG = "ProfileIcons"
        const val PARALLEL = 4
    }
}
