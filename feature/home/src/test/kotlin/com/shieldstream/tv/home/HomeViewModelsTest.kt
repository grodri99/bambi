package com.shieldstream.tv.home

import com.shieldstream.tv.domain.CatalogRepository
import com.shieldstream.tv.domain.EpisodeReleaseRepository
import com.shieldstream.tv.domain.EpisodeTrackingRepository
import com.shieldstream.tv.domain.MyListEntry
import com.shieldstream.tv.domain.MyListRepository
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.domain.WatchProgressRepository
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.CatalogRow
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.HomeFilter
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.Profile
import com.shieldstream.tv.model.RowStyle
import com.shieldstream.tv.model.ShowEpisodeStatus
import com.shieldstream.tv.model.WatchProgress
import com.shieldstream.tv.strings.Texts
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelsTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `continue watching keeps successfully loaded items when another item fails`() = runTest {
        val show = item("show", MediaType.TV, "Show")
        val broken = item("broken", MediaType.MOVIE, "Broken")
        val catalog = mockk<CatalogRepository>()
        coEvery { catalog.getHome(HomeFilter.ALL) } returns listOf(CatalogRow("base", "Base", listOf(show)))
        coEvery { catalog.getHome(not(HomeFilter.ALL)) } returns emptyList()
        coEvery { catalog.getFeatured(any()) } returns emptyList()
        coEvery { catalog.getItem(MediaType.TV, "show") } returns show
        coEvery { catalog.getItem(MediaType.MOVIE, "broken") } throws IOException("offline")
        val progress = mapOf(
            WatchProgress.episodeKey("show", 2, 3) to progress(updatedAt = 200),
            WatchProgress.movieKey("broken") to progress(updatedAt = 100),
        )
        val viewModel = HomeViewModel(
            catalogRepository = catalog,
            watchProgress = mockk { every { this@mockk.progress } returns MutableStateFlow(progress) },
            episodeReleases = mockk { coEvery { status(any()) } returns null },
            tracking = tracking(),
            settings = mockk { every { hiddenCategories } returns MutableStateFlow(emptySet()) },
            myList = mockk { every { entries } returns MutableStateFlow(emptyList()) },
            profiles = mockk { every { active } returns MutableStateFlow(Profile("main", "Main", 0)) },
            texts = mockk(relaxed = true),
        )

        viewModel.load(HomeFilter.ALL)
        advanceUntilIdle()

        val state = viewModel.uiState.value as HomeUiState.Content
        val continueRow = state.rows.first { it.style == RowStyle.CONTINUE }
        assertEquals(listOf("show"), continueRow.items.map { it.id })
        assertEquals(2, continueRow.progress[show.key]?.season)
        assertEquals(3, continueRow.progress[show.key]?.episode)
        assertEquals("base", state.rows.last().id)
    }

    @Test
    fun `my list supports recent and title sorting filtering and completed hiding`() = runTest {
        val tv = item("tv", MediaType.TV, "Zulu")
        val movie = item("movie", MediaType.MOVIE, "Alpha")
        val catalog = mockk<CatalogRepository>()
        coEvery { catalog.getItem(MediaType.MOVIE, "movie") } returns movie
        val releases = mockk<EpisodeReleaseRepository>()
        coEvery { releases.status("tv") } returns ShowEpisodeStatus(
            tv,
            availableEpisodes = listOf(Episode(1, 1, "New", "", null, 45, "2026-01-01")),
        )
        coEvery { releases.isComplete("tv") } returns true
        val entries = listOf(
            MyListEntry(MediaType.TV, "tv", addedAt = 200),
            MyListEntry(MediaType.MOVIE, "movie", addedAt = 100),
        )
        val viewModel = MyListViewModel(
            catalogRepository = catalog,
            myList = mockk { every { this@mockk.entries } returns MutableStateFlow(entries) },
            watchProgress = mockk { every { progress } returns MutableStateFlow(emptyMap()) },
            episodeReleases = releases,
            tracking = tracking(),
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(listOf("tv", "movie"), viewModel.content().items.map { it.id })
        assertEquals(1, viewModel.content().newEpisodeCounts[tv.key])

        viewModel.setSort(MyListSort.TITLE)
        advanceUntilIdle()
        assertEquals(listOf("movie", "tv"), viewModel.content().items.map { it.id })

        viewModel.setFilter(MyListFilter.TV)
        advanceUntilIdle()
        assertEquals(listOf("tv"), viewModel.content().items.map { it.id })

        viewModel.toggleHideCompleted()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is MyListUiState.Empty)
    }

    private fun MyListViewModel.content() = uiState.value as MyListUiState.Content

    private fun tracking() = mockk<EpisodeTrackingRepository> {
        every { followedShowIds } returns MutableStateFlow(emptySet())
        every { dismissedEpisodeKeys } returns MutableStateFlow(emptySet())
    }

    private fun item(id: String, type: MediaType, title: String) = CatalogItem(
        id = id,
        imdbId = null,
        type = type,
        title = title,
        overview = "",
        posterUrl = "https://image.test/$id.jpg",
        backdropUrl = null,
        year = 2026,
        rating = null,
    )

    private fun progress(updatedAt: Long) = WatchProgress(
        positionMs = 60_000,
        durationMs = 3_600_000,
        updatedAt = updatedAt,
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    private val dispatcher = UnconfinedTestDispatcher()

    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                Dispatchers.setMain(dispatcher)
                try {
                    base.evaluate()
                } finally {
                    Dispatchers.resetMain()
                }
            }
        }
}
