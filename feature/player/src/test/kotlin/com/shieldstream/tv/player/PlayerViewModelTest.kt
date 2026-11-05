package com.shieldstream.tv.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.domain.ResolveState
import com.shieldstream.tv.domain.ResolveStreamUseCase
import com.shieldstream.tv.domain.SelectedContentHolder
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.domain.SkipSegmentsRepository
import com.shieldstream.tv.domain.SubtitleRepository
import com.shieldstream.tv.domain.TraktRepository
import com.shieldstream.tv.domain.WatchProgressRepository
import com.shieldstream.tv.media.Media3Factory
import com.shieldstream.tv.media.PlaybackPolicyProvider
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.DeviceOptimization
import com.shieldstream.tv.model.HdrType
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.PlaybackMedia
import com.shieldstream.tv.model.PlaybackPolicy
import com.shieldstream.tv.model.Profile
import com.shieldstream.tv.model.Quality
import com.shieldstream.tv.model.StreamSource
import com.shieldstream.tv.model.SubtitlePreference
import com.shieldstream.tv.strings.Texts
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.rules.TestWatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.model.Statement

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `player creation does not finish after lifecycle stops`() = runBlocking {
        val allowSettingsRead = CompletableDeferred<Unit>()
        val players = FakePlayers(Tracks.EMPTY)
        val viewModel = fixture(
            players = players,
            bufferSecondsFlow = flow {
                allowSettingsRead.await()
                emit(40)
            },
            subtitlePreference = SubtitlePreference.OFF,
        ).viewModel
        viewModel.start()
        assertNull(viewModel.player.value)

        viewModel.onStop()
        allowSettingsRead.complete(Unit)
        yield()

        assertNull(viewModel.player.value)
        viewModel.onStop()
    }

    @Test
    fun `embedded subtitle preference is reapplied after player recreation`() = runBlocking {
        val players = FakePlayers(spanishTextTracks())
        val viewModel = fixture(
            players = players,
            bufferSecondsFlow = flowOf(40),
            subtitlePreference = SubtitlePreference.SPANISH,
        ).viewModel
        viewModel.start()
        yield()
        players.listener(0).onPlaybackStateChanged(Player.STATE_READY)
        assertTrue(players.parameters[0].overrides.isNotEmpty())

        viewModel.onStop()
        viewModel.onStart()
        yield()
        players.listener(1).onPlaybackStateChanged(Player.STATE_READY)

        assertTrue(players.parameters[1].overrides.isNotEmpty())
        viewModel.onStop()
    }

    private fun fixture(
        players: FakePlayers,
        bufferSecondsFlow: Flow<Int>,
        subtitlePreference: SubtitlePreference,
    ): Fixture {
        val policy = PlaybackPolicy.automatic(512L * 1024 * 1024)
        val media3Factory = mockk<Media3Factory>()
        every { media3Factory.createPlayer(any()) } answers { players.create() }
        every { media3Factory.dataSourceFactory } returns mockk()
        val playbackPolicies = mockk<PlaybackPolicyProvider> {
            every { automatic() } returns policy
            every { resolve(any(), any(), any()) } returns policy
        }
        val resolve = mockk<ResolveStreamUseCase>()
        every { resolve.invoke(any<StreamSource>()) } returns flowOf<ResolveState>(
            ResolveState.Ready("https://example.test/video"),
        )
        val settings = mockk<SettingsRepository>(relaxed = true) {
            every { deviceOptimization } returns flowOf(DeviceOptimization.CUSTOM)
            every { this@mockk.maxBufferSeconds } returns bufferSecondsFlow
            every { seekThumbnails } returns flowOf(false)
            every { autoNextEpisode } returns flowOf(true)
        }
        val profile = Profile(
            id = "test",
            name = "Test",
            avatar = 0,
            spanishFirst = false,
            spanishOnlySources = false,
            subtitlePreference = subtitlePreference,
        )
        val profiles = mockk<ProfileRepository> {
            every { active } returns MutableStateFlow(profile)
        }
        val item = CatalogItem(
            id = "1",
            imdbId = "tt1",
            type = MediaType.MOVIE,
            title = "Movie",
            overview = "",
            posterUrl = null,
            backdropUrl = null,
            year = 2020,
            rating = null,
        )
        val holder = SelectedContentHolder().apply {
            selectItem(item)
            playQueue(
                sources = listOf(source()),
                label = "Movie",
                progressKey = "movie:1",
                media = PlaybackMedia(MediaType.MOVIE, "tt1", "Movie", "Movie", 2020),
            )
        }
        return Fixture(
            PlayerViewModel(
                media3Factory = media3Factory,
                playbackPolicies = playbackPolicies,
                resolveStreamUseCase = resolve,
                settings = settings,
                profiles = profiles,
                holder = holder,
                watchProgress = mockk<WatchProgressRepository>(relaxed = true),
                subtitles = mockk<SubtitleRepository>(relaxed = true),
                trakt = mockk<TraktRepository>(relaxed = true),
                skipSegmentsRepository = mockk<SkipSegmentsRepository>(relaxed = true),
                texts = mockk<Texts>(relaxed = true),
            ),
        )
    }

    private fun source() = StreamSource(
        id = "source",
        title = "Source",
        infoHash = "hash",
        fileIdx = null,
        magnet = "",
        quality = Quality.FHD_1080,
        hdrType = HdrType.SDR,
        sizeBytes = 1_000,
        seeders = 1,
        isCached = true,
        directUrl = "https://example.test/video",
    )

    private fun spanishTextTracks(): Tracks {
        val format = Format.Builder()
            .setId("spanish-subtitles")
            .setLanguage("es")
            .setSampleMimeType(MimeTypes.APPLICATION_SUBRIP)
            .build()
        val group = Tracks.Group(
            TrackGroup("subtitles", format),
            false,
            intArrayOf(C.FORMAT_HANDLED),
            booleanArrayOf(false),
        )
        return Tracks(listOf(group))
    }

    private data class Fixture(val viewModel: PlayerViewModel)

    private class FakePlayers(private val tracks: Tracks) {
        val parameters = mutableListOf<TrackSelectionParameters>()
        private val listeners = mutableListOf<Player.Listener?>()

        fun create(): ExoPlayer {
            val player = mockk<ExoPlayer>(relaxed = true)
            var selection = TrackSelectionParameters.DEFAULT
            parameters += selection
            listeners += null
            val index = parameters.lastIndex
            val listener = slot<Player.Listener>()
            every { player.trackSelectionParameters } answers { selection }
            every { player.trackSelectionParameters = any() } answers {
                selection = firstArg()
                parameters[index] = selection
            }
            every { player.currentTracks } returns tracks
            every { player.playbackState } returns Player.STATE_READY
            every { player.duration } returns C.TIME_UNSET
            every { player.currentPosition } returns 0L
            every { player.addListener(capture(listener)) } answers {
                listeners[index] = listener.captured
            }
            return player
        }

        fun listener(index: Int): Player.Listener {
            assertNotNull(listeners[index])
            return listeners[index]!!
        }
    }
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
