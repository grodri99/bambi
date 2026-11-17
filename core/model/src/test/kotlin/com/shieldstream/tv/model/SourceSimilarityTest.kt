package com.shieldstream.tv.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceSimilarityTest {
    private fun s(id: String, q: Quality, hdr: HdrType = HdrType.SDR, title: String = id, size: Long = 50_000_000_000) = StreamSource(
        id = id, title = title, infoHash = id, fileIdx = null, magnet = "", quality = q, hdrType = hdr,
        sizeBytes = size, seeders = 1, isCached = true,
    )

    @Test
    fun `a failed 4K DV castellano falls back to the closest one`() {
        val picked = s("picked", Quality.UHD_2160, HdrType.DOLBY_VISION, "Movie 2160p DV Castellano")
        val en4kDv = s("en4kdv", Quality.UHD_2160, HdrType.DOLBY_VISION, "Movie 2160p DV English")
        val es1080 = s("es1080", Quality.FHD_1080, title = "Movie 1080p Castellano", size = 10_000_000_000)
        val es4kHdr = s("es4khdr", Quality.UHD_2160, HdrType.HDR10, "Movie 2160p HDR10 Castellano")
        val es4kDv = s("es4kdv", Quality.UHD_2160, HdrType.DOLBY_VISION, "Movie 2160p DV Castellano")
        val es720 = s("es720", Quality.HD_720, title = "Movie 720p Castellano", size = 3_000_000_000)

        val order = SourceSimilarity.orderBySimilarity(picked, listOf(es720, en4kDv, es1080, es4kHdr, es4kDv)).map { it.id }

        assertEquals(listOf("es4kdv", "es4khdr", "es1080", "es720", "en4kdv"), order)
    }
}
