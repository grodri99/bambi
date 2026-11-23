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
