package com.shieldstream.tv.data.repository

import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.domain.DebridService
import com.shieldstream.tv.domain.SourceProvider
import com.shieldstream.tv.domain.StremioAddons
import com.shieldstream.tv.network.dto.TorrentioStreamDto

/*
 * Lo que la búsqueda de fuentes necesita de cada plataforma (Android o escritorio), en pequeño.
 */

/** Textos que ve el usuario (en Android salen de R.string; en escritorio, en castellano). */
interface StreamTexts {
    fun providerTitle(provider: SourceProvider): String
    fun providerSection(provider: SourceProvider): String
    fun imdbMissing(title: String): String
    fun pickEpisode(): String
    fun notResponding(): String
    fun noSources(): String
    fun providerUnavailable(names: String): String
    fun debridKeyMissing(service: String): String
    fun stremioAddon(): String
    fun subscriptionExpired(service: String): String
    fun invalidServiceKey(service: String): String
    fun serviceBlocked(service: String): String
    fun serviceUnavailableTorrentio(service: String): String
}

/** Claves guardadas por servicio (en Android, TokenStore cifrado). */
fun interface StreamSecrets {
    fun secret(service: ApiService): String?
}

/** Ajustes que afectan a la búsqueda. */
interface StreamSettings {
    suspend fun sourceProviders(): Set<SourceProvider>
    suspend fun debridService(): DebridService
    suspend fun spanishFirst(): Boolean

    /**
     * Con resolución propia la app elige el capítulo dentro de un pack de temporada; a través de Torrentio, sin saber
     * el número de archivo, saldría el vídeo más grande del pack (otro capítulo): ahí solo valen los sueltos.
     */
    suspend fun canPickFile(): Boolean = debridService().supportsDirectMagnetResolution
}

/** Registro: en Android va a logcat; por defecto, a la consola. */
object StreamLog {
    @Volatile
    var sink: (warning: Boolean, tag: String, message: String) -> Unit = { _, tag, message -> println("$tag: $message") }

    fun w(tag: String, message: String) = sink(true, tag, message)
    fun i(tag: String, message: String) = sink(false, tag, message)
}

/** Igual que android.net.Uri.encode: deja letras, números y _-!.~'()* y codifica el resto en UTF-8. */
fun uriEncode(text: String): String {
    val out = StringBuilder()
    for (byte in text.toByteArray(Charsets.UTF_8)) {
        val c = byte.toInt() and 0xFF
        val ch = c.toChar()
        if (c < 0x80 && (ch.isLetterOrDigit() || ch in "_-!.~'()*")) out.append(ch)
        else out.append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 0xF])
    }
    return out.toString()
}

fun torrentioConfig(debrid: DebridService, key: String): String = "${debrid.torrentioKey}=${uriEncode(key)}"

fun TorrentioStreamDto.isDebridError(): Boolean =
    url?.contains("/videos/") == true && name.contains("error", ignoreCase = true)

fun debridErrorMessage(title: String, service: String, texts: StreamTexts): String = when {
    title.contains("expired", ignoreCase = true) -> texts.subscriptionExpired(service)
    title.contains("invalid", ignoreCase = true) -> texts.invalidServiceKey(service)
    title.contains("blocked", ignoreCase = true) -> texts.serviceBlocked(service)
    else -> texts.serviceUnavailableTorrentio(service)
}

private val HASH = Regex("^[0-9a-fA-F]{40}$")

/** Hash y número de archivo de un enlace /resolve/ de Torrentio. */
fun parseResolveUrl(url: String): Pair<String, Int?>? {
    val path = runCatching { java.net.URI(url).path }.getOrNull()
        ?: url.substringAfter("://", "").substringAfter('/', "").substringBefore('?').let { "/$it" }
    val segments = path.split('/').filter { it.isNotEmpty() }
    val start = segments.indexOf("resolve").takeIf { it >= 0 } ?: return null
    val hash = segments.getOrNull(start + 3)?.takeIf { HASH.matches(it) }
        ?: segments.drop(start + 3).firstOrNull { HASH.matches(it) }
        ?: return null
    val fileIdx = segments.getOrNull(start + 5)?.toIntOrNull()
    return hash.lowercase() to fileIdx
}

fun aioStreamBases(raw: String): List<String> =
    StremioAddons.parse(raw).mapNotNull { it.baseUrl }

fun aioStreamBase(manifestUrl: String): String? = StremioAddons.baseUrl(manifestUrl)
