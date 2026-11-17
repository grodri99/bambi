package com.shieldstream.tv.data.repository.streams

import android.util.Log
import com.shieldstream.tv.data.repository.ShowNameFilter
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.network.api.TmdbApi
import com.shieldstream.tv.network.dto.TmdbTranslationsDto
import com.shieldstream.tv.network.dto.TorrentioStreamDto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/** Título en inglés de TMDB (el que suelen llevar los torrents), si lo hay. */
internal fun englishTitle(t: TmdbTranslationsDto?): String? =
    t?.translations?.firstOrNull { it.iso_639_1 == "en" }?.data
        ?.let { it.title ?: it.name }
        ?.takeIf { it.isNotBlank() }

/**
 * Cómo se llama un título en los torrents: el nombre en inglés para buscar, todos sus nombres (español, inglés,
 * original) para filtrar lo que es de otra serie, y para el anime la numeración absoluta de los capítulos. Todo sale
 * de TMDB una vez por título y se guarda en memoria.
 */
internal class ReleaseTitles(private val tmdb: TmdbApi, private val key: () -> String) {
    private val lock = Mutex()
    private val releaseTitles = ConcurrentHashMap<String, String>()
    private val showTitles = ConcurrentHashMap<String, List<String>>()
    private val animeSeasons = ConcurrentHashMap<String, Map<Int, Int>>()
    private val seasonStarts = ConcurrentHashMap<String, Int>()

    /** Nombre para buscar en los buscadores de torrents (el inglés de TMDB o el original; si falla, el de la app). */
    suspend fun releaseTitle(item: CatalogItem): String = lock.withLock {
        releaseTitles[item.key]?.let { return@withLock it }
        val title = runCatching {
            val id = item.id.toInt()
            if (item.type == MediaType.TV) {
                tmdb.tvDetails(id, key()).also { d ->
                    showTitles[item.key] = buildList {
                        add(item.title); add(d.name); d.original_name?.let(::add)
                        d.translations?.translations.orEmpty()
                            .filter { it.iso_639_1 == "es" || it.iso_639_1 == "en" }
                            .forEach { t -> t.data?.name?.let(::add) }
                    }.filter { it.isNotBlank() }.distinct()
                    if (d.original_language == "ja" && d.genres.any { it.id == 16 }) {
                        animeSeasons[item.key] = d.seasons.filter { it.season_number > 0 }.associate { it.season_number to it.episode_count }
                    }
                }.let { englishTitle(it.translations) ?: it.original_name }
            } else {
                tmdb.movieDetails(id, key()).also { d ->
                    showTitles[item.key] = buildList {
                        add(item.title); add(d.title); d.original_title?.let(::add)
                        d.translations?.translations.orEmpty()
                            .filter { it.iso_639_1 == "es" || it.iso_639_1 == "en" }
                            .forEach { t -> t.data?.title?.let(::add) }
                    }.filter { it.isNotBlank() }.distinct()
                }.let { englishTitle(it.translations) ?: it.original_title }
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
        title?.also { releaseTitles[item.key] = it } ?: item.title
    }

    /** Nombres del título en TMDB (español, inglés, original), para [ShowNameFilter] y [CastellanoMatcher]. */
    suspend fun showTitlesFor(item: CatalogItem): List<String> {
        releaseTitle(item) // rellena showTitles (una petición por título, en caché)
        return showTitles[item.key].orEmpty().ifEmpty { listOf(item.title) }
    }

    /** Anime japonés según TMDB (se sabe tras [releaseTitle]). */
    fun isAnime(item: CatalogItem): Boolean = animeSeasons.containsKey(item.key)

    /** Número absoluto del capítulo en un anime (los fansubs numeran 1, 2… 75 sin temporadas), o null. */
    suspend fun absoluteEpisode(item: CatalogItem, season: Int, episode: Int): Int? {
        val counts = animeSeasons[item.key] ?: return null
        val start = seasonStarts.getOrPut("${item.key}:$season") {
            runCatching { tmdb.tvSeason(item.id.toInt(), season, key()).episodes.minOfOrNull { it.episode_number } }
                .getOrNull() ?: 1
        }
        if (start > 1) return episode
        val before = (1 until season).map { counts[it] ?: return null }
        return before.sum() + episode
    }

    /**
     * Torrentio acierta la serie por su id de IMDb salvo en las webs españolas (MejorTorrent, Wolfmax4k…), que busca
     * por título: en "Juego de tronos" colaba "El juego de la pirámide". Solo se filtran las de esas webs.
     */
    suspend fun withoutOtherShows(streams: List<TorrentioStreamDto>, item: CatalogItem): List<TorrentioStreamDto> {
        fun TorrentioStreamDto.text() = title.ifBlank { description.orEmpty() }
        if (streams.none { SPANISH_SITE.containsMatchIn(it.text()) }) return streams
        val titles = showTitlesFor(item)
        return streams.filter { dto ->
            !SPANISH_SITE.containsMatchIn(dto.text()) ||
                ShowNameFilter.matches(
                    dto.text().lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
                    titles,
                    movie = item.type != MediaType.TV,
                )
        }.also { kept ->
            if (kept.size != streams.size) Log.w("Catalog", "Webs españolas: ${streams.size - kept.size} fuentes de otro título descartadas")
        }
    }

    private companion object {
        /** Webs españolas de Torrentio: las busca por título, no por id de IMDb. */
        val SPANISH_SITE = Regex("⚙️\\s*(MejorTorrent|Wolfmax4k|Cinecalidad)", RegexOption.IGNORE_CASE)
    }
}
