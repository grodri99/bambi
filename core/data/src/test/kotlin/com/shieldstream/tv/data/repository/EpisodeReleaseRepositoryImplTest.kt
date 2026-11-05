package com.shieldstream.tv.data.repository

import com.shieldstream.tv.domain.CatalogRepository
import com.shieldstream.tv.domain.EpisodeTrackingRepository
import com.shieldstream.tv.domain.WatchProgressRepository
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.Season
import com.shieldstream.tv.model.ShowAiring
import com.shieldstream.tv.model.WatchProgress
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException

class EpisodeReleaseRepositoryImplTest {

    @Test
    fun `failed season download does not count as a fully watched season`() = runTest {
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
        val watchedEpisode = Episode(1, 1, "Watched", "", null, 45, "2020-01-01")
        val catalog = mockk<CatalogRepository>()
        coEvery { catalog.getShowAiring("42") } returns ShowAiring(
            item = item,
            seasons = listOf(Season(1, "Season 1", 1), Season(2, "Season 2", 1)),
        )
        coEvery { catalog.getEpisodes(item, 1) } returns listOf(watchedEpisode)
        coEvery { catalog.getEpisodes(item, 2) } throws IOException("season unavailable")
        val watchedKey = WatchProgress.episodeKey("42", 1, 1)
        val watchProgress = mockk<WatchProgressRepository>()
        every { watchProgress.progress } returns MutableStateFlow(
            mapOf(watchedKey to WatchProgress(positionMs = 1, durationMs = 1, updatedAt = 1)),
        )

        val repository = EpisodeReleaseRepositoryImpl(
            catalog = catalog,
            watchProgress = watchProgress,
            tracking = mockk<EpisodeTrackingRepository>(relaxed = true),
        )

        assertFalse(repository.isComplete("42"))
    }
}
