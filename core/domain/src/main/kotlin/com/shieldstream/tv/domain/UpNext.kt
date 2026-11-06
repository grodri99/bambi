package com.shieldstream.tv.domain

import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.HomeFilter
import com.shieldstream.tv.model.Season
import com.shieldstream.tv.model.WatchProgress

data class UpNextCandidate(
    val showId: String,
    val season: Int,
    val episode: Int,
    val updatedAt: Long,
)

object UpNext {
    const val MAX_CANDIDATES = 10

    fun candidates(
        all: Map<String, WatchProgress>,
        filter: HomeFilter,
        maxItems: Int = MAX_CANDIDATES,
    ): List<UpNextCandidate> {
        if (filter == HomeFilter.MOVIES) return emptyList()
        return all.entries.mapNotNull { (key, progress) -> parse(key, progress) }
            .groupBy { it.showId }
            .values
            .mapNotNull { episodes -> episodes.maxByOrNull { it.updatedAt } }
            .filter { candidate ->
                all[WatchProgress.episodeKey(candidate.showId, candidate.season, candidate.episode)]?.isWatched == true
            }
            .sortedByDescending { it.updatedAt }
            .take(maxItems)
    }

    fun seasonsToLoad(candidate: UpNextCandidate, seasons: List<Season>): List<Season> {
        val ordered = seasons.filter { it.number > 0 }.sortedBy { it.number }
        val current = if (candidate.season == 0) {
            ordered.firstOrNull()
        } else {
            ordered.firstOrNull { it.number == candidate.season }
        } ?: return emptyList()
        return listOfNotNull(
            current,
            ordered.firstOrNull { it.number > current.number },
        )
    }

    fun hasCompleteEpisodeList(season: Season, episodes: List<Episode>): Boolean {
        if (season.episodeCount <= 0) return false
        val numbers = episodes
            .filter { it.season == season.number && it.number > 0 }
            .mapTo(mutableSetOf()) { it.number }
        return numbers.size == season.episodeCount && (1..season.episodeCount).all { it in numbers }
    }

    fun nextEpisode(
        candidate: UpNextCandidate,
        seasonEpisodes: Map<Int, List<Episode>>,
        watchedKeys: Set<String>,
        dismissedKeys: Set<String>,
        today: String,
    ): Episode? {
        val next = nextUnwatched(candidate, seasonEpisodes, watchedKeys) ?: return null
        val key = WatchProgress.episodeKey(candidate.showId, next.season, next.number)
        val airDate = next.airDate?.takeIf { ISO_DATE.matches(it) } ?: return null
        return next.takeIf {
            next.season > 0 && next.number > 0 && airDate <= today && key !in dismissedKeys
        }
    }

    fun hasNextUnwatched(
        candidate: UpNextCandidate,
        seasonEpisodes: Map<Int, List<Episode>>,
        watchedKeys: Set<String>,
    ): Boolean = nextUnwatched(candidate, seasonEpisodes, watchedKeys) != null

    private fun nextUnwatched(
        candidate: UpNextCandidate,
        seasonEpisodes: Map<Int, List<Episode>>,
        watchedKeys: Set<String>,
    ): Episode? = seasonEpisodes.values.flatten()
        .distinctBy { it.season to it.number }
        .filter {
            it.season > candidate.season ||
                (it.season == candidate.season && it.number > candidate.episode)
        }
        .sortedWith(compareBy<Episode> { it.season }.thenBy { it.number })
        .firstOrNull {
            WatchProgress.episodeKey(candidate.showId, it.season, it.number) !in watchedKeys
        }

    private fun parse(key: String, progress: WatchProgress): UpNextCandidate? {
        val parts = key.split(':')
        if (parts.size != 4 || parts[0] != "tv" || parts[1].isBlank()) return null
        return UpNextCandidate(
            showId = parts[1],
            season = parts[2].toIntOrNull()?.takeIf { it >= 0 } ?: return null,
            episode = parts[3].toIntOrNull()?.takeIf { it > 0 } ?: return null,
            updatedAt = progress.updatedAt,
        )
    }

    private val ISO_DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
}
