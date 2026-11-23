package com.shieldstream.tv.data.repository.streams

import com.shieldstream.tv.data.repository.StreamLog
import com.shieldstream.tv.data.repository.StreamTexts
import com.shieldstream.tv.data.repository.CastellanoMatcher
import com.shieldstream.tv.domain.SourceProvider
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.SpanishAudio
import com.shieldstream.tv.model.StreamSource
import com.shieldstream.tv.network.api.TorrentioApi
import com.shieldstream.tv.network.dto.TorrentioStreamDto
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Buscador propio de castellano (ver [SourceProvider.CASTELLANO]): a la vez, las webs españolas de Torrentio y
 * búsquedas con el título en español en Knaben y The Pirate Bay. Se queda con lo que es de este título y dice
 * traer castellano, sin repetidos, y ordena por lo claro que lo dice el nombre, calidad y seeds.
 */
class CastellanoSearch(
    private val torrentio: TorrentioApi,
    private val indexers: TorrentIndexers,
    private val titles: ReleaseTitles,
    private val texts: StreamTexts,
    private val canPickFile: suspend () -> Boolean,
) {
    suspend fun streams(item: CatalogItem, type: String, streamId: String): List<StreamSource> = coroutineScope {
        val parts = streamId.split(':')
        val season = parts.getOrNull(1)?.toIntOrNull()
        val episode = parts.getOrNull(2)?.toIntOrNull()
        val names = titles.showTitlesFor(item)
        val matcher = CastellanoMatcher(listOf(item.title) + names, season, episode, item.year)

        val sites = async {
            runCatching {
                titles.withoutOtherShows(
                    torrentio.streamsAt("${SourceProvider.TORRENTIO.baseUrl}/$SPANISH_SITES_CONFIG/stream/$type/$streamId.json").streams,
                    item,
                )
            }.onFailure { StreamLog.w("Catalog", "Castellano (webs españolas) falló: ${it.message}") }.getOrDefault(emptyList())
        }
        val searches = listOf(SourceProvider.KNABEN, SourceProvider.THEPIRATEBAY).map { p ->
            async {
                runCatching { search(p, matcher) }
                    .onFailure { StreamLog.w("Catalog", "Castellano (${p.name}) falló: ${it.message}") }.getOrDefault(emptyList())
            }
        }
        // Cada resultado va a la sección del sitio del que sale (Torrentio, Knaben, The Pirate Bay): ya no hay una
        // sección "En castellano" aparte, que se confundía con el filtro "Solo español".
        val fromSites = sites.await().map { it to SourceProvider.TORRENTIO }
        val fromSearches = searches.awaitAll().zip(listOf(SourceProvider.KNABEN, SourceProvider.THEPIRATEBAY))
            .flatMap { (list, p) -> list.map { it to p } }
        StreamLog.w("Catalog", "Castellano: ${fromSites.size} de webs españolas + ${fromSearches.size} de búsquedas ${matcher.queries}")
        val pickFile = canPickFile()
        (fromSites + fromSearches)
            .distinctBy { it.first.infoHash?.lowercase() }
            .mapNotNull { (dto, origin) -> dto.toStreamSource(item)?.copy(provider = texts.providerSection(origin)) }
            .filter { it.spanishAudioScore >= SpanishAudio.CASTELLANO_MIN }
            .filter { season == null || episode == null || pickFile || isSingleEpisode(it.title, season, episode) }
            .map { if (season != null) it.copy(season = season, episode = episode) else it }
            .sortedWith(
                compareByDescending<StreamSource> { it.spanishAudioScore }
                    .thenByDescending { it.quality.rank }
                    .thenByDescending { it.seeders ?: 0 },
            )
    }

    private suspend fun search(p: SourceProvider, matcher: CastellanoMatcher): List<TorrentioStreamDto> =
        matcher.queries.flatMap { query ->
            val hits = if (p == SourceProvider.THEPIRATEBAY) indexers.pirateBay(query) else indexers.knaben(query, categories = null)
            hits.filter { matcher.accepts(it.name) }.map { indexers.dto(p, it) }
        }

    private fun isSingleEpisode(name: String, season: Int, episode: Int): Boolean =
        Regex("(?i)s0*$season\\W?e0*$episode(?!\\d)|(?<!\\d)$season[x.]0*$episode(?!\\d)").containsMatchIn(name)

    private companion object {
        /** Torrentio limitado a las webs españolas, con el castellano delante. */
        const val SPANISH_SITES_CONFIG = "providers=mejortorrent,wolfmax4k,cinecalidad|language=spanish"
    }
}
