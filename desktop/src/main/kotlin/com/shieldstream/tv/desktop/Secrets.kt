package com.shieldstream.tv.desktop

import com.sun.jna.platform.win32.Crypt32Util
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Base64

/**
 * Secretos de Bambi (sesiones de Trakt y, más adelante, las claves de Ajustes) cifrados con DPAPI de Windows: solo tu
 * usuario de Windows en este PC puede descifrarlos, como el Keystore de Android en la tele. Fuera de Windows (pruebas y
 * capturas) se guardan sin cifrar.
 */
class SecretStore(private val file: File) {
    private val json = Json { prettyPrint = true }
    private val values: MutableMap<String, String> = load()

    @Synchronized
    fun get(key: String): String? = values[key]?.let { runCatching { String(unprotect(Base64.getDecoder().decode(it))) }.getOrNull() }

    @Synchronized
    fun set(key: String, value: String?) {
        if (value == null) values.remove(key)
        else values[key] = Base64.getEncoder().encodeToString(protect(value.toByteArray()))
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(MapSerializer(String.serializer(), String.serializer()), values))
    }

    private fun load(): MutableMap<String, String> = runCatching {
        json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), file.readText()).toMutableMap()
    }.getOrDefault(mutableMapOf())

    private fun protect(bytes: ByteArray): ByteArray = if (WINDOWS) Crypt32Util.cryptProtectData(bytes) else bytes
    private fun unprotect(bytes: ByteArray): ByteArray = if (WINDOWS) Crypt32Util.cryptUnprotectData(bytes) else bytes

    companion object {
        val WINDOWS = System.getProperty("os.name").orEmpty().startsWith("Windows")

        fun default() = SecretStore(File(ConfigStore.defaultFile().parentFile, "secrets.dat"))
    }
}
