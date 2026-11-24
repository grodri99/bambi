package com.shieldstream.tv.desktop

import com.sun.jna.platform.win32.Crypt32Util
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Secretos de Bambi (claves de Ajustes y sesiones de Trakt) cifrados con DPAPI de Windows: solo tu usuario de Windows en
 * este PC puede descifrarlos, como el Keystore de Android en la tele. Fuera de Windows (pruebas y capturas) se guardan
 * sin cifrar.
 *
 * Varias partes de la app abren el mismo secrets.dat (Ajustes y Trakt). Antes cada una guardaba su copia en memoria y,
 * al escribir, pisaba el archivo con la suya: al crear un perfil, Trakt guardaba su copia (sin las claves de Ajustes) y
 * al volver a abrir Bambi habían desaparecido TMDB, el debrid y los addons. Ahora cada escritura vuelve a leer el
 * archivo y solo cambia su clave, con un cerrojo común por archivo, y se escribe entero de golpe (archivo temporal y
 * renombrar) para que un cierre a medias no lo deje vacío.
 */
class SecretStore(private val file: File) {
    private val json = Json { prettyPrint = true }
    private val lock: Any = locks.getOrPut(runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)) { Any() }
    private var values: Map<String, String> = emptyMap()
    private var loadedStamp = -1L

    fun get(key: String): String? = synchronized(lock) {
        refresh()
        values[key]?.let { runCatching { String(unprotect(Base64.getDecoder().decode(it))) }.getOrNull() }
    }

    fun set(key: String, value: String?) = synchronized(lock) {
        // Siempre sobre lo que hay en disco ahora mismo, no sobre una copia vieja.
        val next = read().toMutableMap()
        if (value == null) next.remove(key)
        else next[key] = Base64.getEncoder().encodeToString(protect(value.toByteArray()))
        write(next)
        values = next
        loadedStamp = stamp()
    }

    private fun refresh() {
        val now = stamp()
        if (now != loadedStamp) {
            values = read()
            loadedStamp = now
        }
    }

    private fun stamp(): Long = if (file.exists()) file.lastModified() * 31 + file.length() else 0L

    private fun read(): Map<String, String> = runCatching {
        json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), file.readText())
    }.getOrDefault(emptyMap())

    private fun write(map: Map<String, String>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(MapSerializer(String.serializer(), String.serializer()), map))
        runCatching {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.recoverCatching {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }.getOrThrow()
    }

    private fun protect(bytes: ByteArray): ByteArray = if (WINDOWS) Crypt32Util.cryptProtectData(bytes) else bytes
    private fun unprotect(bytes: ByteArray): ByteArray = if (WINDOWS) Crypt32Util.cryptUnprotectData(bytes) else bytes

    companion object {
        val WINDOWS = System.getProperty("os.name").orEmpty().startsWith("Windows")
        private val locks = ConcurrentHashMap<String, Any>()

        fun default() = SecretStore(File(ConfigStore.defaultFile().parentFile, "secrets.dat"))
    }
}
