package com.shieldstream.tv.home

import com.shieldstream.tv.domain.CatalogRepository
import com.shieldstream.tv.domain.EpisodeReleaseRepository
import com.shieldstream.tv.domain.EpisodeTrackingRepository
import com.shieldstream.tv.domain.MyListEntry
import com.shieldstream.tv.domain.MyListRepository
import com.shieldstream.tv.domain.NewEpisodes
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
import com.shieldstream.tv.model.Season
import com.shieldstream.tv.model.ShowAiring
import com.shieldstream.tv.model.ShowEpisodeStatus
import com.shieldstream.tv.model.WatchProgress
import com.shieldstream.tv.strings.Texts
import com.shieldstream.tv.ui.component.upNextImageUrl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
import org.junit.Assert.assertNull
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
            settings = homeSettings(),
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
    fun `home row limit updates catalog rows without limiting continue watching`() = runTest {
        val items = (1..20).map { item(it.toString(), MediaType.MOVIE, "Movie $it") }
        val catalog = mockk<CatalogRepository> {
            coEvery { getHome(any()) } returns listOf(CatalogRow("base", "Base", items))
            coEvery { getFeatured(any()) } returns emptyList()
            items.forEach { item -> coEvery { getItem(item.type, item.id) } returns item }
        }
        val progress = items.mapIndexed { index, item ->
            WatchProgress.movieKey(item.id) to progress(updatedAt = index.toLong())
        }.toMap()
        val rowItemLimit = MutableStateFlow(10)
        val viewModel = homeViewModel(
            catalog,
            mockk(),
            profileFlow(traktUser = null),
            progress = progress,
            rowItemLimit = rowItemLimit,
        )

        viewModel.load(HomeFilter.ALL)
        advanceUntilIdle()
        assertEquals((1..10).map { it.toString() }, viewModel.baseRowItems())
        assertEquals(20, viewModel.continueRowSize())

        rowItemLimit.value = 15
        advanceUntilIdle()
        assertEquals((1..15).map { it.toString() }, viewModel.baseRowItems())
        assertEquals(20, viewModel.continueRowSize())
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
            settings = homeSettings(),
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
    fun `up next targets the next unwatched aired episode`() = runTest {
        val show = item("show", MediaType.TV, "Show")
        val today = NewEpisodes.dateWindow(System.currentTimeMillis()).second
        val first = Episode(1, 1, "First", "", null, 45, today)
        val second = Episode(1, 2, "Second", "", null, 45, today)
        val third = Episode(1, 3, "Third", "", null, 45, today)
        val catalog = catalog(show)
        coEvery { catalog.getShowAiring("show") } returns ShowAiring(
            show,
            seasons = listOf(Season(1, "Season 1", 3), Season(2, "Season 2", 1)),
        )
        coEvery { catalog.getEpisodes(show, 1) } returns listOf(first, second, third)
        val progress = mapOf(
            WatchProgress.episodeKey("show", 1, 1) to watched(100),
            WatchProgress.episodeKey("show", 1, 2) to watched(200),
        )
        val viewModel = homeViewModel(catalog, mockk(), profileFlow(), progress = progress)

        viewModel.load(HomeFilter.TV)
        advanceUntilIdle()

        val upNext = viewModel.upNextRow()!!
        assertEquals(RowStyle.UP_NEXT, upNext.style)
        assertEquals(third, upNext.upNext[show.key]?.episode)
        assertEquals(true, upNext.upNext[show.key]?.isNewEpisode)
        assertEquals(false, upNext.upNext[show.key]?.isNewSeason)
        assertEquals(UpNextBadge.NEW_EPISODE, upNextBadge(upNext.upNext[show.key]))
        assertEquals(listOf("up_next", "base"), viewModel.rowIds())
        coVerify(exactly = 0) { catalog.getEpisodes(show, 2) }
    }

    @Test
    fun `up next rejects incomplete season data without loading the following season`() = runTest {
        val show = item("show", MediaType.TV, "Show")
        val today = NewEpisodes.dateWindow(System.currentTimeMillis()).second
        val first = Episode(1, 1, "First", "", null, 45, today)
        val catalog = catalog(show)
        coEvery { catalog.getShowAiring("show") } returns ShowAiring(
            show,
            seasons = listOf(Season(1, "Season 1", 2), Season(2, "Season 2", 1)),
        )
        coEvery { catalog.getEpisodes(show, 1) } returns listOf(first)
        val progress = mapOf(WatchProgress.episodeKey("show", 1, 1) to watched(100))
        val viewModel = homeViewModel(catalog, mockk(), profileFlow(), progress = progress)

        viewModel.load(HomeFilter.TV)
        advanceUntilIdle()

        assertNull(viewModel.upNextRow())
        coVerify(exactly = 0) { catalog.getEpisodes(show, 2) }
    }

    @Test
    fun `up next crosses a season boundary only after a complete season is exhausted`() = runTest {
        val show = item("show", MediaType.TV, "Show")
        val today = NewEpisodes.dateWindow(System.currentTimeMillis()).second
        val first = Episode(1, 1, "First", "", null, 45, today)
        val second = Episode(1, 2, "Second", "", null, 45, today)
        val following = Episode(2, 1, "Following", "", null, 45, today)
        val catalog = catalog(show)
        coEvery { catalog.getShowAiring("show") } returns ShowAiring(
            show,
            seasons = listOf(Season(1, "Season 1", 2), Season(2, "Season 2", 1)),
        )
        coEvery { catalog.getEpisodes(show, 1) } returns listOf(first, second)
        coEvery { catalog.getEpisodes(show, 2) } returns listOf(following)
        val progress = mapOf(
            WatchProgress.episodeKey("show", 1, 1) to watched(100),
            WatchProgress.episodeKey("show", 1, 2) to watched(200),
        )
        val viewModel = homeViewModel(catalog, mockk(), profileFlow(), progress = progress)

        viewModel.load(HomeFilter.TV)
        advanceUntilIdle()

        val info = viewModel.upNextRow()?.upNext?.get(show.key)
        assertEquals(following, info?.episode)
        assertEquals(true, info?.isNewEpisode)
        assertEquals(true, info?.isNewSeason)
        assertEquals(UpNextBadge.NEW_SEASON, upNextBadge(info))
        coVerify(exactly = 1) { catalog.getEpisodes(show, 1) }
        coVerify(exactly = 1) { catalog.getEpisodes(show, 2) }
    }

    @Test
    fun `up next keeps posterless shows with fallback artwork`() = runTest {
        val show = item("show", MediaType.TV, "Show").copy(posterUrl = null, backdropUrl = "https://image.test/backdrop.jpg")
        val first = Episode(1, 1, "First", "", null, 45, "2026-11-01")
        val second = Episode(1, 2, "Second", "", "https://image.test/still.jpg", 45, "2026-11-02")
        val catalog = catalog(show)
        coEvery { catalog.getShowAiring("show") } returns ShowAiring(show, seasons = listOf(Season(1, "Season 1", 2)))
        coEvery { catalog.getEpisodes(show, 1) } returns listOf(first, second)
        val progress = mapOf(WatchProgress.episodeKey("show", 1, 1) to watched(100))
        val viewModel = homeViewModel(catalog, mockk(), profileFlow(), progress = progress)

        viewModel.load(HomeFilter.TV)
        advanceUntilIdle()

        assertEquals(second, viewModel.upNextRow()?.upNext?.get(show.key)?.episode)
    }

    @Test
    fun `up next badges reject same day and older air dates`() = runTest {
        val today = NewEpisodes.dateWindow(System.currentTimeMillis()).second
        val sameDayShow = item("same-day", MediaType.TV, "Same day")
        val oldSeasonShow = item("old-season", MediaType.TV, "Old season")
        val sameDayTarget = Episode(1, 2, "Same day target", "", null, 45, today)
        val oldSeasonTarget = Episode(2, 1, "Old season target", "", null, 45, "2000-01-01")
        val catalog = catalog(sameDayShow, oldSeasonShow)
        coEvery { catalog.getShowAiring("same-day") } returns ShowAiring(
            sameDayShow,
            seasons = listOf(Season(1, "Season 1", 2)),
        )
        coEvery { catalog.getShowAiring("old-season") } returns ShowAiring(
            oldSeasonShow,
            seasons = listOf(Season(1, "Season 1", 1), Season(2, "Season 2", 1)),
        )
        coEvery { catalog.getEpisodes(sameDayShow, 1) } returns listOf(
            Episode(1, 1, "Watched", "", null, 45, today),
            sameDayTarget,
        )
        coEvery { catalog.getEpisodes(oldSeasonShow, 1) } returns listOf(
            Episode(1, 1, "Watched", "", null, 45, "2000-01-01"),
        )
        coEvery { catalog.getEpisodes(oldSeasonShow, 2) } returns listOf(oldSeasonTarget)
        val now = System.currentTimeMillis()
        val progress = mapOf(
            WatchProgress.episodeKey("same-day", 1, 1) to watched(now),
            WatchProgress.episodeKey("old-season", 1, 1) to watched(now - 1),
        )
        val viewModel = homeViewModel(catalog, mockk(), profileFlow(), progress = progress)

        viewModel.load(HomeFilter.TV)
        advanceUntilIdle()

        val rows = viewModel.upNextRow()?.upNext.orEmpty()
        assertEquals(false, rows[sameDayShow.key]?.isNewEpisode)
        assertEquals(UpNextBadge.UP_NEXT, upNextBadge(rows[sameDayShow.key]))
        assertEquals(false, rows[oldSeasonShow.key]?.isNewEpisode)
        assertEquals(true, rows[oldSeasonShow.key]?.isNewSeason)
        assertEquals(UpNextBadge.UP_NEXT, upNextBadge(rows[oldSeasonShow.key]))
    }

    @Test
    fun `up next artwork prefers still then backdrop then poster`() {
        val show = item("show", MediaType.TV, "Show").copy(
            posterUrl = "poster",
            backdropUrl = "backdrop",
        )
        val withoutStill = Episode(1, 2, "Target", "", null, 45)
        val withStill = withoutStill.copy(stillUrl = "still")

        assertEquals("still", upNextImageUrl(show, withStill))
        assertEquals("backdrop", upNextImageUrl(show, withoutStill))
        assertEquals("poster", upNextImageUrl(show.copy(backdropUrl = null), withoutStill))
        assertNull(upNextImageUrl(show.copy(backdropUrl = null, posterUrl = null), withoutStill))
    }

    @Test
    fun `up next ignores timestamp only updates for watched progress`() = runTest {
        val show = item("show", MediaType.TV, "Show")
        val today = NewEpisodes.dateWindow(System.currentTimeMillis()).second
        val first = Episode(1, 1, "First", "", null, 45, today)
        val second = Episode(1, 2, "Second", "", null, 45, today)
        val catalog = catalog(show)
        coEvery { catalog.getShowAiring("show") } returns ShowAiring(
            show,
            seasons = listOf(Season(1, "Season 1", 2)),
        )
        coEvery { catalog.getEpisodes(show, 1) } returns listOf(first, second)
        val key = WatchProgress.episodeKey("show", 1, 1)
        val progress = MutableStateFlow(mapOf(key to watched(100)))
        val watchProgress = mockk<WatchProgressRepository> { every { this@mockk.progress } returns progress }
        val viewModel = homeViewModel(catalog, mockk(), profileFlow(), watchProgress = watchProgress)

        viewModel.load(HomeFilter.TV)
        advanceUntilIdle()
        assertEquals(second, viewModel.upNextRow()?.upNext?.get(show.key)?.episode)

        progress.value = mapOf(key to WatchProgress(3_500_000, 3_600_000, updatedAt = 200))
        advanceUntilIdle()

        assertEquals(second, viewModel.upNextRow()?.upNext?.get(show.key)?.episode)
        coVerify(exactly = 1) { catalog.getShowAiring("show") }
        coVerify(exactly = 1) { catalog.getEpisodes(show, 1) }
    }

    @Test
    fun `mark up next watched updates Trakt and local progress`() = runTest {
        val show = item("show", MediaType.TV, "Show")
        val episode = Episode(1, 2, "Second", "", null, 45, "2026-11-02")
        val key = WatchProgress.episodeKey(show.id, episode.season, episode.number)
        val watchProgress = mockk<WatchProgressRepository> {
            every { progress } returns MutableStateFlow(emptyMap())
            every { this@mockk.get(key) } returns null
            every { save(any(), any(), any()) } returns Unit
        }
        val trakt = mockk<TraktRepository>(relaxed = true)
        val viewModel = homeViewModel(
            catalog = catalog(show),
            trakt = trakt,
            active = profileFlow(),
            watchProgress = watchProgress,
        )

        viewModel.markUpNextWatched(show, episode)

        verify(exactly = 1) { trakt.setWatched(listOf(key), watched = true) }
        verify(exactly = 1) { watchProgress.save(key, 45 * 60_000L, 45 * 60_000L) }
    }

    @Test
    fun `remove up next only dismisses the target`() = runTest {
        val show = item("show", MediaType.TV, "Show")
        val episode = Episode(1, 2, "Second", "", null, 45, "2026-11-02")
        val key = WatchProgress.episodeKey(show.id, episode.season, episode.number)
        val watchProgress = mockk<WatchProgressRepository> {
            every { progress } returns MutableStateFlow(emptyMap())
        }
        val tracking = tracking()
        val trakt = mockk<TraktRepository>(relaxed = true)
        val viewModel = homeViewModel(
            catalog = catalog(show),
            trakt = trakt,
            active = profileFlow(),
            watchProgress = watchProgress,
            episodeTracking = tracking,
        )

        viewModel.removeUpNext(show, episode)

        verify(exactly = 1) { tracking.dismissEpisodes(listOf(key)) }
        verify(exactly = 0) { watchProgress.save(any(), any(), any()) }
        verify(exactly = 0) { trakt.setWatched(any(), any()) }
    }

    @Test
    fun `up next focus key follows show identity after reordering`() {
        val first = item("first", MediaType.TV, "First")
        val second = item("second", MediaType.TV, "Second")
        val row = CatalogRow("up_next", "Up next", listOf(second, first), style = RowStyle.UP_NEXT)

        assertEquals("up_next/0", focusKeyForItem(listOf(row), row.id, second.key))
        assertEquals("up_next/1", focusKeyForItem(listOf(row), row.id, first.key))
        assertNull(focusKeyForItem(listOf(row), row.id, "missing"))
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

    @Test
    fun `handoff cancels the remaining other tab prefetch`() = runTest {
        val movie = item("movie", MediaType.MOVIE, "Movie")
        val catalog = catalog(movie)
        val tvPrefetchStarted = CompletableDeferred<Unit>()
        val tvPrefetch = CompletableDeferred<List<CatalogRow>>()
        coEvery { catalog.getHome(HomeFilter.TV) } coAnswers {
            tvPrefetchStarted.complete(Unit)
            tvPrefetch.await()
        }
        val viewModel = homeViewModel(
            catalog = catalog,
            trakt = mockk(relaxed = true),
            active = profileFlow(traktUser = null),
        )

        viewModel.load(HomeFilter.ALL)
        advanceUntilIdle()
        assertTrue(tvPrefetchStarted.isCompleted)

        viewModel.cancelBackgroundPrefetch()
        advanceUntilIdle()

        coVerify(exactly = 0) { catalog.getHome(HomeFilter.MOVIES) }
    }

    private fun MyListViewModel.content() = uiState.value as MyListUiState.Content

    private fun HomeViewModel.rowIds() = (uiState.value as HomeUiState.Content).rows.map { it.id }

    private fun HomeViewModel.recommendedRow() =
        (uiState.value as HomeUiState.Content).rows.firstOrNull { it.id == "recommended_for_you" }

    private fun HomeViewModel.baseRowItems() =
        (uiState.value as HomeUiState.Content).rows.single { it.id == "base" }.items.map { it.id }

    private fun HomeViewModel.continueRowSize() =
        (uiState.value as HomeUiState.Content).rows.single { it.style == RowStyle.CONTINUE }.items.size

    private fun HomeViewModel.upNextRow() =
        (uiState.value as HomeUiState.Content).rows.firstOrNull { it.id == "up_next" }

    private fun profileFlow(
        profileId: String = "main",
        traktUser: String? = "viewer",
    ) = MutableStateFlow(Profile(profileId, "Main", 0, traktUser = traktUser))

    private fun homeViewModel(
        catalog: CatalogRepository,
        trakt: TraktRepository,
        active: MutableStateFlow<Profile>,
        progress: Map<String, WatchProgress> = emptyMap(),
        watchProgress: WatchProgressRepository? = null,
        episodeTracking: EpisodeTrackingRepository = tracking(),
        rowItemLimit: MutableStateFlow<Int> = MutableStateFlow(SettingsRepository.DEFAULT_HOME_ROW_ITEM_LIMIT),
    ) = HomeViewModel(
        catalogRepository = catalog,
        watchProgress = watchProgress ?: mockk { every { this@mockk.progress } returns MutableStateFlow(progress) },
        episodeReleases = mockk(),
        tracking = episodeTracking,
        settings = homeSettings(rowItemLimit),
        myList = mockk { every { entries } returns MutableStateFlow(emptyList()) },
        profiles = mockk { every { this@mockk.active } returns active },
        trakt = trakt,
        texts = mockk(relaxed = true),
    )

    private fun homeSettings(
        rowItemLimit: MutableStateFlow<Int> = MutableStateFlow(SettingsRepository.DEFAULT_HOME_ROW_ITEM_LIMIT),
    ) = mockk<SettingsRepository> {
        every { hiddenCategories } returns MutableStateFlow(emptySet())
        every { homeRowItemLimit } returns rowItemLimit
    }

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
        every { dismissEpisodes(any()) } returns Unit
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

    private fun watched(updatedAt: Long) = WatchProgress(
        positionMs = 3_600_000,
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
