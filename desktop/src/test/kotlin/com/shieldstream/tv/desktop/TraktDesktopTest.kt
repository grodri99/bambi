package com.shieldstream.tv.desktop

import com.shieldstream.tv.domain.MyListEntry
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.WatchProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TraktDesktopTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `progress keys translate both ways`() {
        assertEquals("movie:603", TraktKeys.toTrakt("MOVIE:603"))
        assertEquals("tv:1399:2:5", TraktKeys.toTrakt("TV:1399:2:5"))
        assertEquals("MOVIE:603", TraktKeys.toDesktop("movie:603"))
        assertEquals("TV:1399:2:5", TraktKeys.toDesktop("tv:1399:2:5"))
        assertNull(TraktKeys.toTrakt("TV:1399"))
        assertNull(TraktKeys.toDesktop("show:1"))
    }

    @Test
    fun `watched and half watched progress survive the round trip`() {
        val watched = TraktKeys.fromTrakt("movie:603", WatchProgress(1, 1, 1000))!!
        assertTrue(watched.finished)
        assertFalse(watched.resumable)
        assertTrue(TraktKeys.toWatchProgress(watched).isWatched)

        val half = TraktKeys.fromTrakt("tv:1399:1:2", WatchProgress(1_200_000, 3_000_000, 2000))!!
        assertEquals(1, half.season)
        assertEquals(2, half.episode)
        assertTrue(half.resumable)
        val back = TraktKeys.toWatchProgress(half)
        assertEquals(1_200_000, back.positionMs)
        assertEquals(3_000_000, back.durationMs)
    }

    @Test
    fun `remote progress only wins when newer and keeps the poster`() {
        val library = LibraryStore(File(tmp.root, "lib.json"))
        val item = SavedItem(id = "603", type = "MOVIE", title = "The Matrix", posterUrl = "p.jpg")
        library.record(SavedProgress(item, positionSec = 600.0, durationSec = 8000.0, updatedAt = 5000))
        library.importProgress(listOf(TraktKeys.fromTrakt("movie:603", WatchProgress(100_000, 8_000_000, 4000))!!))
        assertEquals(600.0, library.progress.single().positionSec, 0.0)
        library.importProgress(listOf(TraktKeys.fromTrakt("movie:603", WatchProgress(900_000, 8_000_000, 6000))!!))
        val p = library.progress.single()
        assertEquals(900.0, p.positionSec, 0.0)
        assertEquals("The Matrix", p.item.title)
        assertEquals("p.jpg", p.item.posterUrl)
    }

    @Test
    fun `items from trakt get their details filled later`() {
        val library = LibraryStore(File(tmp.root, "lib.json"))
        library.importMyList(listOf(SavedItem(id = "1399", type = "TV", title = "", addedAt = 10)), emptyList())
        assertEquals(listOf("1399"), library.itemsWithoutDetails().map { it.id })
        library.updateItem(SavedItem(id = "1399", type = "TV", title = "Juego de tronos", posterUrl = "got.jpg"))
        assertTrue(library.itemsWithoutDetails().isEmpty())
        assertEquals("Juego de tronos", library.myList.single().title)
        assertEquals(10, library.myList.single().addedAt)
    }

    @Test
    fun `my list follows the active profile and reports user changes`() = runBlocking {
        val store = ProfileStore(tmp.root)
        val session = Session(store.profiles.single())
        val repo = DesktopMyListRepository(store, session, scope) {}
        val library = store.library(session.profile!!)
        library.importMyList(listOf(SavedItem(id = "603", type = "MOVIE", title = "", addedAt = 1)), emptyList())
        val entries = withTimeout(5_000) { repo.entries.first { it.isNotEmpty() } }
        assertEquals(listOf(MyListEntry(MediaType.MOVIE, "603", 1)), entries)

        repo.importRemote(emptyList(), listOf("MOVIE_603"))
        assertTrue(library.myList.isEmpty())
    }

    @Test
    fun `sync prefs keep strings longs and sets`() {
        val file = File(tmp.root, "trakt-sync.json")
        val prefs = JsonTraktPrefs(file)
        prefs.put(mapOf("all_p" to "2026-11-18", "last_p" to 42L, "wl_p" to setOf("MOVIE_1", "TV_2")))
        val again = JsonTraktPrefs(file)
        assertEquals("2026-11-18", again.getString("all_p"))
        assertEquals(42L, again.getLong("last_p"))
        assertEquals(setOf("MOVIE_1", "TV_2"), again.getStringSet("wl_p"))
        again.put(mapOf("all_p" to null))
        assertNull(JsonTraktPrefs(file).getString("all_p"))
        assertEquals(0L, again.getLong("nope"))
    }

    @Test
    fun `secrets round trip and can be removed`() {
        val file = File(tmp.root, "secrets.dat")
        SecretStore(file).set("trakt_p", "{\"accessToken\":\"x\"}")
        assertEquals("{\"accessToken\":\"x\"}", SecretStore(file).get("trakt_p"))
        SecretStore(file).set("trakt_p", null)
        assertNull(SecretStore(file).get("trakt_p"))
    }
}
