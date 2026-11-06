package com.shieldstream.tv.data.repository

import com.shieldstream.tv.domain.ITHIL_ALLDEBRID
import com.shieldstream.tv.domain.IthilLibraryRepository
import com.shieldstream.tv.domain.IthilSource
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.HdrType
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.Quality
import com.shieldstream.tv.model.StreamSource
import java.security.MessageDigest

internal fun ithilSources(
    library: IthilLibraryRepository,
    item: CatalogItem,
    episode: Episode?,
): List<StreamSource> {
    if (!library.isReady()) return emptyList()
    val tmdb = item.id.toIntOrNull() ?: return emptyList()
    val rows = when (item.type) {
        MediaType.MOVIE -> library.movieSources(tmdb)
        MediaType.TV -> {
            requireNotNull(episode) { "An episode is required for Ithil series" }
            library.episodeSources(tmdb, episode.season, episode.number)
        }
    }
    return rows.map { it.toStreamSource(item, episode) }
}

private fun IthilSource.toStreamSource(item: CatalogItem, episode: Episode?): StreamSource {
    val title = listOf(item.title, quality, audio, info).filter(String::isNotBlank).joinToString(" · ")
    return StreamSource(
        id = stableId(url),
        title = title,
        infoHash = stableId(url),
        fileIdx = null,
        magnet = "",
        quality = Quality.fromName(quality),
        hdrType = HdrType.fromName(quality),
        sizeBytes = 0L,
        seeders = null,
        isCached = false,
        resolveUrl = url,
        debrid = ITHIL_ALLDEBRID,
        provider = "Ithil",
        season = episode?.season,
        episode = episode?.number,
        languages = audio.toLanguages(),
    )
}

private fun stableId(url: String): String =
    MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).take(20)
        .joinToString("") { "%02x".format(it) }

private fun String.toLanguages(): List<String> = split(',').mapNotNull { tag ->
    when (tag.trim().lowercase()) {
        "esp" -> "ES"
        "lat" -> "LAT"
        "eng" -> "EN"
        "jpn" -> "JA"
        "fra" -> "FR"
        "deu" -> "DE"
        "ita" -> "IT"
        "cat" -> "CA"
        "por" -> "PT"
        "zho" -> "ZH"
        "tur" -> "TR"
        "kor" -> "KO"
        "rus" -> "RU"
        else -> null
    }
}.distinct()
