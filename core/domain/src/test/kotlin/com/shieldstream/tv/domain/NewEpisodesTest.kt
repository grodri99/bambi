package com.shieldstream.tv.domain

import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.HomeFilter
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewEpisodesTest {
    @Test
    fun `candidates combine recent history and listed shows without duplicates`() {
        val progress = mapOf(
            WatchProgress.episodeKey("20", 1, 1) to progress(updatedAt = 100),
            WatchProgress.movieKey("90") to progress(updatedAt = 400),
            WatchProgress.episodeKey("10", 1, 1) to progress(updatedAt = 300),
        )
        val listed = listOf(
            MyListEntry(MediaType.TV, "10", addedAt = 500),
            MyListEntry(MediaType.TV, "30", addedAt = 400),
            MyListEntry(MediaType.MOVIE, "91", addedAt = 300),
        )

        assertEquals(
            listOf("10", "20", "30"),
            NewEpisodes.candidateIds(progress, listed, HomeFilter.ALL),
        )
        assertEquals(
            listOf("10", "20", "30"),
            NewEpisodes.candidateIds(progress, listed, HomeFilter.TV),
        )
        assertTrue(NewEpisodes.candidateIds(progress, listed, HomeFilter.MOVIES).isEmpty())
    }

    @Test
    fun `recent unaired and watched episodes are distinguished`() {
        val latest = episode(airDate = "2026-06-15")

        assertTrue(NewEpisodes.isNew("10", latest, emptySet(), "2026-05-17", "2026-06-16"))
        assertTrue(NewEpisodes.isNew("10", latest.copy(airDate = "2026-05-17"), emptySet(), "2026-05-17", "2026-06-16"))
        assertFalse(NewEpisodes.isNew("10", latest.copy(airDate = "2026-05-16"), emptySet(), "2026-05-17", "2026-06-16"))
        assertFalse(NewEpisodes.isNew("10", latest.copy(airDate = "2026-06-17"), emptySet(), "2026-05-17", "2026-06-16"))
        assertFalse(NewEpisodes.isNew("10", latest.copy(airDate = null), emptySet(), "2026-05-17", "2026-06-16"))
        assertFalse(NewEpisodes.isNew("10", latest.copy(airDate = "yesterday"), emptySet(), "2026-05-17", "2026-06-16"))
    }

    @Test
    fun `watched latest episode is not new`() {
        val latest = episode(airDate = "2026-06-15")
        val watched = setOf(WatchProgress.episodeKey("10", latest.season, latest.number))

        assertFalse(NewEpisodes.isNew("10", latest, watched, "2026-05-17", "2026-06-16"))
        assertTrue(NewEpisodes.isNew("11", latest, watched, "2026-05-17", "2026-06-16"))
    }

    private fun episode(airDate: String?) = Episode(
        season = 2,
        number = 5,
        title = "Episode 5",
        overview = "",
        stillUrl = null,
        runtimeMinutes = 45,
        airDate = airDate,
    )

    private fun progress(updatedAt: Long) = WatchProgress(
        positionMs = 60_000,
        durationMs = 3_600_000,
        updatedAt = updatedAt,
    )
}
