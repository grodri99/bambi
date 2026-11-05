package com.shieldstream.tv.domain

import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.HomeFilter
import com.shieldstream.tv.model.Season
import com.shieldstream.tv.model.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpNextTest {

    @Test
    fun `candidates use latest activity only when it is watched`() {
        val progress = mapOf(
            WatchProgress.episodeKey("older", 1, 1) to watched(updatedAt = 100),
            WatchProgress.episodeKey("newer", 2, 3) to watched(updatedAt = 300),
            WatchProgress.episodeKey("playing", 1, 2) to resumable(updatedAt = 400),
            WatchProgress.episodeKey("playing", 1, 1) to watched(updatedAt = 200),
            WatchProgress.movieKey("movie") to watched(updatedAt = 500),
            "tv:broken" to watched(updatedAt = 600),
        )

        assertEquals(
            listOf("newer" to 3, "older" to 1),
            UpNext.candidates(progress, HomeFilter.TV).map { it.showId to it.episode },
        )
        assertEquals(emptyList<UpNextCandidate>(), UpNext.candidates(progress, HomeFilter.MOVIES))
    }

    @Test
    fun `special activity participates in latest selection without allowing a resumable special`() {
        val regular = WatchProgress.episodeKey(SHOW, 1, 1)
        val special = WatchProgress.episodeKey(SHOW, 0, 1)
        val progress = mapOf(
            regular to watched(updatedAt = 100),
            special to resumable(updatedAt = 200),
        )

        assertEquals(emptyList<UpNextCandidate>(), UpNext.candidates(progress, HomeFilter.TV))

        val candidates = UpNext.candidates(progress + (special to watched(updatedAt = 300)), HomeFilter.TV)
        assertEquals(listOf(0), candidates.map { it.season })
        val episodes = mapOf(1 to listOf(episode(1, 1), episode(1, 2)))
        val next = UpNext.nextEpisode(
            candidates.single(),
            episodes,
            watchedKeys = setOf(WatchProgress.episodeKey(SHOW, 1, 1)),
            dismissedKeys = emptySet(),
            today = TODAY,
        )
        assertEquals(1, next?.season)
        assertEquals(2, next?.number)
    }

    @Test
    fun `only current and following seasons are requested`() {
        val one = Season(1, "One", 3)
        val two = Season(2, "Two", 4)
        val three = Season(3, "Three", 5)
        val seasons = listOf(one, two, three)

        assertEquals(listOf(two, three), UpNext.seasonsToLoad(candidate(season = 2, episode = 4), seasons))
        assertEquals(listOf(one, two), UpNext.seasonsToLoad(candidate(season = 1, episode = 1), seasons))
        assertEquals(listOf(one, two), UpNext.seasonsToLoad(candidate(season = 0, episode = 1), seasons))
        assertEquals(emptyList<Season>(), UpNext.seasonsToLoad(candidate(season = 4, episode = 1), seasons))
    }

    @Test
    fun `episode lists must be complete before targets are selected`() {
        val season = Season(1, "One", 3)
        val complete = listOf(episode(1, 1), episode(1, 2), episode(1, 3))

        assertTrue(UpNext.hasCompleteEpisodeList(season, complete.reversed()))
        assertFalse(UpNext.hasCompleteEpisodeList(season, emptyList()))
        assertFalse(UpNext.hasCompleteEpisodeList(season, complete - complete[1]))
        assertFalse(UpNext.hasCompleteEpisodeList(season, listOf(complete[0], complete[1], episode(1, 4))))
    }

    @Test
    fun `next episode skips watched entries and crosses season boundaries`() {
        val episodes = mapOf(
            1 to listOf(episode(1, 2), episode(1, 3)),
            2 to listOf(episode(2, 1), episode(2, 2)),
        )
        val watched = setOf(
            WatchProgress.episodeKey(SHOW, 1, 1),
            WatchProgress.episodeKey(SHOW, 1, 2),
            WatchProgress.episodeKey(SHOW, 1, 3),
            WatchProgress.episodeKey(SHOW, 2, 1),
        )

        val next = UpNext.nextEpisode(
            candidate(season = 1, episode = 1),
            episodes,
            watchedKeys = watched,
            dismissedKeys = emptySet(),
            today = TODAY,
        )

        assertEquals(2, next?.season)
        assertEquals(2, next?.number)
    }

    @Test
    fun `future or dismissed next episode hides the show instead of skipping ahead`() {
        val episodes = mapOf(1 to listOf(episode(1, 2, airDate = "2026-11-06"), episode(1, 3)))

        assertNull(UpNext.nextEpisode(candidate(), episodes, emptySet(), emptySet(), TODAY))
        assertNull(
            UpNext.nextEpisode(
                candidate(),
                mapOf(1 to listOf(episode(1, 2), episode(1, 3))),
                watchedKeys = emptySet(),
                dismissedKeys = setOf(WatchProgress.episodeKey(SHOW, 1, 2)),
                today = TODAY,
            ),
        )
    }

    private fun candidate(season: Int = 1, episode: Int = 1) =
        UpNextCandidate(SHOW, season, episode, updatedAt = 100)

    private fun episode(season: Int, number: Int, airDate: String = TODAY) =
        Episode(season, number, "E$number", "", null, 45, airDate)

    private fun watched(updatedAt: Long) = WatchProgress(3_600_000, 3_600_000, updatedAt)

    private fun resumable(updatedAt: Long) = WatchProgress(600_000, 3_600_000, updatedAt)

    private companion object {
        const val SHOW = "show"
        const val TODAY = "2026-11-05"
    }
}
