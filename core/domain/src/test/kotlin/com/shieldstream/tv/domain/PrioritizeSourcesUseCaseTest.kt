package com.shieldstream.tv.domain

import com.shieldstream.tv.model.HdrType
import com.shieldstream.tv.model.Quality
import com.shieldstream.tv.model.StreamSource
import com.shieldstream.tv.model.VideoCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrioritizeSourcesUseCaseTest {
    private val prioritize = PrioritizeSourcesUseCase()

    @Test
    fun `hardware AV1 is not penalized or labelled as CPU decoding`() {
        val av1 = source(id = "av1", videoCodec = VideoCodec.AV1, softwareDecode = true)
        val hevc = source(id = "hevc", videoCodec = VideoCodec.HEVC)

        val result = prioritize(
            listOf(av1, hevc),
            supportedHdr = setOf(HdrType.SDR),
            hardwareVideoCodecs = setOf(VideoCodec.AV1, VideoCodec.HEVC),
        )

        assertEquals("av1", result.first().id)
        assertFalse(result.first().softwareDecode)
    }

    @Test
    fun `software AV1 remains behind an equivalent hardware source`() {
        val av1 = source(id = "av1", videoCodec = VideoCodec.AV1, softwareDecode = true)
        val hevc = source(id = "hevc", videoCodec = VideoCodec.HEVC)

        val result = prioritize(
            listOf(av1, hevc),
            supportedHdr = setOf(HdrType.SDR),
            hardwareVideoCodecs = setOf(VideoCodec.HEVC),
        )

        assertEquals("hevc", result.first().id)
        assertTrue(result.last().softwareDecode)
    }

    @Test
    fun `4K AV1 requires a hardware decoder`() {
        val av1 = source(
            id = "av1-4k",
            quality = Quality.UHD_2160,
            videoCodec = VideoCodec.AV1,
            softwareDecode = true,
        )
        val hevc = source(id = "hevc-4k", quality = Quality.UHD_2160, videoCodec = VideoCodec.HEVC)

        val softwareOnly = prioritize(
            listOf(av1, hevc),
            supportedHdr = setOf(HdrType.SDR),
            hardwareVideoCodecs = setOf(VideoCodec.HEVC),
        )
        val hardwareAv1 = prioritize(
            listOf(av1, hevc),
            supportedHdr = setOf(HdrType.SDR),
            hardwareVideoCodecs = setOf(VideoCodec.HEVC, VideoCodec.AV1),
        )

        assertEquals(listOf("hevc-4k"), softwareOnly.map { it.id })
        assertEquals(setOf("av1-4k", "hevc-4k"), hardwareAv1.map { it.id }.toSet())
    }

    @Test
    fun `upscaled 1080p DS4K is not mistaken for AV1 4K`() {
        val av1 = source(
            id = "av1-upscaled",
            title = "Movie.1080p.DS4K.AV1",
            quality = Quality.UHD_2160,
            videoCodec = VideoCodec.AV1,
            softwareDecode = true,
        )

        val result = prioritize(
            listOf(av1),
            supportedHdr = setOf(HdrType.SDR),
            hardwareVideoCodecs = emptySet(),
        )

        assertEquals(listOf("av1-upscaled"), result.map { it.id })
    }

    private fun source(
        id: String,
        title: String = id,
        quality: Quality = Quality.FHD_1080,
        videoCodec: VideoCodec? = null,
        softwareDecode: Boolean = false,
    ) = StreamSource(
        id = id,
        title = title,
        infoHash = id.padEnd(40, '0'),
        fileIdx = null,
        magnet = "magnet:?xt=urn:btih:$id",
        quality = quality,
        hdrType = HdrType.SDR,
        sizeBytes = 1_000_000_000,
        seeders = 10,
        isCached = true,
        videoCodec = videoCodec,
        softwareDecode = softwareDecode,
    )
}
