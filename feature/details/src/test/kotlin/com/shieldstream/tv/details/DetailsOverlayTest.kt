package com.shieldstream.tv.details

import com.shieldstream.tv.model.CatalogDetails
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailsOverlayTest {

    @Test
    fun `back dismisses actions then sources then Details`() {
        assertEquals(DetailsBackAction.DISMISS_ACTIONS, detailsBackAction(actionsOpen = true, panelOpen = true))
        assertEquals(DetailsBackAction.DISMISS_SOURCES, detailsBackAction(actionsOpen = false, panelOpen = true))
        assertEquals(DetailsBackAction.EXIT_DETAILS, detailsBackAction(actionsOpen = false, panelOpen = false))
    }

    @Test
    fun `sources and actions stay mutually exclusive`() {
        assertTrue(detailsSourcesVisible(panelOpen = true, actionsOpen = false))
        assertFalse(detailsSourcesVisible(panelOpen = true, actionsOpen = true))
        assertFalse(detailsSourcesVisible(panelOpen = false, actionsOpen = true))
    }

    @Test
    fun `more is hidden while playback is pending`() {
        assertTrue(detailsMoreVisible(pendingPlay = false))
        assertFalse(detailsMoreVisible(pendingPlay = true))
    }

    @Test
    fun `source languages are capped at three codes`() {
        assertEquals("", compactLanguages(emptyList()))
        assertEquals("EN", compactLanguages(listOf("EN")))
        assertEquals("EN/ES/FR", compactLanguages(listOf("EN", "ES", "FR")))
        assertEquals("EN/RU/IT +4", compactLanguages(listOf("EN", "RU", "IT", "LAT", "FR", "DE", "HI")))
    }

    @Test
    fun `release dates are shown only for future episodes`() {
        assertEquals("2026-11-13", upcomingEpisodeDate("2026-11-13", today = "2026-11-06"))
        assertEquals(null, upcomingEpisodeDate("2026-11-06", today = "2026-11-06"))
        assertEquals(null, upcomingEpisodeDate("2026-11-05", today = "2026-11-06"))
        assertEquals(null, upcomingEpisodeDate(null, today = "2026-11-06"))
        assertEquals(null, upcomingEpisodeDate("", today = "2026-11-06"))
    }

    @Test
    fun `the selected episode can be marked watched without playing it`() {
        assertEquals(
            EpisodeWatchedAction(Episode(1, 2, "Second", "", null, 45), watched = false),
            episodeWatchedAction(seriesContent(selectedEpisode = Episode(1, 2, "Second", "", null, 45))),
        )
        assertEquals(
            EpisodeWatchedAction(Episode(1, 2, "Second", "", null, 45), watched = true),
            episodeWatchedAction(
                seriesContent(selectedEpisode = Episode(1, 2, "Second", "", null, 45), watchedEpisodes = setOf(1 to 2)),
            ),
        )
    }

    @Test
    fun `episode action is unavailable before an episode is selected`() {
        assertNull(episodeWatchedAction(seriesContent(selectedEpisode = null)))
        assertNull(episodeWatchedAction(movieContent()))
    }

    @Test
    fun `episode watched state is independent from the rest of the season`() {
        val content = seriesContent(
            selectedEpisode = Episode(1, 1, "First", "", null, 45),
            watchedEpisodes = setOf(1 to 1),
        )
        assertTrue(episodeWatchedAction(content)?.watched == true)
        assertFalse(seasonAllWatched(content))
    }

    @Test
    fun `the long press modal toggles the episode it was opened on`() {
        val first = Episode(1, 1, "First", "", null, 45)
        val second = Episode(1, 2, "Second", "", null, 45)
        val content = seriesContent(selectedEpisode = first, watchedEpisodes = setOf(1 to 2))

        // The modal acts on the card that was held, not on the episode selected for playback.
        assertTrue(episodeMenuTogglesToWatched(content, first))
        assertFalse(episodeMenuTogglesToWatched(content, second))
    }

    private fun seriesContent(
        selectedEpisode: Episode?,
        watchedEpisodes: Set<Pair<Int, Int>> = emptySet(),
    ): DetailsUiState.Content {
        val item = CatalogItem(
            id = "42",
            imdbId = "tt42",
            type = MediaType.TV,
            title = "Show",
            overview = "",
            posterUrl = null,
            backdropUrl = null,
            year = 2020,
            rating = null,
        )
        val episodes = listOf(
            Episode(1, 1, "First", "", null, 45),
            Episode(1, 2, "Second", "", null, 45),
        )
        val progress = episodes
            .filter { (it.season to it.number) in watchedEpisodes }
            .associate { episode ->
                val key = WatchProgress.episodeKey(item.id, episode.season, episode.number)
                key to WatchProgress(45_000, 45_000, 1)
            }
        return DetailsUiState.Content(
            details = CatalogDetails(item = item),
            selectedSeason = 1,
            episodes = EpisodesState.Ready(episodes),
            selectedEpisode = selectedEpisode,
            progress = progress,
        )
    }

    private fun movieContent(): DetailsUiState.Content {
        val item = CatalogItem(
            id = "7",
            imdbId = "tt7",
            type = MediaType.MOVIE,
            title = "Movie",
            overview = "",
            posterUrl = null,
            backdropUrl = null,
            year = 2021,
            rating = null,
        )
        return DetailsUiState.Content(details = CatalogDetails(item = item))
    }
}
