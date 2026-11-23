package com.shieldstream.tv.desktop

import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.ExternalSubtitle
import com.shieldstream.tv.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/**
 * Subtítulos externos, como en la tele: se buscan en OpenSubtitles (el mismo servicio que usa Stremio, sin cuenta) y
 * en Podnapisi, se ordenan por parecido con el nombre del vídeo y el elegido se descarga (descomprimido y en UTF-8)
 * para dárselo a mpv. No hace falta ninguna clave.
 */
object ExternalSubs {
    private const val USER_AGENT = "Bambi/1.0 (Windows)"
    private const val OPENSUBTITLES = "https://opensubtitles-v3.strem.io"
    private const val PODNAPISI = "https://www.podnapisi.net"
    private const val MAX_DOWNLOAD = 16 * 1024 * 1024
    private const val MAX_SUBTITLE = 8 * 1024 * 1024
    private val SUPPORTED = setOf("srt", "ass", "ssa", "vtt")
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    /** Qué se está viendo: para buscar el subtítulo de esa película o de ese episodio. */
    data class Target(val item: CatalogItem, val episode: Episode?, val fileName: String?)

    /** Busca en todos los proveedores (cada uno, como mucho 10 s) y ordena: idioma pedido, parecido, sin SDH. */
    suspend fun search(target: Target, language: String, client: OkHttpClient = DesktopHttp.client): List<ExternalSubtitle> = coroutineScope {
        listOf<suspend () -> List<ExternalSubtitle>>(
            { openSubtitles(target, language, client) },
            { podnapisi(target, language, client) },
        ).map { provider ->
            async { withTimeoutOrNull(10_000) { runCatching { provider() }.getOrDefault(emptyList()) }.orEmpty() }
        }.awaitAll().flatten()
            .filterNot { language == "es" && it.release.contains("portugu", ignoreCase = true) }
            .map { s -> if (s.release.isNotBlank() && target.fileName != null) s.copy(matchScore = similarity(s.release, target.fileName)) else s }
            .distinctBy { it.provider to it.id }
            .sortedWith(compareByDescending<ExternalSubtitle> { it.matchScore }.thenBy { it.hearingImpaired })
    }

    /** Descarga, descomprime y guarda en UTF-8 en la carpeta temporal. */
    suspend fun download(sub: ExternalSubtitle, target: Target, client: OkHttpClient = DesktopHttp.client): File = withContext(Dispatchers.IO) {
        val raw = client.newCall(Request.Builder().url(sub.downloadUrl).header("User-Agent", USER_AGENT).build()).execute().use { r ->
            check(r.isSuccessful) { "Descarga fallida (HTTP ${r.code})" }
            r.body!!.byteStream().use { it.readBounded(MAX_DOWNLOAD) }
        }
        val (bytes, ext) = unpack(raw, target.episode)
        val dir = File(System.getProperty("java.io.tmpdir"), "bambi-subs").apply { mkdirs() }
        File(dir, "${sub.provider}_${sub.id.hashCode().toUInt()}.$ext").apply { writeText(decode(bytes, sub.language), Charsets.UTF_8) }
    }

    /** Lo que se enseña en el reproductor: «Español · OpenSubtitles · nombre de la release». */
    fun label(sub: ExternalSubtitle): String {
        val lang = sub.language.uppercase() + if (sub.hearingImpaired) " SDH" else ""
        val release = sub.release.removeSuffix(".srt").removeSuffix(".ass").removeSuffix(".vtt").take(60)
        return "$lang · " + release.ifBlank { sub.provider }
    }

    // ---------------------------------------------------------------- proveedores

    @Serializable
    private data class StremioResponse(val subtitles: List<StremioSub> = emptyList())

    @Serializable
    private data class StremioSub(val id: String = "", val url: String = "", val lang: String = "", val subtitleFileName: String? = null, val movieReleaseName: String? = null)

    private suspend fun openSubtitles(target: Target, language: String, client: OkHttpClient): List<ExternalSubtitle> {
        val imdb = target.item.imdbId ?: return emptyList()
        val ep = target.episode
        val (type, id) = if (target.item.type == MediaType.TV && ep != null) "series" to "$imdb:${ep.season}:${ep.number}" else "movie" to imdb
        val extra = target.fileName?.let { "/filename=" + URLEncoder.encode(it, "UTF-8") }.orEmpty()
        val body = get(client, Request.Builder().url("$OPENSUBTITLES/subtitles/$type/$id$extra.json").header("User-Agent", USER_AGENT).build())
        return json.decodeFromString(StremioResponse.serializer(), body).subtitles
            .filter { it.url.isNotBlank() && iso1(it.lang) == language }
            .mapIndexed { index, s ->
                val latino = s.lang.lowercase() in setOf("spl", "spn", "ea")
                val release = (s.subtitleFileName ?: s.movieReleaseName).orEmpty()
                ExternalSubtitle(
                    provider = "OpenSubtitles",
                    id = s.id.ifBlank { s.url },
                    release = if (latino) "(Latino) $release".trim() else release,
                    language = language,
                    hearingImpaired = false,
                    downloadUrl = s.url,
                    matchScore = (0.5f - index * 0.03f).coerceAtLeast(0f),
                )
            }
    }

    @Serializable
    private data class PodnapisiResponse(val data: List<PodnapisiItem> = emptyList())

    @Serializable
    private data class PodnapisiItem(val publish_id: String? = null, val language: String = "", val custom_releases: List<String> = emptyList(), val flags: List<String> = emptyList())

    private suspend fun podnapisi(target: Target, language: String, client: OkHttpClient): List<ExternalSubtitle> {
        val item = target.item
        val url = "$PODNAPISI/subtitles/search/advanced".toHttpUrl().newBuilder()
            .addQueryParameter("keywords", item.title)
            .addQueryParameter("language", language)
            .apply {
                val ep = target.episode
                if (item.type == MediaType.TV) {
                    addQueryParameter("movie_type", "tv-series")
                    ep?.let { addQueryParameter("seasons", it.season.toString()); addQueryParameter("episodes", it.number.toString()) }
                } else {
                    addQueryParameter("movie_type", "movie")
                    item.year?.let { addQueryParameter("year", it.toString()) }
                }
            }.build()
        val body = get(client, Request.Builder().url(url).header("Accept", "application/json").header("User-Agent", USER_AGENT).build())
        return json.decodeFromString(PodnapisiResponse.serializer(), body).data
            .filter { it.publish_id != null && it.language.equals(language, true) }
            .map { p ->
                ExternalSubtitle(
                    provider = "Podnapisi",
                    id = p.publish_id!!,
                    release = p.custom_releases.maxByOrNull { r -> target.fileName?.let { similarity(r, it) } ?: 0f }.orEmpty(),
                    language = language,
                    hearingImpaired = "hearing_impaired" in p.flags,
                    downloadUrl = "$PODNAPISI/subtitles/${p.publish_id}/download",
                )
            }
    }

    private suspend fun get(client: OkHttpClient, request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { r ->
            check(r.isSuccessful) { "HTTP ${r.code}" }
            r.body?.string().orEmpty()
        }
    }

    // ---------------------------------------------------------------- utilidades (como en la tele)

    internal fun iso1(code: String): String? = when (code.lowercase()) {
        "spa", "spl", "spn", "es", "ea" -> "es"
        "eng", "en" -> "en"
        "cat", "ca" -> "ca"
        "fre", "fra", "fr" -> "fr"
        "ita", "it" -> "it"
        "por", "pob", "pt", "pb" -> "pt"
        "ger", "deu", "de" -> "de"
        else -> null
    }

    internal fun similarity(a: String, b: String): Float {
        fun tokens(s: String) = s.lowercase().substringBeforeLast('.').split(Regex("[^a-z0-9]+")).filter { it.length > 1 }.toSet()
        val ta = tokens(a)
        val tb = tokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0f
        return (ta intersect tb).size.toFloat() / (ta union tb).size
    }

    internal fun unpack(raw: ByteArray, episode: Episode?): Pair<ByteArray, String> {
        if (raw.size > 4 && raw[0] == 'P'.code.toByte() && raw[1] == 'K'.code.toByte()) {
            val candidates = mutableListOf<Pair<String, ByteArray>>()
            ZipInputStream(ByteArrayInputStream(raw)).use { zip ->
                var entries = 0
                generateSequence { zip.nextEntry }.forEach { entry ->
                    check(++entries <= 64) { "El zip tiene demasiados ficheros" }
                    val ext = entry.name.substringAfterLast('.', "").lowercase()
                    if (!entry.isDirectory && ext in SUPPORTED) candidates += entry.name to zip.readBounded(MAX_SUBTITLE)
                }
            }
            check(candidates.isNotEmpty()) { "El zip no trae subtítulos" }
            val chosen = episode?.let { e ->
                val patterns = listOf(
                    Regex("s0*${e.season}e0*${e.number}(?!\\d)", RegexOption.IGNORE_CASE),
                    Regex("(?<!\\d)0*${e.season}x0*${e.number}(?!\\d)", RegexOption.IGNORE_CASE),
                )
                candidates.firstOrNull { (name, _) -> patterns.any { it.containsMatchIn(name) } }
            } ?: candidates.first()
            return chosen.second to chosen.first.substringAfterLast('.').lowercase()
        }
        if (raw.size > 2 && raw[0] == 0x1f.toByte() && raw[1] == 0x8b.toByte()) {
            return GZIPInputStream(ByteArrayInputStream(raw)).use { it.readBounded(MAX_SUBTITLE) } to "srt"
        }
        val head = String(raw, 0, minOf(raw.size, 400), Charsets.ISO_8859_1)
        val ext = when {
            head.contains("[Script Info]") -> "ass"
            head.trimStart('﻿', ' ', '\n', '\r').startsWith("WEBVTT") -> "vtt"
            else -> "srt"
        }
        return raw to ext
    }

    internal fun decode(bytes: ByteArray, language: String): String {
        val utf8 = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try {
            utf8.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charset.forName(if (language in setOf("ru", "uk", "bg")) "windows-1251" else "windows-1252"))
        }
        return text.removePrefix("﻿")
    }

    private fun InputStream.readBounded(max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            total += n
            check(total <= max) { "Subtítulo demasiado grande" }
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
