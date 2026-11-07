package com.shieldstream.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceExclusionsTest {

    @Test
    fun `empty exclusions keep every source`() {
        val sources = listOf(source(Quality.UHD_2160, HdrType.DOLBY_VISION), source(Quality.SD, HdrType.SDR))

        assertEquals(sources, SourceExclusions().filter(sources))
    }

    @Test
    fun `quality and video feature exclusions are combined`() {
        val exclusions = SourceExclusions(
            qualities = setOf(Quality.UHD_2160),
            hdrTypes = setOf(HdrType.DOLBY_VISION),
        )
        val uhdSdr = source(Quality.UHD_2160, HdrType.SDR)
        val fullHdDolbyVision = source(Quality.FHD_1080, HdrType.DOLBY_VISION)
        val fullHdHdr10 = source(Quality.FHD_1080, HdrType.HDR10)

        assertTrue(exclusions.excludes(uhdSdr))
        assertTrue(exclusions.excludes(fullHdDolbyVision))
        assertFalse(exclusions.excludes(fullHdHdr10))
        assertEquals(
            listOf(fullHdHdr10),
            exclusions.filter(listOf(uhdSdr, fullHdDolbyVision, fullHdHdr10)),
        )
    }

    @Test
    fun `audio exclusions match any advertised codec`() {
        val exclusions = SourceExclusions(audioCodecs = setOf(AudioCodec.DOLBY_TRUEHD, AudioCodec.DTS))
        val atmosTrueHd = source(Quality.UHD_2160, HdrType.DOLBY_VISION).copy(
            audioCodecs = setOf(AudioCodec.DOLBY_ATMOS, AudioCodec.DOLBY_TRUEHD),
        )
        val dtsX = source(Quality.FHD_1080, HdrType.HDR10).copy(
            audioCodecs = AudioCodec.parse("DTS:X"),
        )
        val aac = source(Quality.FHD_1080, HdrType.SDR).copy(audioCodecs = setOf(AudioCodec.AAC))

        assertTrue(exclusions.excludes(atmosTrueHd))
        assertTrue(exclusions.excludes(dtsX))
        assertFalse(exclusions.excludes(aac))
        assertEquals(listOf(aac), exclusions.filter(listOf(atmosTrueHd, dtsX, aac)))
    }

    private fun source(quality: Quality, hdrType: HdrType) = StreamSource(
        id = "$quality-$hdrType",
        title = "Source",
        infoHash = idHash(quality, hdrType),
        fileIdx = null,
        magnet = "",
        quality = quality,
        hdrType = hdrType,
        sizeBytes = 1_000,
        seeders = 1,
        isCached = true,
    )

    private fun idHash(quality: Quality, hdrType: HdrType) = "${quality.name}-${hdrType.name}"
}
