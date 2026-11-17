package com.shieldstream.tv.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StremioAddonsTest {

    @Test
    fun `existing manifests retain their original values and stable ids`() {
        val raw = "stremio://one.example/manifest.json https://two.example/manifest.json"
        val first = StremioAddons.parse(raw)
        val second = StremioAddons.parse(raw)

        assertEquals(listOf("https://one.example", "https://two.example"), first.map { it.baseUrl })
        assertEquals(listOf("one.example", "two.example"), first.map { it.host })
        assertEquals(first.map { it.id }, second.map { it.id })
        assertEquals(raw, StremioAddons.serialize(first))
        assertTrue(first.all { it.isValid })
    }

    @Test
    fun `commas inside an addon configuration do not split the url`() {
        val torrentio = "https://torrentio.strem.fun/providers=mejortorrent,wolfmax4k,cinecalidad|language=spanish/manifest.json"
        val other = "https://example.com/abc/manifest.json"

        assertEquals(listOf(torrentio), StremioAddons.parse(torrentio).map { it.manifestUrl })
        assertEquals(
            "https://torrentio.strem.fun/providers=mejortorrent,wolfmax4k,cinecalidad|language=spanish",
            StremioAddons.parse(torrentio).single().baseUrl,
        )
        // Varias seguidas se siguen separando por espacios, saltos de línea, o coma justo tras "manifest.json".
        assertEquals(listOf(torrentio, other), StremioAddons.parse("$torrentio, $other").map { it.manifestUrl })
        assertEquals(listOf(torrentio, other), StremioAddons.parse("$torrentio\n$other").map { it.manifestUrl })
        assertEquals(listOf(torrentio, other), StremioAddons.parse(StremioAddons.serialize(StremioAddons.parse("$torrentio $other"))).map { it.manifestUrl })
    }

    @Test
    fun `invalid existing values remain visible instead of being discarded`() {
        val addons = StremioAddons.parse("not-a-url https://valid.example/manifest.json")

        assertEquals(2, addons.size)
        assertFalse(addons.first().isValid)
        assertNull(addons.first().baseUrl)
        assertEquals("Invalid addon", addons.first().host)
        assertEquals("not-a-url https://valid.example/manifest.json", StremioAddons.serialize(addons))
    }
}
