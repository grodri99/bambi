package com.shieldstream.tv.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadNamesTest {
    @Test
    fun `movie and episode names are readable and safe`() {
        assertEquals("Dune (2021).mkv", DownloadNames.fileName("Dune", 2021, null, null, null, "mkv"))
        assertEquals(
            "Los Simpson - S38E01 - Episodio 1.mp4",
            DownloadNames.fileName("Los Simpson", 1989, 38, 1, "Episodio 1", "mp4"),
        )
        assertEquals("Fast Furious (2001).mkv", DownloadNames.fileName("Fast / Furious?", 2001, null, null, null, "mkv"))
    }

    @Test
    fun `extension comes from the file name, then the url, else mkv`() {
        assertEquals("mp4", DownloadNames.extensionFor("Movie.2020.1080p.MP4", "https://x/y"))
        assertEquals("avi", DownloadNames.extensionFor(null, "https://host/d/abc/Movie.avi?token=1"))
        assertEquals("mkv", DownloadNames.extensionFor(null, "https://host/d/abc"))
    }
}
