package com.shieldstream.tv.player

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedSubtitleReaderTest {
    @Test
    fun `accepts embedded text tracks in any language`() {
        assertTrue(isReferenceSubtitle(format("application/x-media3-cues", "en")))
        assertTrue(isReferenceSubtitle(format("application/x-media3-cues", "es")))
        assertTrue(isReferenceSubtitle(format(MimeTypes.APPLICATION_SUBRIP, "en-US")))
        assertTrue(isReferenceSubtitle(format(MimeTypes.APPLICATION_TX3G, "en")))
        assertTrue(isReferenceSubtitle(format(MimeTypes.APPLICATION_TTML, "en")))
        assertTrue(isReferenceSubtitle(format(MimeTypes.APPLICATION_MP4VTT, "en")))
    }

    @Test
    fun `inferred cue durations preserve gaps in the reference signal`() {
        assertEquals(
            listOf(SubInterval(0, 2_500), SubInterval(10_000, 12_500), SubInterval(20_000, 24_000)),
            referenceIntervalsFromStarts(listOf(20_000, 0, 10_000)),
        )
    }

    @Test
    fun `prefers non-forced English reference and falls back to other languages`() {
        val english = format(MimeTypes.APPLICATION_SUBRIP, "en")
        val russian = format(MimeTypes.APPLICATION_SUBRIP, "ru")
        val forcedEnglish = Format.Builder()
            .setSampleMimeType(MimeTypes.APPLICATION_SUBRIP)
            .setLanguage("en")
            .setSelectionFlags(2)
            .build()
        assertTrue(referenceTrackScore(english)!! > referenceTrackScore(russian)!!)
        assertTrue(referenceTrackScore(russian)!! > referenceTrackScore(forcedEnglish)!!)
    }

    @Test
    fun `rejects non-subtitle tracks`() {
        assertFalse(isReferenceSubtitle(format(MimeTypes.VIDEO_H265, "en")))
        assertFalse(isReferenceSubtitle(format(MimeTypes.AUDIO_AAC, "en")))
    }

    private fun format(mime: String, language: String) = Format.Builder()
        .setSampleMimeType(mime)
        .setLanguage(language)
        .build()
}
