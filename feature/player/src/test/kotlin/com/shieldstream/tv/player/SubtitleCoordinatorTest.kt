package com.shieldstream.tv.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import com.shieldstream.tv.domain.SubtitleRepository
import com.shieldstream.tv.model.ExternalSubtitle
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.PlaybackMedia
import com.shieldstream.tv.model.SubtitleFile
import com.shieldstream.tv.model.SubtitlePreference
import com.shieldstream.tv.model.SubtitleQuery
import com.shieldstream.tv.strings.Texts
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale

private fun fakeReferenceFailure(
    reason: ReferenceFailureReason = ReferenceFailureReason.NO_TEXT_TRACK,
    retryable: Boolean = false,
) = ReferenceReadFailure(reason, retryable, elapsedMs = 1, bytesRead = 100, seeks = 0, track = null)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SubtitleCoordinatorTest {

    @Test
    fun `automatic Spanish preference downloads and attaches external subtitles without embedded tracks`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val subtitleFile = File.createTempFile("spanish-subtitle", ".srt").apply {
            writeText("1\n00:00:01,000 --> 00:00:03,000\nHola\n")
        }
        val repository = FakeSubtitleRepository(subtitleFile)
        assertTrue(parse(subtitleFile).isNotEmpty())
        val player = mockk<Player>(relaxed = true) {
            every { currentTracks } returns Tracks.EMPTY
            every { currentPosition } returns 0L
        }
        var parameters = TrackSelectionParameters.DEFAULT
        every { player.trackSelectionParameters } answers { parameters }
        every { player.trackSelectionParameters = any() } answers { parameters = firstArg() }
        val media = PlaybackMedia(MediaType.MOVIE, "tt1", "Movie", "Movie", 2026)
        val coordinator = SubtitleCoordinator(
            scope = scope,
            subtitles = repository,
            subtitlePreference = SubtitlePreference.SPANISH,
            texts = mockk<Texts>(relaxed = true),
            media = { media },
            playingFileName = { "Movie.2026.mkv" },
            sourceUrl = { "https://video.test/movie.mkv" },
            player = { player },
            notify = {},
            referenceReader = FakeReferenceReader(null),
        )

        try {
            coordinator.onTracksChanged(player, Tracks.EMPTY)
            assertEquals("es", repository.lastQuery?.language)
            try {
                withTimeout(5_000) {
                    while (coordinator.activeExternal.value == null) delay(25)
                }
            } catch (e: TimeoutCancellationException) {
                throw AssertionError(
                    "downloads=${repository.downloads} external=${coordinator.externalSubs.value} " +
                        "sync=${coordinator.syncStatus.value}",
                    e,
                )
            }

            assertEquals(1, repository.downloads)
            assertEquals("es", repository.queries.first().language)
            assertEquals("Spanish", coordinator.activeExternal.value?.label)
            assertTrue(coordinator.externalSubs.value is ExternalSubsState.Attached)
            withTimeout(5_000) { while (coordinator.syncStatus.value !is SyncStatus.ReferenceError) delay(25) }
            val failureStatus = coordinator.syncStatus.value as SyncStatus.ReferenceError
            assertEquals(ReferenceFailureReason.NO_TEXT_TRACK, failureStatus.failure.reason)
            assertFalse(failureStatus.failure.retryable)

            coordinator.onNewSource()
            assertNull(coordinator.activeExternal.value)
            assertEquals(SubtitleTiming(), coordinator.subtitleTiming.value)
            assertEquals(SyncStatus.Off, coordinator.syncStatus.value)
            assertEquals(ExternalSubsState.Idle, coordinator.externalSubs.value)
        } finally {
            scope.cancel()
            subtitleFile.delete()
        }
    }

    @Test
    fun `downloaded English reference synchronizes without waiting for playback cues`() = runBlocking {
        val timings = listOf(
            0L to 600L, 2_000L to 2_500L, 4_500L to 5_200L, 8_000L to 8_700L,
            10_000L to 10_500L, 14_000L to 14_800L, 16_000L to 16_500L, 20_000L to 20_700L,
            23_000L to 23_500L, 25_000L to 25_800L, 28_000L to 28_500L, 31_000L to 31_700L,
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val subtitleFile = File.createTempFile("spanish-reference", ".srt").apply {
            writeText(
                timings.mapIndexed { index, (start, end) ->
                    "${index + 1}\n${srtTime(start)} --> ${srtTime(end)}\nText $index\n"
                }.joinToString("\n"),
            )
        }
        val repository = FakeSubtitleRepository(subtitleFile)
        val tracks = englishTextTracks()
        var position = 0L
        val player = mockk<Player>(relaxed = true) {
            every { currentTracks } returns tracks
            every { currentPosition } answers { position }
        }
        var parameters = TrackSelectionParameters.DEFAULT
        every { player.trackSelectionParameters } answers { parameters }
        every { player.trackSelectionParameters = any() } answers { parameters = firstArg() }
        val coordinator = SubtitleCoordinator(
            scope = scope,
            subtitles = repository,
            subtitlePreference = SubtitlePreference.SPANISH,
            texts = mockk<Texts>(relaxed = true),
            media = { PlaybackMedia(MediaType.MOVIE, "tt1", "Movie", "Movie", 2026) },
            playingFileName = { "Movie.2026.mkv" },
            sourceUrl = { "https://video.test/movie.mkv" },
            player = { player },
            notify = {},
            referenceReader = FakeReferenceReader(SubtitleSync.intervalsOf(parse(subtitleFile))),
        )

        try {
            coordinator.onTracksChanged(player, tracks)
            withTimeout(8_000) { while (coordinator.syncStatus.value !is SyncStatus.Auto) delay(25) }

            assertTrue("Expected automatic synchronization, was ${coordinator.syncStatus.value}", coordinator.syncStatus.value is SyncStatus.Auto)
            assertEquals("Spanish", coordinator.activeExternal.value?.label)
        } finally {
            scope.cancel()
            subtitleFile.delete()
        }
    }

    @Test
    fun `manual subtitle selection also uses a downloaded English reference`() = runBlocking {
        val timings = listOf(
            0L to 600L, 2_000L to 2_500L, 4_500L to 5_200L, 8_000L to 8_700L,
            10_000L to 10_500L, 14_000L to 14_800L, 16_000L to 16_500L, 20_000L to 20_700L,
            23_000L to 23_500L, 25_000L to 25_800L, 28_000L to 28_500L, 31_000L to 31_700L,
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val subtitleFile = File.createTempFile("manual-spanish-reference", ".srt").apply {
            writeText(
                timings.mapIndexed { index, (start, end) ->
                    "${index + 1}\n${srtTime(start)} --> ${srtTime(end)}\nText $index\n"
                }.joinToString("\n"),
            )
        }
        val repository = FakeSubtitleRepository(subtitleFile)
        val tracks = englishTextTracks()
        val player = mockk<Player>(relaxed = true) {
            every { currentTracks } returns tracks
            every { currentPosition } returns 0L
        }
        var parameters = TrackSelectionParameters.DEFAULT
        every { player.trackSelectionParameters } answers { parameters }
        every { player.trackSelectionParameters = any() } answers { parameters = firstArg() }
        val coordinator = SubtitleCoordinator(
            scope = scope,
            subtitles = repository,
            subtitlePreference = SubtitlePreference.SPANISH,
            texts = mockk<Texts>(relaxed = true),
            media = { PlaybackMedia(MediaType.MOVIE, "tt1", "Movie", "Movie", 2026) },
            playingFileName = { "Movie.2026.mkv" },
            sourceUrl = { "https://video.test/movie.mkv" },
            player = { player },
            notify = {},
            referenceReader = FakeReferenceReader(SubtitleSync.intervalsOf(parse(subtitleFile))),
        )

        try {
            coordinator.download(repository.result)
            withTimeout(8_000) { while (coordinator.syncStatus.value !is SyncStatus.Auto) delay(25) }

            assertTrue("Expected manual selection to synchronize, was ${coordinator.syncStatus.value}", coordinator.syncStatus.value is SyncStatus.Auto)
            assertEquals("Spanish", coordinator.activeExternal.value?.label)
        } finally {
            scope.cancel()
            subtitleFile.delete()
        }
    }

    @Test
    fun `retry samples current position and failed replacement preserves active timing`() = runBlocking {
        val timings = listOf(
            0L to 600L, 2_000L to 2_500L, 4_500L to 5_200L, 8_000L to 8_700L,
            10_000L to 10_500L, 14_000L to 14_800L, 16_000L to 16_500L, 20_000L to 20_700L,
            23_000L to 23_500L, 25_000L to 25_800L, 28_000L to 28_500L, 31_000L to 31_700L,
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val subtitleFile = File.createTempFile("transactional-subtitle", ".srt").apply {
            writeText(
                timings.mapIndexed { index, (start, end) ->
                    "${index + 1}\n${srtTime(start)} --> ${srtTime(end)}\nText $index\n"
                }.joinToString("\n"),
            )
        }
        val repository = FakeSubtitleRepository(subtitleFile)
        val reader = FakeReferenceReader(SubtitleSync.intervalsOf(parse(subtitleFile)))
        var position = 0L
        val player = mockk<Player>(relaxed = true) {
            every { currentPosition } answers { position }
        }
        val coordinator = SubtitleCoordinator(
            scope = scope,
            subtitles = repository,
            subtitlePreference = SubtitlePreference.SPANISH,
            texts = mockk<Texts>(relaxed = true),
            media = { PlaybackMedia(MediaType.MOVIE, "tt1", "Movie", "Movie", 2026) },
            playingFileName = { "Movie.2026.mkv" },
            sourceUrl = { "https://video.test/movie.mkv" },
            player = { player },
            notify = {},
            referenceReader = reader,
        )

        try {
            coordinator.download(repository.result)
            withTimeout(8_000) { while (coordinator.syncStatus.value !is SyncStatus.Auto) delay(25) }
            position = 1_800_000L
            coordinator.autoSyncNow()
            withTimeout(5_000) { while (reader.positions.size < 2) delay(25) }
            withTimeout(8_000) { while (coordinator.syncStatus.value !is SyncStatus.Auto) delay(25) }
            assertEquals(listOf(0L, 1_800_000L), reader.positions)

            val previous = coordinator.activeExternal.value
            val previousTiming = coordinator.subtitleTiming.value
            val previousStatus = coordinator.syncStatus.value
            reader.failNext = true
            coordinator.download(repository.result)
            withTimeout(5_000) { while (reader.positions.size < 3) delay(25) }
            delay(100)

            assertSame(previous, coordinator.activeExternal.value)
            assertEquals(previousTiming, coordinator.subtitleTiming.value)
            assertEquals(previousStatus, coordinator.syncStatus.value)
            assertEquals(ExternalSubsState.Attached(previous!!.label), coordinator.externalSubs.value)
        } finally {
            scope.cancel()
            subtitleFile.delete()
        }
    }

    @Test
    fun `late demux result cannot mutate a newer source`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val subtitleFile = File.createTempFile("late-reference", ".srt").apply {
            writeText("1\n00:00:01,000 --> 00:00:03,000\nHola\n")
        }
        val repository = FakeSubtitleRepository(subtitleFile)
        val player = mockk<Player>(relaxed = true) {
            every { currentTracks } returns Tracks.EMPTY
            every { currentPosition } returns 0L
        }
        val demuxStarted = CompletableDeferred<Unit>()
        val demuxResult = CompletableDeferred<ReferenceReadResult>()
        val reader = object : EmbeddedReferenceReader {
            override suspend fun read(sourceUrl: String, positionMs: Long): ReferenceReadResult {
                demuxStarted.complete(Unit)
                return demuxResult.await()
            }
        }
        var notifications = 0
        val coordinator = SubtitleCoordinator(
            scope = scope,
            subtitles = repository,
            subtitlePreference = SubtitlePreference.SPANISH,
            texts = mockk<Texts>(relaxed = true),
            media = { PlaybackMedia(MediaType.MOVIE, "tt1", "Movie", "Movie", 2026) },
            playingFileName = { "Movie.2026.mkv" },
            sourceUrl = { "https://video.test/movie.mkv" },
            player = { player },
            notify = { notifications++ },
            referenceReader = reader,
        )

        try {
            coordinator.download(repository.result)
            withTimeout(5_000) { demuxStarted.await() }
            coordinator.onNewSource()
            coordinator.onPlaybackStopped()
            coordinator.disable()
            notifications = 0
            demuxResult.complete(ReferenceReadResult.Failure(fakeReferenceFailure()))
            delay(100)

            assertNull(coordinator.activeExternal.value)
            assertEquals(SyncStatus.Off, coordinator.syncStatus.value)
            assertEquals(ExternalSubsState.Idle, coordinator.externalSubs.value)
            assertEquals(0, notifications)
        } finally {
            scope.cancel()
            subtitleFile.delete()
        }
    }

    @Test
    fun `disabled experimental sync attaches best subtitle without demuxing`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val subtitleFile = File.createTempFile("sync-disabled", ".srt").apply {
            writeText("1\n00:00:01,000 --> 00:00:03,000\nHola\n")
        }
        val repository = FakeSubtitleRepository(subtitleFile)
        val player = mockk<Player>(relaxed = true) {
            every { currentTracks } returns Tracks.EMPTY
            every { currentPosition } returns 0L
        }
        var readerCalls = 0
        val reader = object : EmbeddedReferenceReader {
            override suspend fun read(sourceUrl: String, positionMs: Long): ReferenceReadResult {
                readerCalls++
                return ReferenceReadResult.Failure(fakeReferenceFailure())
            }
        }
        val coordinator = SubtitleCoordinator(
            scope = scope,
            subtitles = repository,
            subtitlePreference = SubtitlePreference.SPANISH,
            texts = mockk<Texts>(relaxed = true),
            media = { PlaybackMedia(MediaType.MOVIE, "tt1", "Movie", "Movie", 2026) },
            playingFileName = { "Movie.2026.mkv" },
            sourceUrl = { "https://video.test/movie.mkv" },
            player = { player },
            notify = {},
            referenceReader = reader,
            experimentalSyncEnabled = false,
        )

        try {
            coordinator.download(repository.result)
            withTimeout(5_000) { while (coordinator.activeExternal.value == null) delay(25) }

            assertEquals("Spanish", coordinator.activeExternal.value?.label)
            assertEquals(SubtitleTiming(), coordinator.subtitleTiming.value)
            assertEquals(SyncStatus.Off, coordinator.syncStatus.value)
            assertEquals(0, readerCalls)
            coordinator.autoSyncNow()
            assertEquals(0, readerCalls)
        } finally {
            scope.cancel()
            subtitleFile.delete()
        }
    }

    private fun englishTextTracks(): Tracks {
        val format = Format.Builder()
            .setId("english-reference")
            .setLanguage("en")
            .setSampleMimeType(MimeTypes.APPLICATION_SUBRIP)
            .build()
        val group = Tracks.Group(
            TrackGroup("subtitles", format),
            false,
            intArrayOf(C.FORMAT_HANDLED),
            booleanArrayOf(true),
        )
        return Tracks(listOf(group))
    }

    private fun srtTime(ms: Long): String = String.format(
        Locale.US,
        "%02d:%02d:%02d,%03d",
        ms / 3_600_000,
        (ms / 60_000) % 60,
        (ms / 1_000) % 60,
        ms % 1_000,
    )

    private fun parse(file: File): List<CuesWithTiming> {
        val format = Format.Builder().setSampleMimeType("application/x-subrip").build()
        val parser = DefaultSubtitleParserFactory().create(format)
        val output = mutableListOf<CuesWithTiming>()
        parser.parse(file.readBytes(), SubtitleParser.OutputOptions.allCues()) { output += it }
        parser.reset()
        return output
    }

    private class FakeReferenceReader(private val intervals: List<SubInterval>?) : EmbeddedReferenceReader {
        val positions = mutableListOf<Long>()
        var failNext = false

        override suspend fun read(sourceUrl: String, positionMs: Long): ReferenceReadResult {
            positions += positionMs
            if (failNext) {
                failNext = false
                return ReferenceReadResult.Failure(fakeReferenceFailure(ReferenceFailureReason.NETWORK, retryable = true))
            }
            if (intervals == null) return ReferenceReadResult.Failure(fakeReferenceFailure())
            return ReferenceReadResult.Success(
                EmbeddedReference(
                    sourceUrl = sourceUrl,
                    track = "fake:en:application/x-subrip",
                    sampledWindow = SubInterval(intervals.first().startMs, intervals.last().endMs),
                    intervals = intervals,
                    elapsedMs = 1,
                    bytesRead = 100,
                    seeks = 0,
                ),
            )
        }
    }

    private class FakeSubtitleRepository(
        private val file: File,
    ) : SubtitleRepository {
        var downloads = 0
        val queries = mutableListOf<SubtitleQuery>()
        var lastQuery: SubtitleQuery? = null
        val result = ExternalSubtitle(
            provider = "test",
            id = "spanish",
            release = "Movie.2026",
            language = "es",
            hearingImpaired = false,
            downloadUrl = "https://example.test/spanish.srt",
            matchScore = 1f,
        )

        override suspend fun search(query: SubtitleQuery): List<ExternalSubtitle> {
            queries += query
            lastQuery = query
            return listOf(result)
        }

        override suspend fun download(subtitle: ExternalSubtitle, query: SubtitleQuery): SubtitleFile {
            downloads++
            return SubtitleFile(file.path, "application/x-subrip", "es", "Spanish")
        }
    }
}
