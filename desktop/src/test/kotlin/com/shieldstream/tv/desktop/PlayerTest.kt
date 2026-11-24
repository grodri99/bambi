package com.shieldstream.tv.desktop

import com.shieldstream.tv.domain.SkipSegments
import com.shieldstream.tv.domain.TimeRange
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PlayerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `skip segments become script options`() {
        val segments = SkipSegments(intro = TimeRange(60_000, 90_500), outro = TimeRange(2_800_000, Long.MAX_VALUE))
        val opts = BambiScript.options(segments, nextEpisode = true)
        assertTrue("bambi-intro=60.0-90.5" in opts)
        assertTrue("bambi-recap=" in opts)
        assertTrue("bambi-outro=2800.0--1.0" in opts)
        assertTrue("bambi-next=yes" in opts)
        assertTrue("bambi-next=no" in BambiScript.options(null, nextEpisode = false))
    }

    @Test
    fun `the player shows the source badges like the tv`() {
        fun src(title: String, q: com.shieldstream.tv.model.Quality) = DesktopSource(title, "💾 20 GB", q, true, "https://x")
        assertEquals(
            listOf("4K", "Dolby Vision", "HDR10", "Dolby Atmos", "5.1"),
            sourceBadges(src("Dune.Part.Two.2024.2160p.WEB-DL.DV.HDR.DDP5.1.Atmos-FLUX", com.shieldstream.tv.model.Quality.UHD_2160)),
        )
        // «Dolby Digital» no es Dolby Vision, y un 1080p sin HDR solo lleva su calidad y el audio.
        val plain = sourceBadges(src("Show.S01E05.1080p.WEB.Dolby.Digital.5.1-GRP", com.shieldstream.tv.model.Quality.FHD_1080))
        assertFalse("Dolby Vision" in plain)
        assertEquals("1080p", plain.first())
        assertTrue("5.1" in plain)
        val opts = BambiScript.options(null, false, badges = listOf("4K", "Dolby Vision"))
        assertTrue("bambi-badges=4K|Dolby Vision" in opts)
    }

    @Test
    fun `source tags show picture, sound, spanish and the rest once`() {
        val s = DesktopSource(
            "Peli.2026.2160p.WEB-DL.DV.HDR.DDP5.1.Atmos-GRP", "Dolby Vision · 18.4 GB · Seeds 120 · ES/EN",
            com.shieldstream.tv.model.Quality.UHD_2160, true, "https://x", "Torrentio", spanish = true,
        )
        val tags = sourceTags(s)
        assertEquals(
            listOf("Dolby Vision", "HDR10", "Dolby Atmos", "5.1", "Castellano", "18.4 GB", "Seeds 120", "ES/EN"),
            tags.map { it.first },
        )
        assertEquals(TagKind.VIDEO, tags.first().second)
        assertEquals(TagKind.SPANISH, tags.first { it.first == "Castellano" }.second)
    }

    @Test
    fun `the script report is read from mpv output`() {
        val r = BambiScript.parse("[bambi] bambi-alang=spa\n[bambi] bambi-slang=no\n[bambi] bambi-next-now\nExiting... (Quit)")
        assertEquals("spa", r.audioLang)
        assertEquals("no", r.subLang)
        assertTrue(r.next)
        assertFalse(r.sleep)
        val empty = BambiScript.parse("Exiting... (End of file)")
        assertNull(empty.audioLang)
        assertFalse(empty.next)
        assertTrue(BambiScript.parse("bambi-sleep-episode").sleepAtEnd)
        assertTrue(BambiScript.parse("[bambi] bambi-slate-now").slate)
        assertTrue("bambi-slate=yes" in BambiScript.options(null, nextEpisode = false, slateCheck = true))
    }

    @Test
    fun `the script is written next to the playback`() {
        val file = BambiScript.write(tmp.root)
        val text = file.readText()
        assertTrue(text.contains("read_options(o, \"bambi\")"))
        assertTrue(text.contains("bambi-next-now"))
    }

    @Test
    fun `track choices are remembered per show`() {
        val library = LibraryStore(File(tmp.root, "lib.json"))
        val show = CatalogItem("1399", null, MediaType.TV, "Juego de tronos", "", null, null, null, null)
        assertNull(library.tracksFor(show))
        library.saveTracks(show, TrackPreference("eng", "spa"))
        library.saveTracks(show, TrackPreference(null, null))
        assertEquals(TrackPreference("eng", "spa"), LibraryStore(File(tmp.root, "lib.json")).tracksFor(show))
    }

    @Test
    fun `requests from the bambi script are read from the mpv pipe`() {
        assertEquals("fs#3", requestIn("{\"event\":\"property-change\",\"id\":1,\"name\":\"user-data/bambi/request\",\"data\":\"fs#3\"}"))
        assertEquals("esc#4", requestIn("{\"event\":\"property-change\",\"id\":1,\"name\":\"user-data/bambi/request\",\"data\":\"esc#4\"}"))
        // Así lo manda mpv con observe_property_string: el texto entre comillas.
        assertEquals("fs#5", requestIn("{\"event\":\"property-change\",\"id\":1,\"name\":\"user-data/bambi/request\",\"data\":\"\\\"fs#5\\\"\"}"))
        assertNull(requestIn("{\"event\":\"property-change\",\"id\":1,\"name\":\"user-data/bambi/request\",\"data\":null}"))
        assertNull(requestIn("{\"request_id\":0,\"error\":\"success\"}"))
        assertNull(requestIn("no es json"))
    }

    @Test
    fun `the player draws its own netflix style controls`() {
        val text = BambiScript.write(tmp.root).readText()
        assertTrue(text.contains("create_osd_overlay"))
        assertTrue(text.contains("user-data/bambi/request"))
        assertTrue("bambi-accent=1E90FF" in BambiScript.options(null, nextEpisode = false, accent = "1E90FF"))
    }
}
