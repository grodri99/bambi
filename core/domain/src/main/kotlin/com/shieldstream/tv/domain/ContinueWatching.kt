package com.shieldstream.tv.domain

import com.shieldstream.tv.model.HomeFilter
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.WatchProgress

data class ContinueEntry(
    val type: MediaType,
    val id: String,
    val season: Int?,
    val episode: Int?,
    val progress: WatchProgress,
)

object ContinueWatching {
    const val MAX_ITEMS = 15

    fun entries(all: Map<String, WatchProgress>, filter: HomeFilter): List<ContinueEntry> {
        val parsed = all.mapNotNull { (key, progress) -> parse(key, progress) }
        val movies = parsed.filter { it.type == MediaType.MOVIE }
        val shows = parsed
            .filter { it.type == MediaType.TV }
            .groupBy { it.id }
            .values
            .map { episodes -> episodes.maxBy { it.progress.updatedAt } }
        return (movies + shows)
            .filter { it.progress.isResumable }
            .filter {
                when (filter) {
                    HomeFilter.ALL -> true
                    HomeFilter.MOVIES -> it.type == MediaType.MOVIE
                    HomeFilter.TV -> it.type == MediaType.TV
                }
            }
            .sortedByDescending { it.progress.updatedAt }
            .take(MAX_ITEMS)
    }

    fun keysToRemove(all: Map<String, WatchProgress>, type: MediaType, id: String): List<String> =
        if (type == MediaType.MOVIE) {
            listOf(WatchProgress.movieKey(id))
        } else {
            all.filter { (key, progress) -> key.startsWith(WatchProgress.showPrefix(id)) && progress.isResumable }
                .keys.toList()
        }

    private fun parse(key: String, progress: WatchProgress): ContinueEntry? {
        val parts = key.split(':')
        return when {
            parts.size == 2 && parts[0] == "movie" ->
                ContinueEntry(MediaType.MOVIE, parts[1], null, null, progress)
            parts.size == 4 && parts[0] == "tv" ->
                ContinueEntry(
                    type = MediaType.TV,
                    id = parts[1],
                    season = parts[2].toIntOrNull() ?: return null,
                    episode = parts[3].toIntOrNull() ?: return null,
                    progress = progress,
                )
            else -> null
        }
    }
}
