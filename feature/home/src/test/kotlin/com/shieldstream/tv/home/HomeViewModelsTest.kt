package com.shieldstream.tv.home

import com.shieldstream.tv.domain.CatalogRepository
import com.shieldstream.tv.domain.EpisodeReleaseRepository
import com.shieldstream.tv.domain.EpisodeTrackingRepository
import com.shieldstream.tv.domain.MyListEntry
import com.shieldstream.tv.domain.MyListRepository
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.domain.TraktRepository
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
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
            trakt = mockk(),
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
    fun `recommended row mixes Trakt feeds and keeps successfully hydrated items`() = runTest {
        val movie = item("movie", MediaType.MOVIE, "Movie")
        val show = item("show", MediaType.TV, "Show")
        val catalog = mockk<CatalogRepository>()
        coEvery { catalog.getHome(HomeFilter.ALL) } returns listOf(CatalogRow("base", "Base", listOf(movie)))
        coEvery { catalog.getHome(not(HomeFilter.ALL)) } returns emptyList()
        coEvery { catalog.getFeatured(any()) } returns emptyList()
        coEvery { catalog.getItem(MediaType.MOVIE, "movie") } returns movie
        coEvery { catalog.getItem(MediaType.MOVIE, "broken") } throws IOException("offline")
        coEvery { catalog.getItem(MediaType.TV, "show") } returns show
        val trakt = mockk<TraktRepository>()
        coEvery { trakt.recommendations("main", MediaType.MOVIE) } returns listOf("movie", "broken")
        coEvery { trakt.recommendations("main", MediaType.TV) } returns listOf("show")
        val viewModel = HomeViewModel(
            catalogRepository = catalog,
            watchProgress = mockk { every { progress } returns MutableStateFlow(emptyMap()) },
            episodeReleases = mockk(),
            tracking = tracking(),
            settings = mockk { every { hiddenCategories } returns MutableStateFlow(emptySet()) },
            myList = mockk { every { entries } returns MutableStateFlow(emptyList()) },
            profiles = mockk {
                every { active } returns MutableStateFlow(Profile("main", "Main", 0, traktUser = "viewer"))
            },
            trakt = trakt,
            texts = mockk(relaxed = true),
        )

        viewModel.load(HomeFilter.ALL)
        advanceUntilIdle()

        val state = viewModel.uiState.value as HomeUiState.Content
        val recommended = state.rows.first { it.id == "recommended_for_you" }
        assertEquals(listOf("movie", "show"), recommended.items.map { it.id })
        assertEquals(listOf("recommended_for_you", "base"), state.rows.map { it.id })
    }

    @Test
    fun `recommendations do not block existing home content`() = runTest {
        val movie = item("movie", MediaType.MOVIE, "Movie")
        val pending = CompletableDeferred<List<String>>()
        val trakt = mockk<TraktRepository>()
        coEvery { trakt.recommendations("main", MediaType.MOVIE) } coAnswers { pending.await() }
        val viewModel = homeViewModel(catalog(movie), trakt, profileFlow())

        viewModel.load(HomeFilter.MOVIES)
        runCurrent()

        assertEquals(listOf("base"), viewModel.rowIds())

        pending.complete(listOf("movie"))
        runCurrent()

        assertEquals(listOf("recommended_for_you", "base"), viewModel.rowIds())
        assertEquals(listOf("movie"), viewModel.recommendedRow()?.items?.map { it.id })
    }

    @Test
    fun `recommendations follow movie and TV home filters`() = runTest {
        val movie = item("movie", MediaType.MOVIE, "Movie")
        val show = item("show", MediaType.TV, "Show")
        val trakt = mockk<TraktRepository>()
        coEvery { trakt.recommendations("main", MediaType.MOVIE) } returns listOf("movie")
        coEvery { trakt.recommendations("main", MediaType.TV) } returns listOf("show")
        val viewModel = homeViewModel(catalog(movie, show), trakt, profileFlow())

        viewModel.load(HomeFilter.MOVIES)
        advanceUntilIdle()

        assertEquals(listOf("movie"), viewModel.recommendedRow()?.items?.map { it.id })
        coVerify(exactly = 0) { trakt.recommendations("main", MediaType.TV) }

        viewModel.load(HomeFilter.TV)
        advanceUntilIdle()

        assertEquals(listOf("show"), viewModel.recommendedRow()?.items?.map { it.id })
        coVerify(exactly = 1) { trakt.recommendations("main", MediaType.MOVIE) }
        coVerify(exactly = 1) { trakt.recommendations("main", MediaType.TV) }
    }

    @Test
    fun `empty and unusable recommendations stay hidden`() = runTest {
        val movie = item("movie", MediaType.MOVIE, "Movie")
        val catalog = catalog(movie)
        coEvery { catalog.getItem(MediaType.TV, "broken") } throws IOException("offline")
        val trakt = mockk<TraktRepository>()
        coEvery { trakt.recommendations("main", MediaType.MOVIE) } returns emptyList()
        coEvery { trakt.recommendations("main", MediaType.TV) } returns listOf("broken")
        val viewModel = homeViewModel(catalog, trakt, profileFlow())

        viewModel.load(HomeFilter.MOVIES)
        advanceUntilIdle()
        assertEquals(null, viewModel.recommendedRow())

        viewModel.load(HomeFilter.TV)
        advanceUntilIdle()
        assertEquals(null, viewModel.recommendedRow())
        assertEquals(listOf("base"), viewModel.rowIds())
    }

    @Test
    fun `recommendations react to Trakt connection and kids profile changes`() = runTest {
        val movie = item("movie", MediaType.MOVIE, "Movie")
        val active = profileFlow(traktUser = null)
        val trakt = mockk<TraktRepository>()
        coEvery { trakt.recommendations("main", MediaType.MOVIE) } returns listOf("movie")
        val viewModel = homeViewModel(catalog(movie), trakt, active)

        viewModel.load(HomeFilter.MOVIES)
        advanceUntilIdle()
        assertEquals(null, viewModel.recommendedRow())
        coVerify(exactly = 0) { trakt.recommendations(any(), any()) }

        active.value = active.value.copy(traktUser = "viewer")
        advanceUntilIdle()
        assertEquals(listOf("movie"), viewModel.recommendedRow()?.items?.map { it.id })

        active.value = active.value.copy(traktUser = null)
        advanceUntilIdle()
        assertEquals(null, viewModel.recommendedRow())

        active.value = active.value.copy(traktUser = "viewer", kids = true)
        advanceUntilIdle()
        assertEquals(null, viewModel.recommendedRow())
        coVerify(exactly = 1) { trakt.recommendations("main", MediaType.MOVIE) }
    }

    @Test
    fun `profile changes clear old rows and ignore stale in flight results`() = runTest {
        val first = item("first", MediaType.MOVIE, "First")
        val firstUpdated = item("first-updated", MediaType.MOVIE, "First updated")
        val second = item("second", MediaType.MOVIE, "Second")
        val active = profileFlow(profileId = "first", traktUser = "first")
        val secondPending = CompletableDeferred<List<String>>()
        var firstCalls = 0
        val trakt = mockk<TraktRepository>()
        coEvery { trakt.recommendations("first", MediaType.MOVIE) } coAnswers {
            firstCalls += 1
            if (firstCalls == 1) listOf("first") else listOf("first-updated")
        }
        coEvery { trakt.recommendations("second", MediaType.MOVIE) } coAnswers {
            withContext(NonCancellable) { secondPending.await() }
        }
        val viewModel = homeViewModel(catalog(first, firstUpdated, second), trakt, active)

        viewModel.load(HomeFilter.MOVIES)
        advanceUntilIdle()
        assertEquals(listOf("first"), viewModel.recommendedRow()?.items?.map { it.id })

        active.value = Profile("second", "Second", 0, traktUser = "second")
        runCurrent()
        assertEquals(null, viewModel.recommendedRow())

        active.value = Profile("first", "First", 0, traktUser = "first")
        runCurrent()
        assertEquals(null, viewModel.recommendedRow())

        secondPending.complete(listOf("second"))
        runCurrent()

        assertEquals(listOf("first-updated"), viewModel.recommendedRow()?.items?.map { it.id })
        coVerify(exactly = 1) { trakt.recommendations("second", MediaType.MOVIE) }
        coVerify(exactly = 2) { trakt.recommendations("first", MediaType.MOVIE) }
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

    private fun HomeViewModel.rowIds() = (uiState.value as HomeUiState.Content).rows.map { it.id }

    private fun HomeViewModel.recommendedRow() =
        (uiState.value as HomeUiState.Content).rows.firstOrNull { it.id == "recommended_for_you" }

    private fun profileFlow(
        profileId: String = "main",
        traktUser: String? = "viewer",
    ) = MutableStateFlow(Profile(profileId, "Main", 0, traktUser = traktUser))

    private fun homeViewModel(
        catalog: CatalogRepository,
        trakt: TraktRepository,
        active: MutableStateFlow<Profile>,
    ) = HomeViewModel(
        catalogRepository = catalog,
        watchProgress = mockk { every { progress } returns MutableStateFlow(emptyMap()) },
        episodeReleases = mockk(),
        tracking = tracking(),
        settings = mockk { every { hiddenCategories } returns MutableStateFlow(emptySet()) },
        myList = mockk { every { entries } returns MutableStateFlow(emptyList()) },
        profiles = mockk { every { this@mockk.active } returns active },
        trakt = trakt,
        texts = mockk(relaxed = true),
    )

    private fun catalog(base: CatalogItem, vararg hydrated: CatalogItem): CatalogRepository {
        val catalog = mockk<CatalogRepository>()
        coEvery { catalog.getHome(any()) } returns listOf(CatalogRow("base", "Base", listOf(base)))
        coEvery { catalog.getFeatured(any()) } returns emptyList()
        (listOf(base) + hydrated).forEach { item ->
            coEvery { catalog.getItem(item.type, item.id) } returns item
        }
        return catalog
    }

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
