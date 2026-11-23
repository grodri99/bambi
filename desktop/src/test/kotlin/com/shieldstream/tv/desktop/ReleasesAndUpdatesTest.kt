package com.shieldstream.tv.desktop

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
import java.time.LocalDate

class ReleasesAndUpdatesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val show = CatalogItem("1399", null, MediaType.TV, "Serie", "", null, null, null, null)
    private val today = LocalDate.of(2026, 11, 18)

    @Test
    fun `recent unwatched and upcoming within thirty days`() {
        val library = LibraryStore(File(tmp.root, "lib.json"))
        setWatched(library, null, show, listOf(2 to 1), true)
        val eps = listOf(
            ReleaseEpisode(show, 2, 1, "Visto", today.minusDays(14)),
            ReleaseEpisode(show, 2, 2, "Nuevo", today.minusDays(7)),
            ReleaseEpisode(show, 2, 3, "Hoy", today),
            ReleaseEpisode(show, 2, 4, "Pronto", today.plusDays(7)),
            ReleaseEpisode(show, 1, 1, "Viejo", today.minusDays(400)),
            ReleaseEpisode(show, 3, 1, "Lejano", today.plusDays(90)),
        )
        val r = ReleaseCenter.split(eps, library, today)
        assertEquals(listOf("Hoy", "Nuevo"), r.recent.map { it.title })
        assertEquals(listOf("Pronto"), r.upcoming.map { it.title })
    }

    @Test
    fun `followed shows are the started ones and my list`() {
        val library = LibraryStore(File(tmp.root, "lib.json"))
        library.toggleMyList(show)
        library.toggleMyList(CatalogItem("603", null, MediaType.MOVIE, "Peli", "", null, null, null, null))
        assertEquals(listOf("1399"), ReleaseCenter.followed(library).map { it.id })
    }

    @Test
    fun `notifications are given only once`() {
        val seen = SeenReleases(File(tmp.root, "seen.json"))
        assertEquals(listOf("a", "b"), seen.unseen(listOf("a", "b")))
        seen.markSeen(listOf("a"))
        assertEquals(listOf("b"), SeenReleases(File(tmp.root, "seen.json")).unseen(listOf("a", "b")))
    }

    @Test
    fun `versions compare like numbers`() {
        assertTrue(Updater.isNewer("v1.0.10", "1.0.9"))
        assertTrue(Updater.isNewer("1.1", "1.0.8"))
        assertFalse(Updater.isNewer("v1.0.8", "1.0.8"))
        assertFalse(Updater.isNewer("1.0.7", "1.0.8"))
    }

    @Test
    fun `release picks the windows installer`() {
        val body = """
            {"tag_name":"v1.0.9","body":"Notas","assets":[
              {"name":"Bambi-1.0.9.apk","browser_download_url":"https://x/a.apk"},
              {"name":"Bambi-Windows-1.0.9.zip","browser_download_url":"https://x/w.zip"},
              {"name":"Bambi-1.0.9.msi","browser_download_url":"https://x/b.msi"}]}
        """.trimIndent()
        val u = Updater.parse(body)!!
        assertEquals("1.0.9", u.version)
        assertEquals("https://x/b.msi", u.url)
        assertNull(Updater.parse("""{"tag_name":"v1.0.9","assets":[{"name":"Bambi.apk","browser_download_url":"https://x"}]}"""))
        // Con el instalador de Windows, ese es el que se usa.
        val setup = """{"tag_name":"v1.1.0","assets":[{"name":"Bambi-1.1.0.apk","browser_download_url":"https://x/a.apk"},""" +
            """{"name":"Bambi-Windows-Setup-1.1.0.exe","browser_download_url":"https://x/s.exe"}]}"""
        assertEquals("https://x/s.exe", Updater.parse(setup)!!.url)
    }
}
