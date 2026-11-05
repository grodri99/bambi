package com.shieldstream.tv.data.subtitles

import com.shieldstream.tv.model.tr
import android.content.Context
import android.util.Log
import com.shieldstream.tv.domain.SubtitleRepository
import com.shieldstream.tv.model.ExternalSubtitle
import com.shieldstream.tv.model.SubtitleFile
import com.shieldstream.tv.model.SubtitleLanguages
import com.shieldstream.tv.model.SubtitleQuery
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubtitleRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: SubtitleHttp,
    openSubtitles: OpenSubtitlesProvider,
    podnapisi: PodnapisiProvider,
    subDl: SubDlProvider,
) : SubtitleRepository {

    private val providers: List<SubtitleProvider> = listOf(openSubtitles, podnapisi, subDl)

    override suspend fun search(query: SubtitleQuery): List<ExternalSubtitle> = coroutineScope {
        providers
            .filter { it.enabled }
            .map { provider ->
                async {
                    withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                        runCatching { provider.search(query) }
                            .onFailure { Log.w(TAG, "${provider.name}: ${it.message}") }
                            .getOrDefault(emptyList())
                    }.orEmpty()
                }
            }
            .awaitAll()
            .flatten()
            .map { sub ->
                val score = if (sub.release.isNotBlank() && query.fileName != null) {
                    similarity(sub.release, query.fileName!!)
                } else {
                    sub.matchScore
                }
                sub.copy(matchScore = score)
            }
            .distinctBy { it.provider to it.id }
            .sortedWith(compareByDescending<ExternalSubtitle> { it.matchScore }.thenBy { it.hearingImpaired })
    }

    override suspend fun download(subtitle: ExternalSubtitle, query: SubtitleQuery): SubtitleFile =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(subtitle.downloadUrl)
                .header("User-Agent", USER_AGENT)
                .build()
            val raw = http.client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Descarga fallida (HTTP ${response.code})" }
                val body = response.body ?: error(tr("Respuesta vacía", "Empty response"))
                check(body.contentLength() <= SubtitleLimits.DOWNLOAD_BYTES) {
                    tr("El subtítulo descargado es demasiado grande", "The subtitle download is too large")
                }
                body.byteStream().use { it.readBounded(SubtitleLimits.DOWNLOAD_BYTES) }
            }

            val (bytes, ext) = unpack(raw, query)
            val text = decode(bytes, subtitle.language)
            val dir = File(context.cacheDir, "subs").apply { mkdirs() }
            val file = File(dir, "${subtitle.provider}_${subtitle.id.hashCode().toUInt()}.$ext")
            file.writeText(text, Charsets.UTF_8)

            SubtitleFile(
                path = file.absolutePath,
                mimeType = mimeFor(ext),
                language = subtitle.language,
                label = "${SubtitleLanguages.label(subtitle.language)} · ${subtitle.provider}" +
                    if (subtitle.hearingImpaired) " (SDH)" else "",
            )
        }

    private fun unpack(raw: ByteArray, query: SubtitleQuery): Pair<ByteArray, String> {
        if (raw.size > 4 && raw[0] == 'P'.code.toByte() && raw[1] == 'K'.code.toByte()) {
            val candidates = mutableListOf<Pair<String, ByteArray>>()
            var entries = 0
            var totalBytes = 0
            ZipInputStream(ByteArrayInputStream(raw)).use { zip ->
                generateSequence { zip.nextEntry }.forEach { entry ->
                    entries++
                    check(entries <= SubtitleLimits.ARCHIVE_ENTRIES) {
                        tr("El zip contiene demasiados archivos", "The zip contains too many files")
                    }
                    val ext = entry.name.substringAfterLast('.', "").lowercase()
                    if (!entry.isDirectory && ext in SUPPORTED_EXTENSIONS) {
                        val bytes = zip.readBounded(SubtitleLimits.SUBTITLE_BYTES)
                        totalBytes += bytes.size
                        check(totalBytes <= SubtitleLimits.ARCHIVE_BYTES) {
                            tr("Los subtítulos descomprimidos son demasiado grandes", "The decompressed subtitles are too large")
                        }
                        candidates += entry.name to bytes
                    }
                }
            }
            check(candidates.isNotEmpty()) { tr("El zip no contiene subtítulos compatibles (srt/ass/vtt)", "The zip has no compatible subtitles (srt/ass/vtt)") }
            val episode = episodeMatch(candidates, query)
            check(query.media.season == null || candidates.size == 1 || episode != null) {
                tr("El zip no identifica el episodio ${query.media.season}x${query.media.episode}", "The zip doesn't identify episode ${query.media.season}x${query.media.episode}")
            }
            val chosen = episode ?: candidates.first()
            return chosen.second to chosen.first.substringAfterLast('.').lowercase()
        }
        if (raw.size > 2 && raw[0] == 0x1f.toByte() && raw[1] == 0x8b.toByte()) {
            return GZIPInputStream(ByteArrayInputStream(raw)).use {
                it.readBounded(SubtitleLimits.SUBTITLE_BYTES)
            } to "srt"
        }
        check(raw.size <= SubtitleLimits.SUBTITLE_BYTES) {
            tr("El subtítulo es demasiado grande", "The subtitle is too large")
        }
        return raw to guessExtension(raw)
    }

    private fun episodeMatch(candidates: List<Pair<String, ByteArray>>, query: SubtitleQuery): Pair<String, ByteArray>? {
        val s = query.media.season ?: return null
        val e = query.media.episode ?: return null
        val patterns = listOf(
            Regex("s0*${s}e0*${e}(?!\\d)", RegexOption.IGNORE_CASE),
            Regex("(?<!\\d)0*${s}x0*${e}(?!\\d)", RegexOption.IGNORE_CASE),
        )
        return candidates.firstOrNull { (name, _) -> patterns.any { it.containsMatchIn(name) } }
    }

    private fun guessExtension(bytes: ByteArray): String {
        val head = String(bytes, 0, minOf(bytes.size, 400), Charsets.ISO_8859_1)
        return when {
            head.contains("[Script Info]") -> "ass"
            head.trimStart('﻿', ' ', '\n', '\r').startsWith("WEBVTT") -> "vtt"
            else -> "srt"
        }
    }

    private fun decode(bytes: ByteArray, language: String): String {
        val utf8 = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try {
            utf8.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charset.forName(LEGACY_CHARSETS[language] ?: "windows-1252"))
        }
        return text.removePrefix("﻿")
    }

    private fun mimeFor(ext: String) = when (ext) {
        "ass", "ssa" -> "text/x-ssa"
        "vtt" -> "text/vtt"
        else -> "application/x-subrip"
    }

    private companion object {
        const val TAG = "Subtitles"
        const val PROVIDER_TIMEOUT_MS = 10_000L
        val SUPPORTED_EXTENSIONS = setOf("srt", "ass", "ssa", "vtt")
        val LEGACY_CHARSETS = mapOf(
            "ru" to "windows-1251", "uk" to "windows-1251", "bg" to "windows-1251",
            "pl" to "windows-1250", "cs" to "windows-1250", "hu" to "windows-1250", "ro" to "windows-1250",
            "el" to "windows-1253", "tr" to "windows-1254", "he" to "windows-1255", "ar" to "windows-1256",
        )
    }
}
