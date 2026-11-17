package com.shieldstream.tv.data.repository.streams

import android.util.Log
import com.shieldstream.tv.domain.SourceProvider
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.StreamSource
import com.shieldstream.tv.network.dto.TorrentioStreamDto
import com.shieldstream.tv.strings.Texts
import com.shieldstream.tv.strings.titleResource

/**
 * Búsqueda por título en los buscadores propios (The Pirate Bay, Knaben, Nyaa): consultas con el nombre en inglés
 * y el capítulo (o el año), y se queda con lo que empieza por ese título. Los packs de temporada solo valen si la
 * app sabe elegir el capítulo dentro ([canPickFile]).
 */
internal class ScraperSearch(
    private val indexers: TorrentIndexers,
    private val titles: ReleaseTitles,
    private val texts: Texts,
    private val canPickFile: suspend () -> Boolean,
) {
    suspend fun streams(p: SourceProvider, item: CatalogItem, streamId: String): List<StreamSource> {
        val pickFile = canPickFile()
        val parts = streamId.split(':')
        val season = parts.getOrNull(1)?.toIntOrNull()
        val episode = parts.getOrNull(2)?.toIntOrNull()
        val episodeTag = if (season != null && episode != null) "S%02dE%02d".format(season, episode) else null
        val seasonTag = season?.let { "S%02d".format(it) }
        val title = titles.releaseTitle(item)
        val wanted = normalize(title)
        val queries = if (episodeTag != null) listOf("$title $episodeTag", "$title $seasonTag") else listOf("$title ${item.year ?: ""}".trim())
        val absolute = if (season != null && episode != null) titles.absoluteEpisode(item, season, episode) else null
        val anime = item.genres.any { it.contains("anim", ignoreCase = true) } || titles.isAnime(item)
        val allQueries = if (absolute != null) queries + "$title ${"%02d".format(absolute)}" else queries
        val packRx = season?.let { Regex("(?i)(\\bS0*$it\\b(?!\\s*E\\d)|season\\s*0*$it\\b|temporada\\s*0*$it\\b|complete)") }
        var seen = 0
        val titleRx = Regex(
            "^(?:\\[[^\\]]*\\]\\s*)*" + title.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
                .joinToString("[\\W_]+") { Regex.escape(it) } +
                "[\\W_]+(?:\\(?(?:19|20)\\d{2}|s\\d|season|temporada|complete|\\d{3,4}p|\\d+x\\d)",
            RegexOption.IGNORE_CASE,
        )
        val absoluteRx = absolute?.let {
            Regex(
                "^(?:\\[[^\\]]*\\]\\s*)*" + title.split(Regex("[^\\p{L}\\p{N}]+")).filter { w -> w.isNotBlank() }
                    .joinToString("[\\W_]+") { w -> Regex.escape(w) } +
                    "[\\W_]+(?:e|ep|episode|episodio)?\\s*0*$it(?:v\\d)?(?!\\d|\\s*[-~]\\s*\\d)",
                RegexOption.IGNORE_CASE,
            )
        }
        fun accepts(name: String): Boolean {
            seen++
            if (!normalize(name).contains(wanted)) return false
            if (absoluteRx?.containsMatchIn(name) == true) return true
            if (!titleRx.containsMatchIn(name)) return false
            if (episodeTag == null) return item.year == null || name.contains(item.year.toString())
            // Los packs de temporada solo si la app sabe elegir el capítulo dentro (resolución propia).
            return name.contains(episodeTag, ignoreCase = true) || (pickFile && packRx?.containsMatchIn(name) == true)
        }
        val found = mutableListOf<TorrentioStreamDto>()
        for (query in allQueries) {
            if (p == SourceProvider.NYAA && !anime) break
            val hits = when (p) {
                SourceProvider.THEPIRATEBAY -> indexers.pirateBay(query)
                SourceProvider.KNABEN -> indexers.knaben(
                    query,
                    categories = listOf(if (episodeTag != null) 2000000 else 3000000) + if (anime) listOf(6000000) else emptyList(),
                )
                else -> indexers.nyaa(query)
            }
            hits.filter { accepts(it.name) }.forEach { found += indexers.dto(p, it) }
        }
        Log.w("Catalog", "${texts.get(p.titleResource)}: ${found.size} aceptadas de $seen (consultas: $allQueries)")
        return found.distinctBy { it.infoHash }.mapNotNull { it.toStreamSource(item) }
            .map { if (season != null) it.copy(season = season, episode = episode) else it }
            .sortedByDescending { it.seeders ?: 0 }
    }

    private fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
}
