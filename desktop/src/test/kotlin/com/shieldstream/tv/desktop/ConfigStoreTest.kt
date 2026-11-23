package com.shieldstream.tv.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ConfigStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `keys are never written to config json`() {
        val file = File(tmp.root, "config.json")
        ConfigStore(file).save(DesktopConfig(tmdbKey = "tmdb-secret", debridKey = "debrid-secret", addons = "https://a/b/manifest.json", traktClientSecret = "trakt-secret"))
        val text = file.readText()
        assertFalse(text.contains("tmdb-secret") || text.contains("debrid-secret") || text.contains("trakt-secret") || text.contains("manifest"))
        val again = ConfigStore(file).current
        assertEquals("tmdb-secret", again.tmdbKey)
        assertEquals("debrid-secret", again.debridKey)
        assertEquals("https://a/b/manifest.json", again.addons)
        assertEquals("trakt-secret", again.traktClientSecret)
    }

    @Test
    fun `plain keys from an older version move to the encrypted store`() {
        val file = File(tmp.root, "config.json")
        file.writeText("""{"tmdbKey":"old-tmdb","debrid":"TORBOX","debridKey":"old-debrid","spanishFirst":false}""")
        val loaded = ConfigStore(file).current
        assertEquals("old-tmdb", loaded.tmdbKey)
        assertEquals("old-debrid", loaded.debridKey)
        assertEquals("TORBOX", loaded.debrid)
        assertFalse(loaded.spanishFirst)
        assertFalse(file.readText().contains("old-tmdb"))
        assertTrue(File(tmp.root, "secrets.dat").isFile)
        assertEquals("old-debrid", ConfigStore(file).current.debridKey)
    }

    @Test
    fun `clearing a key removes it`() {
        val file = File(tmp.root, "config.json")
        val store = ConfigStore(file)
        store.save(DesktopConfig(tmdbKey = "x"))
        store.save(DesktopConfig(tmdbKey = ""))
        assertEquals("", ConfigStore(file).current.tmdbKey)
    }
}
