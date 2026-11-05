package com.shieldstream.tv.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

@OptIn(UnstableApi::class)
class SubtitleSyncTest {

    @Test
    fun `align finds timing corrections in both directions`() {
        val reference = cues(40)

        for (externalShift in listOf(5_000L, -4_000L)) {
            val external = reference.map { it.shift(externalShift) }
            val result = SubtitleSync.align(reference, listOf(SubInterval(0, 180_000)), external)
                ?: error("Expected an alignment for shift $externalShift")

            assertEquals(1.0, result.timing.scale, 0.0)
            assertEquals(-externalShift, result.timing.offsetMs)
            assertTrue("Expected a strong match for shift $externalShift: $result", result.strong)
        }
    }

    @Test
    fun `align detects frame rate scaling`() {
        val reference = cues(40)
        val externalScale = 23.976 / 25.0
        val expectedCorrection = 25.0 / 23.976
        val external = reference.map { it.scale(externalScale) }

        val result = SubtitleSync.align(reference, listOf(SubInterval(0, 180_000)), external)
            ?: error("Expected a frame-rate alignment")

        assertEquals(expectedCorrection, result.timing.scale, 0.0001)
        assertTrue("Coarse offset was ${result.timing.offsetMs}", abs(result.timing.offsetMs) <= 20)
        assertTrue("Expected a strong frame-rate match: $result", result.strong)
    }

    @Test
    fun `high correlation constant rate match accepts a small peak ratio margin`() {
        val result = SyncResult(
            timing = SubtitleTiming(-14_100, 1.0),
            correlation = 0.6376586f,
            ratio = 1.4869413f,
            referenceCues = 40,
        )
        assertTrue(result.strong)
    }

    @Test
    fun `high correlation frame rate match does not require a large peak ratio`() {
        val result = SyncResult(
            timing = SubtitleTiming(4_900, 25.0 / 23.976),
            correlation = 0.778573f,
            ratio = 1.1044441f,
            referenceCues = 40,
        )
        assertTrue(result.strong)
    }

    @Test
    fun `align rejects insufficient references and playback windows`() {
        val references = cues(12)
        val longWindow = listOf(SubInterval(0, 60_000))

        assertNull(SubtitleSync.align(references.take(9), longWindow, references))
        assertNull(SubtitleSync.align(references, listOf(SubInterval(0, 19_999)), references))
        assertNull(SubtitleSync.align(references, longWindow, emptyList()))
    }

    @Test
    fun `align stops when cancelled`() {
        val references = cues(40)
        assertNull(
            SubtitleSync.align(
                references,
                listOf(SubInterval(0, 180_000)),
                references,
                isActive = { false },
            ),
        )
    }

    @Test
    fun `plausible matches require agreement in both windows`() {
        val candidate = SyncResult(SubtitleTiming(-410), 0.30f, 2.2f, 40)
        val agreeing = SyncResult(SubtitleTiming(-450), 0.28f, 2.1f, 20)
        val disagreeing = SyncResult(SubtitleTiming(2_000), 0.28f, 2.1f, 20)

        assertTrue(confirmedByWindows(candidate, agreeing, agreeing))
        assertFalse(confirmedByWindows(candidate, agreeing, disagreeing))
        assertFalse(confirmedByWindows(candidate, agreeing, null))
    }

    @Test
    fun `strong matches do not require a second confirmation`() {
        val strong = SyncResult(SubtitleTiming(-410), 0.80f, 2.2f, 40)
        assertTrue(confirmedByWindows(strong, null, null))
    }

    @Test
    fun `segmentAt respects cue boundaries and gaps`() {
        val segments = listOf(
            segment(startMs = 1_000, durationMs = 2_000),
            segment(startMs = 5_000, durationMs = 2_000),
        )

        assertEquals(-1, SubtitleSync.segmentAt(segments, 999_000))
        assertEquals(0, SubtitleSync.segmentAt(segments, 1_000_000))
        assertEquals(0, SubtitleSync.segmentAt(segments, 2_999_999))
        assertEquals(-1, SubtitleSync.segmentAt(segments, 3_000_000))
        assertEquals(-1, SubtitleSync.segmentAt(segments, 4_999_999))
        assertEquals(1, SubtitleSync.segmentAt(segments, 5_000_000))
        assertEquals(1, SubtitleSync.segmentAt(segments, 6_999_999))
        assertEquals(-1, SubtitleSync.segmentAt(segments, 7_000_000))
    }

    @Test
    fun `intervalsOf merges adjacent cues and infers missing durations`() {
        val segments = listOf(
            segment(startMs = 0, durationMs = 1_000),
            segment(startMs = 1_000, durationMs = 1_000),
            segment(startMs = 3_000, durationMs = C.TIME_UNSET),
            segment(startMs = 4_000, durationMs = 1_000),
        )

        assertEquals(
            listOf(SubInterval(0, 2_000), SubInterval(3_000, 5_000)),
            SubtitleSync.intervalsOf(segments),
        )
    }

    private fun cues(count: Int): List<SubInterval> {
        val result = mutableListOf<SubInterval>()
        var start = 0L
        for (index in 0 until count) {
            val duration = 300L + (index * 104_729L % 800L)
            result += SubInterval(start, start + duration)
            start += 1_200L + (index * 7_919L % 2_500L)
        }
        return result
    }

    private fun SubInterval.shift(offsetMs: Long) = SubInterval(startMs + offsetMs, endMs + offsetMs)

    private fun SubInterval.scale(scale: Double) = SubInterval(
        (startMs * scale).toLong(),
        (endMs * scale).toLong(),
    )

    private fun segment(startMs: Long, durationMs: Long) = CuesWithTiming(
        listOf(Cue.Builder().setText("cue").build()),
        startMs * 1_000,
        if (durationMs == C.TIME_UNSET) C.TIME_UNSET else durationMs * 1_000,
    )
}
