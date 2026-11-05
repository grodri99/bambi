package com.shieldstream.tv.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import com.shieldstream.tv.domain.ResolveState
import com.shieldstream.tv.domain.ResolveStreamUseCase
import com.shieldstream.tv.model.HdrType
import com.shieldstream.tv.model.Quality
import com.shieldstream.tv.model.StreamSource
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceFailoverControllerTest {

    @Test
    fun `non-blocked failures stop after the attempt limit`() = runTest {
        val fixture = Fixture(List(9) { source(id = "$it") })
        fixture.behavior = { source -> flowOf(ResolveState.Failed(source.id)) }

        val events = fixture.controller.resolve(0).toList()

        assertEquals((0 until 8).map { "$it" }, fixture.requested)
        assertEquals("none worked\n7", events.filterIsInstance<SourceResolutionEvent.Error>().single().message)
    }

    @Test
    fun `blocked failures do not consume an attempt`() = runTest {
        val fixture = Fixture(listOf(source(id = "blocked"), source(id = "working")))
        fixture.behavior = { source ->
            if (source.id == "blocked") {
                flowOf(ResolveState.Failed("blocked", blocked = true))
            } else {
                flowOf(ResolveState.Progress("loading"), ResolveState.Ready("url"))
            }
        }

        val events = fixture.controller.resolve(0).toList()

        assertEquals(listOf("blocked", "working"), fixture.requested)
        val ready = events.filterIsInstance<SourceResolutionEvent.Ready>().single()
        assertEquals(1, ready.index)
        assertEquals("url", ready.url)
        assertEquals(1, fixture.controller.queueIndex)
        assertEquals("source 2/2 loading", events.filterIsInstance<SourceResolutionEvent.Resolving>().single().message)
    }

    @Test
    fun `only not-cached failures produce the service-specific terminal error`() = runTest {
        val fixture = Fixture(
            listOf(
                source(id = "first", debrid = "TorBox"),
                source(id = "second", debrid = "TorBox"),
            ),
        )
        fixture.behavior = { source -> flowOf(ResolveState.Failed(source.id, notCached = true)) }

        val events = fixture.controller.resolve(0).toList()

        assertEquals("none cached TorBox.\nsecond", events.filterIsInstance<SourceResolutionEvent.Error>().single().message)
    }

    @Test
    fun `failed Spanish candidate returns to the original working source`() = runTest {
        val fixture = Fixture(
            listOf(source(id = "original", title = "Spanish original"), source(id = "candidate", title = "Spanish")),
            spanishFirst = true,
        )
        fixture.behavior = { source ->
            if (source.id == "candidate") flowOf(ResolveState.Failed("unavailable"))
            else flowOf(ResolveState.Ready("original-url"))
        }
        fixture.controller.resolve(0).toList()

        val action = fixture.controller.checkSpanishAudio(Player.STATE_READY, audioTracks("en"))
        assertEquals(SourceFailoverAction.SwitchSource(1, "switching Spanish"), action)
        val events = fixture.controller.resolve(1).toList()

        val fallback = events.filterIsInstance<SourceResolutionEvent.Fallback>().single()
        assertEquals(0, fallback.index)
        assertEquals("fallback", fallback.notice)
    }

    @Test
    fun `Spanish-only playback stops when no source can be verified`() {
        val fixture = Fixture(listOf(source(title = "English")), spanishOnly = true, spanishFirst = true)

        val action = fixture.controller.checkSpanishAudio(Player.STATE_READY, audioTracks("en"))

        assertEquals(SourceFailoverAction.FatalError("no verified Spanish"), action)
        fixture.controller.onPlayerCreated()
        assertEquals(SourceFailoverAction.AudioAccepted, fixture.controller.checkSpanishAudio(Player.STATE_READY, audioTracks("es-MX")))
    }

    @Test
    fun `short addon error video switches once to the next source`() = runTest {
        val fixture = Fixture(
            listOf(
                source(id = "slate", resolveUrl = "https://resolve.test", sizeBytes = 150_000_000),
                source(id = "next", resolveUrl = "https://resolve.test", sizeBytes = 150_000_000),
            ),
        )
        fixture.controller.resolve(0).toList()

        val action = fixture.controller.checkErrorSlate(60_000)

        assertEquals(SourceFailoverAction.SwitchSource(1, "missing episode"), action)
        assertNull(fixture.controller.checkErrorSlate(60_000))
    }

    @Test
    fun `open errors fail over only before playback has started`() = runTest {
        val fixture = Fixture(listOf(source(id = "first"), source(id = "second")))
        fixture.controller.resolve(0).toList()

        assertEquals(SourceFailoverAction.SwitchSource(1), fixture.controller.onSourceOpenError("IO_ERROR"))
        fixture.controller.everPlayed = true
        assertNull(fixture.controller.onSourceOpenError("IO_ERROR"))
    }

    private class Fixture(
        queue: List<StreamSource>,
        spanishOnly: Boolean = false,
        spanishFirst: Boolean = false,
    ) {
        val requested = mutableListOf<String>()
        var behavior: (StreamSource) -> Flow<ResolveState> = { flowOf(ResolveState.Ready("url:${it.id}")) }
        private val resolver = mockk<ResolveStreamUseCase>()
        val controller = SourceFailoverController(queue, resolver, TestTexts, spanishOnly, spanishFirst)

        init {
            every { resolver.invoke(any()) } answers {
                val source = firstArg<StreamSource>()
                requested += source.id
                behavior(source)
            }
        }
    }

    private object TestTexts : Texts {
        override fun get(id: Int, vararg formatArgs: Any): String = when (id) {
            StringsR.string.player_queue_source -> "source ${formatArgs[0]}/${formatArgs[1]} "
            StringsR.string.player_none_of_the_sources_tried_worked -> "none worked"
            StringsR.string.player_none_of_the_sources_tried_is_cached -> "none cached "
            StringsR.string.player_no_other_working_spanish_source_back_to -> "fallback"
            StringsR.string.player_no_spanish_audio_trying_another_source -> "switching Spanish"
            StringsR.string.player_no_sources_with_verified_spanish_audio_available -> "no verified Spanish"
            StringsR.string.player_this_source_has_no_spanish_audio -> "not Spanish"
            StringsR.string.player_that_source_doesn_t_have_the_episode -> "missing episode"
            StringsR.string.player_source_open_error -> "open ${formatArgs[0]}"
            else -> "text $id"
        }

        override fun quantity(id: Int, count: Int, vararg formatArgs: Any): String = "text $id"
    }

    private fun source(
        id: String = "source",
        title: String = "Source",
        debrid: String? = null,
        resolveUrl: String? = null,
        sizeBytes: Long = 1_000,
    ) = StreamSource(
        id = id,
        title = title,
        infoHash = id,
        fileIdx = null,
        magnet = "",
        quality = Quality.FHD_1080,
        hdrType = HdrType.SDR,
        sizeBytes = sizeBytes,
        seeders = 1,
        isCached = true,
        debrid = debrid,
        resolveUrl = resolveUrl,
    )

    private fun audioTracks(language: String): Tracks {
        val format = Format.Builder()
            .setId("audio-$language")
            .setLanguage(language)
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .build()
        val group = Tracks.Group(
            TrackGroup("audio", format),
            false,
            intArrayOf(C.FORMAT_HANDLED),
            booleanArrayOf(false),
        )
        return Tracks(listOf(group))
    }
}
