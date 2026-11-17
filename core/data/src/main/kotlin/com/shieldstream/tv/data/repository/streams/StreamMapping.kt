package com.shieldstream.tv.data.repository.streams

import android.net.Uri
import com.shieldstream.tv.data.repository.parseResolveUrl
import com.shieldstream.tv.domain.DebridService
import com.shieldstream.tv.model.AudioCodec
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.HdrType
import com.shieldstream.tv.model.Quality
import com.shieldstream.tv.model.StreamSource
import com.shieldstream.tv.model.VideoCodec
import com.shieldstream.tv.network.dto.TorrentioStreamDto

/*
 * De un resultado de Torrentio o de un addon de Stremio ([TorrentioStreamDto]) a una fuente de la app
 * ([StreamSource]): calidad, HDR, tamaño, seeds, idiomas (por las banderas) y códecs, sacados del texto.
 */

internal fun TorrentioStreamDto.toStreamSource(item: CatalogItem): StreamSource? {
    val hash = infoHash ?: return null
    val text = title.ifBlank { description.orEmpty() }
    if (UNSUPPORTED_CODEC.containsMatchIn("$name $text")) return null
    val isAv1 = AV1_CODEC.containsMatchIn("$name $text")
    val fileName = behaviorHints?.filename
    val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: item.title
    // Addons con formato propio (AIOStreams): la primera línea puede ser solo "🎞 HEVC"; vale más el archivo.
    val releaseName = if (fileName != null && firstLine.count { it.isLetterOrDigit() } < 10) fileName else firstLine
    val magnet = "magnet:?xt=urn:btih:${hash.lowercase()}&dn=${Uri.encode(fileName ?: releaseName)}"
    return StreamSource(
        id = "$hash:${fileIdx ?: 0}",
        title = releaseName,
        infoHash = hash,
        fileIdx = fileIdx,
        magnet = magnet,
        quality = Quality.fromName("$name $text"),
        hdrType = HdrType.fromName("$name $text"),
        sizeBytes = parseSize(text),
        seeders = availability ?: parseSeeders(text),
        isCached = false,
        fileName = fileName,
        languages = parseLanguages("$name\n$text"),
        videoCodec = if (isAv1) VideoCodec.AV1 else null,
        audioCodecs = AudioCodec.parse("$name $text ${fileName.orEmpty()}"),
        softwareDecode = isAv1,
    )
}

/** Resultado de Torrentio con otro debrid: el enlace /resolve/ lleva el hash y el archivo. */
internal fun TorrentioStreamDto.toDebridSource(item: CatalogItem, debrid: DebridService): StreamSource? {
    val link = url?.takeIf { it.contains("/resolve/") } ?: return null
    val (hash, idx) = parseResolveUrl(link) ?: return null
    return copy(infoHash = hash, fileIdx = fileIdx ?: idx).toStreamSource(item)?.copy(
        resolveUrl = link,
        debrid = debrid.title,
        isCached = name.startsWith("[${debrid.shortName}+]"),
    )
}

private fun parseLanguages(text: String): List<String> {
    val codePoints = ArrayList<Int>()
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        codePoints.add(cp)
        i += Character.charCount(cp)
    }
    val result = mutableListOf<String>()
    var j = 0
    while (j < codePoints.size - 1) {
        val a = codePoints[j]
        val b = codePoints[j + 1]
        if (a in 0x1F1E6..0x1F1FF && b in 0x1F1E6..0x1F1FF) {
            val country = "${'A' + (a - 0x1F1E6)}${'A' + (b - 0x1F1E6)}"
            result += FLAG_TO_LANGUAGE[country] ?: country
            j += 2
        } else {
            j++
        }
    }
    if (result.isEmpty() && text.contains("multi", ignoreCase = true)) result += "MULTI"
    return result.distinct()
}

private fun parseSize(text: String): Long {
    val match = SIZE_REGEX.find(text) ?: return 0L
    val value = match.groupValues[1].toDoubleOrNull() ?: return 0L
    val unit = match.groupValues[2].lowercase()
    val multiplier = when {
        unit.contains("g") -> 1024L * 1024L * 1024L
        unit.contains("m") -> 1024L * 1024L
        else -> 1024L
    }
    return (value * multiplier).toLong()
}

private fun parseSeeders(text: String): Int? =
    SEEDERS_REGEX.find(text)?.groupValues?.get(1)?.toIntOrNull()

private val SIZE_REGEX = Regex("([\\d.]+)\\s*(GB|GiB|MB|MiB)", RegexOption.IGNORE_CASE)
private val SEEDERS_REGEX = Regex("👤\\s*(\\d+)")
private val UNSUPPORTED_CODEC = Regex("(?<![a-z0-9])(x266|vvc|h\\.?266)(?![a-z0-9])", RegexOption.IGNORE_CASE)
private val AV1_CODEC = Regex("(?<![a-z0-9])av1(?![a-z0-9])", RegexOption.IGNORE_CASE)
private val FLAG_TO_LANGUAGE = mapOf(
    "ES" to "ES", "MX" to "LAT", "AR" to "LAT", "GB" to "EN", "US" to "EN",
    "IT" to "IT", "FR" to "FR", "DE" to "DE", "PT" to "PT", "BR" to "PT-BR",
    "RU" to "RU", "UA" to "UK", "JP" to "JA", "KR" to "KO", "CN" to "ZH",
    "IN" to "HI", "NL" to "NL", "PL" to "PL",
)
