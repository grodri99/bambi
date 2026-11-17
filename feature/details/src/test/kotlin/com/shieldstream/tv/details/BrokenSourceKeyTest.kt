package com.shieldstream.tv.details

import com.shieldstream.tv.model.HdrType
import com.shieldstream.tv.model.Quality
import com.shieldstream.tv.model.StreamSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrokenSourceKeyTest {
    private fun source(id: String, hash: String) = StreamSource(
        id = id, title = "S", infoHash = hash, fileIdx = null, magnet = "", quality = Quality.FHD_1080,
        hdrType = HdrType.SDR, sizeBytes = 1, seeders = 1, isCached = false,
    )

    @Test
    fun `same torrent from two providers shares the broken mark`() {
        val torrentio = source("TORRENTIO:abc:0", "ABC")
        val addon = source("ADDON:abc:0", "abc")
        assertEquals("abc", torrentio.brokenKey())
        assertTrue(addon.isMarkedBroken(setOf(torrentio.brokenKey())))
    }

    @Test
    fun `sources without hash use their id and old marks by id still count`() {
        val direct = source("CASTELLANO:link", "")
        assertEquals("CASTELLANO:link", direct.brokenKey())
        assertTrue(source("OLD:id", "fff").isMarkedBroken(setOf("OLD:id")))
        assertFalse(source("OTHER", "eee").isMarkedBroken(setOf("OLD:id")))
    }
}
