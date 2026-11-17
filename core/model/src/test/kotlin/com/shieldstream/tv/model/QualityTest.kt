package com.shieldstream.tv.model

import org.junit.Assert.assertEquals
import org.junit.Test

class QualityTest {
    @Test
    fun `the Wolfmax4k site name is not a 4K marker`() {
        assertEquals(Quality.FHD_1080, Quality.fromName("La peli (2026) [Bluray 1080p][Esp]\n👤 10 💾 10.4 GB ⚙️ Wolfmax4k"))
        assertEquals(Quality.HD_720, Quality.fromName("La peli (2026) [Bluray 720p][Esp] ⚙️ Wolfmax4k"))
        assertEquals(Quality.UHD_2160, Quality.fromName("La peli (2026) [4k 2160p][Esp] ⚙️ Wolfmax4k"))
        assertEquals(Quality.UHD_2160, Quality.fromName("Movie.2024.4K.HDR.x265"))
    }
}
