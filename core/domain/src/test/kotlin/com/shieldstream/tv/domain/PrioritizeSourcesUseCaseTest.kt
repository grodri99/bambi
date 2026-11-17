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

    @Test
    fun `spanish first puts castellano ahead of cached sources in other languages`() {
        val english4k = source(id = "en4k", title = "Movie.2024.2160p.WEB-DL", quality = Quality.UHD_2160)
        val castellano = source(id = "cast", title = "Movie.2024.1080p.Castellano")
        val spanish = source(id = "spa", title = "Movie.2024.1080p.Spanish.WEB-DL")
        val uncachedCastellano = source(id = "cast-uncached", title = "Movie 2024 720p [Castellano]", isCached = false)
        val latino = source(id = "lat", title = "Movie.2024.1080p.Latino")
        val vose = source(id = "vose", title = "Movie.2024.1080p.VOSE")

        val result = prioritize(
            listOf(english4k, vose, latino, uncachedCastellano, spanish, castellano),
            supportedHdr = setOf(HdrType.SDR),
            preferSpanish = true,
        ).map { it.id }

        // Castellano en caché (a igual calidad, 3 antes que 2), castellano sin caché y luego el resto por calidad.
        assertEquals(listOf("cast", "spa", "cast-uncached", "en4k", "lat", "vose"), result)
    }

    @Test
    fun `only a few uncached castellano sources go before cached ones`() {
        val uncached = (1..5).map { source(id = "u$it", title = "Serie S01E0$it Castellano", isCached = false) }
        val cachedEnglish = source(id = "en", title = "Serie.S01E01.1080p.WEB-DL")

        val result = prioritize(
            uncached + cachedEnglish,
            supportedHdr = setOf(HdrType.SDR),
            preferSpanish = true,
        ).map { it.id }

        assertEquals(3, result.indexOf("en"))
    }

    @Test
    fun `without spanish first the usual order is untouched`() {
        val english4k = source(id = "en4k", title = "Movie.2024.2160p.WEB-DL", quality = Quality.UHD_2160)
        val castellano = source(id = "cast", title = "Movie.2024.1080p.Castellano")

        val result = prioritize(listOf(castellano, english4k), supportedHdr = setOf(HdrType.SDR))

        assertEquals(listOf("en4k", "cast"), result.map { it.id })
    }

    @Test
    fun `inside castellano quality wins over detector certainty`() {
        val sure720 = source(id = "sure720", title = "Movie 2024 720p [Castellano]", quality = Quality.HD_720)
        val likely4k = source(id = "likely4k", title = "Movie.2024.2160p.Spanish", quality = Quality.UHD_2160)

        val result = prioritize(listOf(sure720, likely4k), supportedHdr = setOf(HdrType.SDR), preferSpanish = true)

        assertEquals(listOf("likely4k", "sure720"), result.map { it.id })
    }

    @Test
    fun `resolution beats HDR and the preferred quality cap goes first`() {
        val dv1080 = source(id = "dv1080", quality = Quality.FHD_1080, hdr = HdrType.DOLBY_VISION)
        val sdr4k = source(id = "sdr4k", quality = Quality.UHD_2160)
        val dv4k = source(id = "dv4k", quality = Quality.UHD_2160, hdr = HdrType.DOLBY_VISION)
        val all = setOf(HdrType.SDR, HdrType.HDR10, HdrType.DOLBY_VISION)

        assertEquals(listOf("dv4k", "sdr4k", "dv1080"), prioritize(listOf(dv1080, sdr4k, dv4k), all).map { it.id })
        assertEquals(
            listOf("dv1080", "dv4k", "sdr4k"),
            prioritize(listOf(dv1080, sdr4k, dv4k), all, preferred = com.shieldstream.tv.model.PreferredQuality.FHD).map { it.id },
        )
    }

    @Test
    fun `unknown quality is read from the file name`() {
        val unknown = source(id = "u", title = "Movie", quality = Quality.UNKNOWN).copy(fileName = "Movie.2024.720p.mkv")
        assertEquals(Quality.HD_720, prioritize(listOf(unknown), setOf(HdrType.SDR)).single().quality)
    }

    private fun source(
        id: String,
        title: String = id,
        quality: Quality = Quality.FHD_1080,
        videoCodec: VideoCodec? = null,
        softwareDecode: Boolean = false,
        isCached: Boolean = true,
        hdr: HdrType = HdrType.SDR,
    ) = StreamSource(
        id = id,
        title = title,
        infoHash = id.padEnd(40, '0'),
        fileIdx = null,
        magnet = "magnet:?xt=urn:btih:$id",
        quality = quality,
        hdrType = hdr,
        sizeBytes = 1_000_000_000,
        seeders = 10,
        isCached = isCached,
        videoCodec = videoCodec,
        softwareDecode = softwareDecode,
    )
}
