package com.shieldstream.tv.domain

import com.shieldstream.tv.model.HomeFilter
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinueWatchingTest {

    @Test
    fun `shows contribute only their most recently touched episode`() {
        val progress = mapOf(
            WatchProgress.episodeKey("show", 1, 1) to progress(updatedAt = 100),
            WatchProgress.episodeKey("show", 1, 2) to progress(updatedAt = 300),
            WatchProgress.movieKey("movie") to progress(updatedAt = 200),
        )

        val entries = ContinueWatching.entries(progress, HomeFilter.ALL)

        assertEquals(listOf("tv:show:1:2", "movie:movie"), entries.map { it.key })
        assertEquals(2, entries.first().episode)
    }

    @Test
    fun `filters are applied after grouping shows`() {
        val progress = mapOf(
            WatchProgress.episodeKey("show", 1, 1) to progress(updatedAt = 100),
            WatchProgress.episodeKey("show", 1, 2) to progress(updatedAt = 300),
            WatchProgress.movieKey("movie") to progress(updatedAt = 200),
        )

        assertEquals(listOf("movie:movie"), ContinueWatching.entries(progress, HomeFilter.MOVIES).map { it.key })
        assertEquals(listOf("tv:show:1:2"), ContinueWatching.entries(progress, HomeFilter.TV).map { it.key })
    }

    @Test
    fun `only resumable valid entries are returned and capped`() {
        val progress = buildMap {
            repeat(20) { index ->
                put(WatchProgress.movieKey(index.toString()), progress(updatedAt = index.toLong()))
            }
            put(WatchProgress.movieKey("watched"), progress(positionMs = 3_500_000, updatedAt = 1_000))
            put(WatchProgress.movieKey("short"), progress(positionMs = 10_000, updatedAt = 1_001))
            put("tv:invalid", progress(updatedAt = 1_002))
            put("tv:show:not-a-season:1", progress(updatedAt = 1_003))
        }

        val entries = ContinueWatching.entries(progress, HomeFilter.ALL)

        assertEquals(ContinueWatching.MAX_ITEMS, entries.size)
        assertEquals("movie:19", entries.first().key)
        assertTrue(entries.none { it.id in setOf("watched", "short", "invalid", "show") })
    }

    @Test
    fun `show removal clears only resumable episodes for that exact show`() {
        val resumableFirst = WatchProgress.episodeKey("1", 1, 1)
        val resumableSecond = WatchProgress.episodeKey("1", 2, 1)
        val watched = WatchProgress.episodeKey("1", 2, 2)
        val short = WatchProgress.episodeKey("1", 2, 3)
        val otherShow = WatchProgress.episodeKey("10", 1, 1)
        val progress = mapOf(
            resumableFirst to progress(updatedAt = 100),
            resumableSecond to progress(updatedAt = 200),
            watched to progress(positionMs = 3_500_000),
            short to progress(positionMs = 10_000),
            otherShow to progress(updatedAt = 300),
        )

        assertEquals(
            setOf(resumableFirst, resumableSecond),
            ContinueWatching.keysToRemove(progress, MediaType.TV, "1").toSet(),
        )
        assertEquals(
            listOf(WatchProgress.movieKey("1")),
            ContinueWatching.keysToRemove(progress, MediaType.MOVIE, "1"),
        )
    }

    private val ContinueEntry.key: String
        get() = when (type) {
            MediaType.TV -> WatchProgress.episodeKey(id, season!!, episode!!)
            MediaType.MOVIE -> WatchProgress.movieKey(id)
        }

    private fun progress(
        positionMs: Long = 60_000,
        durationMs: Long = 3_600_000,
        updatedAt: Long = 0,
    ) = WatchProgress(positionMs, durationMs, updatedAt)
}
