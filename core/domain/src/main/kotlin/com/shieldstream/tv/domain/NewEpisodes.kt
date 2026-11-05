package com.shieldstream.tv.domain

import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.HomeFilter
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.WatchProgress
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object NewEpisodes {
    const val MAX_ITEMS = 15
    const val MAX_CANDIDATES = 15
    const val RECENT_DAYS = 30
    const val UPCOMING_DAYS = 30

    fun candidateIds(
        progress: Map<String, WatchProgress>,
        myList: List<MyListEntry>,
        filter: HomeFilter,
        followedShowIds: Set<String> = emptySet(),
        maxItems: Int = MAX_CANDIDATES,
    ): List<String> {
        if (filter == HomeFilter.MOVIES) return emptyList()
        val watchedShows = progress.entries
            .filter { (key, _) -> key.startsWith("tv:") }
            .sortedByDescending { it.value.updatedAt }
            .mapNotNull { (key, _) -> key.split(':').getOrNull(1) }
        val listedShows = myList.filter { it.type == MediaType.TV }.map { it.id }
        return (watchedShows + followedShowIds + listedShows).distinct().take(maxItems)
    }

    fun dateWindow(now: Long): Pair<String, String> {
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val today = isoDate(calendar.time)
        calendar.add(Calendar.DAY_OF_YEAR, -RECENT_DAYS)
        return isoDate(calendar.time) to today
    }

    fun futureDate(now: Long, days: Int = UPCOMING_DAYS): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        calendar.add(Calendar.DAY_OF_YEAR, days)
        return isoDate(calendar.time)
    }

    fun isNew(
        showId: String,
        episode: Episode,
        watchedKeys: Set<String>,
        cutoffDate: String,
        today: String,
        dismissedKeys: Set<String> = emptySet(),
    ): Boolean {
        val airDate = episode.airDate?.takeIf { ISO_DATE.matches(it) } ?: return false
        if (episode.season <= 0 || episode.number <= 0 || airDate !in cutoffDate..today) return false
        val key = WatchProgress.episodeKey(showId, episode.season, episode.number)
        return key !in watchedKeys && key !in dismissedKeys
    }

    fun isUpcoming(episode: Episode, today: String, endDate: String): Boolean {
        val airDate = episode.airDate?.takeIf { ISO_DATE.matches(it) } ?: return false
        return episode.season > 0 && episode.number > 0 && airDate > today && airDate <= endDate
    }

    private fun isoDate(date: Date): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)

    private val ISO_DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
}
