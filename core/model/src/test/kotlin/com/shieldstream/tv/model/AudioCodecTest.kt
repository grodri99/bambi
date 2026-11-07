package com.shieldstream.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioCodecTest {

    @Test
    fun `detects atmos together with its underlying codec`() {
        assertEquals(
            setOf(AudioCodec.DOLBY_ATMOS, AudioCodec.DOLBY_TRUEHD),
            AudioCodec.parse("Movie.2160p.TrueHD.7.1.Atmos"),
        )
    }

    @Test
    fun `dolby digital plus tags do not become dolby digital`() {
        listOf("EAC3", "E-AC-3", "DDP5.1", "DD+ 5.1", "Dolby Digital Plus").forEach { tag ->
            assertEquals(tag, setOf(AudioCodec.DOLBY_DIGITAL_PLUS), AudioCodec.parse(tag))
        }
        listOf("AC3", "DD5.1", "Dolby Digital 5.1").forEach { tag ->
            assertEquals(tag, setOf(AudioCodec.DOLBY_DIGITAL), AudioCodec.parse(tag))
        }
    }

    @Test
    fun `specific dts variants also match the dts family exclusion`() {
        assertEquals(setOf(AudioCodec.DTS_X, AudioCodec.DTS), AudioCodec.parse("DTS:X 7.1"))
        assertEquals(setOf(AudioCodec.DTS_HD, AudioCodec.DTS), AudioCodec.parse("DTS-HD.MA.7.1"))
        assertEquals(setOf(AudioCodec.DTS_EXPRESS, AudioCodec.DTS), AudioCodec.parse("DTS Express"))
        assertEquals(setOf(AudioCodec.DTS), AudioCodec.parse("DTS5.1"))
    }

    @Test
    fun `detects other advertised codecs and ignores unknown metadata`() {
        assertEquals(
            setOf(AudioCodec.AAC, AudioCodec.OPUS, AudioCodec.FLAC, AudioCodec.MP3, AudioCodec.PCM),
            AudioCodec.parse("AAC Opus FLAC MP3 LPCM"),
        )
        assertTrue(AudioCodec.parse("Movie 1080p HEVC").isEmpty())
    }
}
