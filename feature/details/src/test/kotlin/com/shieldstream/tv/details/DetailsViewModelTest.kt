package com.shieldstream.tv.details

import com.shieldstream.tv.domain.CatalogRepository
import com.shieldstream.tv.domain.EpisodeTarget
import com.shieldstream.tv.domain.EpisodeTrackingRepository
import com.shieldstream.tv.domain.MyListRepository
import com.shieldstream.tv.domain.ITHIL_ALLDEBRID
import com.shieldstream.tv.domain.PrioritizeSourcesUseCase
import com.shieldstream.tv.domain.RealDebridRepository
import com.shieldstream.tv.domain.SelectedContentHolder
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.domain.StreamResults
import com.shieldstream.tv.domain.TraktRepository
import com.shieldstream.tv.domain.WatchProgressRepository
import com.shieldstream.tv.media.DeviceCapabilityDetector
import com.shieldstream.tv.model.AudioCodec
import com.shieldstream.tv.model.CatalogDetails
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.DeviceCapabilities
import com.shieldstream.tv.model.DeviceOptimization
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.HdrType
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.Quality
import com.shieldstream.tv.model.Season
import com.shieldstream.tv.model.SourceExclusions
import com.shieldstream.tv.model.StreamSource
import com.shieldstream.tv.model.WatchProgress
import com.shieldstream.tv.strings.Texts
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.rules.TestWatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.model.Statement

@OptIn(ExperimentalCoroutinesApi::class)
class DetailsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `play after changing season resolves the episode from the new season`() = runTest {
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
        val seasonOne = Episode(1, 1, "Old episode", "", null, 45)
        val seasonTwo = Episode(2, 1, "New episode", "", null, 45)
        val catalog = mockk<CatalogRepository>()
        coEvery { catalog.getDetails(item) } returns CatalogDetails(
            item = item,
            seasons = listOf(Season(1, "Season 1", 1), Season(2, "Season 2", 1)),
        )
        coEvery { catalog.getEpisodes(item, 1) } returns listOf(seasonOne)
        coEvery { catalog.getEpisodes(item, 2) } returns listOf(seasonTwo)
        coEvery { catalog.getStreams(item, any()) } answers {
            val episode = secondArg<Episode>()
            flowOf(StreamResults(sources = listOf(source(episode))))
        }
        val holder = SelectedContentHolder().apply { selectItem(item) }
        val viewModel = viewModel(catalog, holder)
        testScheduler.advanceUntilIdle()
        viewModel.playEpisode(seasonOne)
        testScheduler.advanceUntilIdle()
        assertEquals(1, holder.playback.value?.media?.season)

        viewModel.selectSeason(2)
        testScheduler.advanceUntilIdle()
        viewModel.play()
        testScheduler.advanceUntilIdle()

        assertEquals(2, holder.playback.value?.media?.season)
        assertEquals(1, holder.playback.value?.media?.episode)
    }

    @Test
    fun `continue autoplay prefetches sources in parallel without losing next episode`() = runTest {
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
        val target = EpisodeTarget(2, 3)
        val episode = Episode(2, 3, "Current", "", null, 45)
        val next = Episode(2, 4, "Next", "", null, 45)
        val detailsResult = CompletableDeferred<CatalogDetails>()
        val episodesResult = CompletableDeferred<List<Episode>>()
        val streamsStarted = CompletableDeferred<Unit>()
        val catalog = mockk<CatalogRepository>()
        coEvery { catalog.getDetails(item) } coAnswers { detailsResult.await() }
        coEvery { catalog.getEpisodes(item, 2) } coAnswers { episodesResult.await() }
        every { catalog.getStreams(item, any()) } answers {
            val requestedEpisode = secondArg<Episode>()
            assertEquals(target.season, requestedEpisode.season)
            assertEquals(target.number, requestedEpisode.number)
            streamsStarted.complete(Unit)
            flow {
                val sources = listOf("a", "b", "c").map { id ->
                    source(episode).copy(id = id, infoHash = "hash-$id")
                }
                emit(StreamResults(sources = sources))
                awaitCancellation()
            }
        }
        val holder = SelectedContentHolder().apply {
            selectItem(item, target, playImmediately = true)
        }

        val viewModel = viewModel(catalog, holder)
        testScheduler.advanceUntilIdle()

        assertTrue(streamsStarted.isCompleted)
        assertEquals(item.title, (viewModel.uiState.value as DetailsUiState.Content).details.item.title)
        assertNull(holder.playback.value)

        episodesResult.complete(listOf(episode, next))
        testScheduler.advanceUntilIdle()
        assertNull(holder.playback.value)

        detailsResult.complete(CatalogDetails(item = item))
        testScheduler.advanceUntilIdle()

        assertEquals(target.season, holder.playback.value?.media?.season)
        assertEquals(target.number, holder.playback.value?.media?.episode)
        assertEquals(next.number, holder.playback.value?.next?.episode)
        verify(exactly = 1) { catalog.getStreams(item, any()) }
    }

    @Test
    fun `continue autoplay reuses the saved movie source and keeps failover alternatives`() = runTest {
        val item = CatalogItem(
            id = "42",
            imdbId = "tt42",
            type = MediaType.MOVIE,
            title = "Movie",
            overview = "",
            posterUrl = null,
            backdropUrl = null,
            year = 2020,
            rating = null,
        )
        val episode = Episode(1, 1, "", "", null, null)
        val sources = listOf("a", "b", "c").map { id ->
            source(episode).copy(id = "source-$id", infoHash = "hash-$id")
        }
        val key = WatchProgress.movieKey(item.id)
        val saved = WatchProgress(60_000, 3_600_000, 1, sourceId = "source-b")
        val progress = mockk<WatchProgressRepository>(relaxed = true) {
            every { this@mockk.progress } returns MutableStateFlow(mapOf(key to saved))
            every { this@mockk.get(key) } returns saved
        }
        val catalog = mockk<CatalogRepository>()
        coEvery { catalog.getDetails(item) } returns CatalogDetails(item = item)
        every { catalog.getStreams(item, null) } returns flowOf(StreamResults(sources = sources))
        val holder = SelectedContentHolder().apply { selectItem(item, playImmediately = true) }

        viewModel(catalog, holder, progress)
        testScheduler.advanceUntilIdle()

        assertEquals(
            listOf("source-b", "source-a", "source-c"),
            holder.playback.value?.sources?.map { it.id },
        )
    }

    @Test
    fun `continue autoplay reuses the source saved for the target episode`() = runTest {
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
        val episode = Episode(2, 3, "Current", "", null, 45)
        val sources = listOf("a", "b", "c").map { id ->
            source(episode).copy(id = "source-$id", infoHash = "hash-$id")
        }
        val key = WatchProgress.episodeKey(item.id, episode.season, episode.number)
        val saved = WatchProgress(60_000, 2_700_000, 1, sourceId = "source-c")
        val progress = mockk<WatchProgressRepository>(relaxed = true) {
            every { this@mockk.progress } returns MutableStateFlow(mapOf(key to saved))
            every { this@mockk.get(key) } returns saved
        }
        val catalog = mockk<CatalogRepository>()
        coEvery { catalog.getDetails(item) } returns CatalogDetails(item = item)
        coEvery { catalog.getEpisodes(item, 2) } returns listOf(episode)
        every { catalog.getStreams(item, any()) } returns flowOf(StreamResults(sources = sources))
        val holder = SelectedContentHolder().apply {
            selectItem(item, EpisodeTarget(2, 3), playImmediately = true)
        }

        viewModel(catalog, holder, progress)
        testScheduler.advanceUntilIdle()

        assertEquals(
            listOf("source-c", "source-a", "source-b"),
            holder.playback.value?.sources?.map { it.id },
        )
    }

    @Test
    fun `manual source selection overrides the previously saved source`() = runTest {
        val item = CatalogItem(
            id = "42",
            imdbId = "tt42",
            type = MediaType.MOVIE,
            title = "Movie",
            overview = "",
            posterUrl = null,
            backdropUrl = null,
            year = 2020,
            rating = null,
        )
        val episode = Episode(1, 1, "", "", null, null)
        val sources = listOf("a", "b", "c").map { id ->
            source(episode).copy(id = "source-$id", infoHash = "hash-$id")
        }
        val key = WatchProgress.movieKey(item.id)
        val saved = WatchProgress(60_000, 3_600_000, 1, sourceId = "source-b")
        val progress = mockk<WatchProgressRepository>(relaxed = true) {
            every { this@mockk.progress } returns MutableStateFlow(mapOf(key to saved))
            every { this@mockk.get(key) } returns saved
        }
        val catalog = mockk<CatalogRepository>()
        coEvery { catalog.getDetails(item) } returns CatalogDetails(item = item)
        every { catalog.getStreams(item, null) } returns flowOf(StreamResults(sources = sources))
        val holder = SelectedContentHolder().apply { selectItem(item) }
        val viewModel = viewModel(catalog, holder, progress)
        testScheduler.advanceUntilIdle()

        viewModel.playSource(sources.first())

        assertEquals("source-a", holder.playback.value?.sources?.first()?.id)
    }

    @Test
    fun `automatic playback excludes configured video and audio sources but manual selection can show all`() = runTest {
        val item = CatalogItem(
            id = "42",
            imdbId = "tt42",
            type = MediaType.MOVIE,
            title = "Movie",
            overview = "",
            posterUrl = null,
            backdropUrl = null,
            year = 2020,
            rating = null,
        )
        val episode = Episode(1, 1, "", "", null, null)
        val allowed = source(episode).copy(id = "allowed", infoHash = "allowed")
        val dolbyVision = source(episode).copy(id = "dolby", infoHash = "dolby", hdrType = HdrType.DOLBY_VISION)
        val uhd = source(episode).copy(id = "uhd", infoHash = "uhd", quality = Quality.UHD_2160)
        val trueHd = source(episode).copy(
            id = "truehd",
            infoHash = "truehd",
            audioCodecs = setOf(AudioCodec.DOLBY_TRUEHD),
        )
        val catalog = mockk<CatalogRepository>()
        coEvery { catalog.getDetails(item) } returns CatalogDetails(item = item)
        every { catalog.getStreams(item, null) } returns flowOf(
            StreamResults(sources = listOf(allowed, dolbyVision, uhd, trueHd)),
        )
        val holder = SelectedContentHolder().apply { selectItem(item) }
        val viewModel = viewModel(
            catalog,
            holder,
            exclusions = SourceExclusions(
                qualities = setOf(Quality.UHD_2160),
                hdrTypes = setOf(HdrType.DOLBY_VISION),
                audioCodecs = setOf(AudioCodec.DOLBY_TRUEHD),
            ),
        )
        testScheduler.advanceUntilIdle()

        viewModel.play()
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("allowed"), holder.playback.value?.sources?.map { it.id })
        val ready = (viewModel.uiState.value as DetailsUiState.Content).sources as SourcesState.Ready
        assertEquals(4, ready.sources.size)

        viewModel.playSource(dolbyVision)

        assertEquals("dolby", holder.playback.value?.sources?.first()?.id)
    }

    @Test
    fun `Ithil links stay queued for failover without allowing other uncached resolvers`() = runTest {
        val item = CatalogItem(
            id = "424242",
            imdbId = null,
            type = MediaType.MOVIE,
            title = "Movie",
            overview = "",
            posterUrl = null,
            backdropUrl = null,
            year = 2001,
            rating = null,
        )
        val sources = listOf("ithil-1", "other", "ithil-2").map { id ->
            source(Episode(1, 1, "", "", null, null)).copy(
                id = id,
                infoHash = id,
                directUrl = null,
                resolveUrl = "https://video.test/$id",
                isCached = false,
                debrid = if (id.startsWith("ithil")) ITHIL_ALLDEBRID else "TorBox",
            )
        }
        val catalog = mockk<CatalogRepository>()
        coEvery { catalog.getDetails(item) } returns CatalogDetails(item = item)
        every { catalog.getStreams(item, null) } returns flowOf(StreamResults(sources = sources))
        val holder = SelectedContentHolder().apply { selectItem(item) }
        val viewModel = viewModel(catalog, holder)
        testScheduler.advanceUntilIdle()

        viewModel.play()
        testScheduler.advanceUntilIdle()

        val queuedIds = holder.playback.value?.sources?.map { it.id }.orEmpty()
        assertEquals(2, queuedIds.size)
        assertEquals(setOf("ithil-1", "ithil-2"), queuedIds.toSet())
    }

    private fun viewModel(
        catalog: CatalogRepository,
        holder: SelectedContentHolder,
        watchProgress: WatchProgressRepository? = null,
        exclusions: SourceExclusions = SourceExclusions(),
    ): DetailsViewModel =
        DetailsViewModel(
            catalogRepository = catalog,
            realDebridRepository = mockk<RealDebridRepository>(relaxed = true),
            prioritizeSourcesUseCase = PrioritizeSourcesUseCase(),
            deviceCapabilities = mockk<DeviceCapabilityDetector> {
                every { capabilities } returns DeviceCapabilities(
                    model = "test",
                    heapLimitBytes = 512L * 1024 * 1024,
                    hardwareVideoCodecs = emptySet(),
                    supportedHdr = emptySet(),
                )
            },
            holder = holder,
            watchProgress = watchProgress ?: mockk<WatchProgressRepository>(relaxed = true) {
                every { progress } returns MutableStateFlow(emptyMap())
            },
            settings = mockk<SettingsRepository>(relaxed = true) {
                every { spanishFirst } returns flowOf(false)
                every { spanishOnlySources } returns flowOf(false)
                every { sourceExclusions } returns flowOf(exclusions)
                every { deviceOptimization } returns flowOf(DeviceOptimization.AUTOMATIC)
            },
            trakt = mockk<TraktRepository>(relaxed = true),
            myList = mockk<MyListRepository>(relaxed = true) {
                every { entries } returns MutableStateFlow(emptyList())
                every { contains(any(), any()) } returns false
            },
            tracking = mockk<EpisodeTrackingRepository>(relaxed = true) {
                every { followedShowIds } returns MutableStateFlow(emptySet())
            },
            texts = mockk<Texts>(relaxed = true),
            sourceProcessingDispatcher = SourceProcessingDispatcher(mainDispatcher.dispatcher),
        )

    private fun source(episode: Episode) = StreamSource(
        id = "${episode.season}:${episode.number}",
        title = "Source",
        infoHash = "hash-${episode.season}-${episode.number}",
        fileIdx = null,
        magnet = "",
        quality = Quality.FHD_1080,
        hdrType = HdrType.SDR,
        sizeBytes = 1_000,
        seeders = 1,
        isCached = true,
        directUrl = "https://example.test/${episode.season}/${episode.number}",
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    val dispatcher = UnconfinedTestDispatcher()

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
