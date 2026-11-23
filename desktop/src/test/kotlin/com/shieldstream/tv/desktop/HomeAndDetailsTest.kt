package com.shieldstream.tv.desktop

import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.Category
import com.shieldstream.tv.model.MediaType
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HomeAndDetailsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val show = CatalogItem("1399", "tt0944947", MediaType.TV, "Juego de tronos", "", null, null, 2011, 8.4f)
    private val movie = CatalogItem("603", "tt0133093", MediaType.MOVIE, "The Matrix", "", null, null, 1999, 8.2f)

    @Test
    fun `continue label shows episode and minutes left like the tv`() {
        val p = SavedProgress(SavedItem.of(show), season = 1, episode = 6, positionSec = 600.0, durationSec = 1440.0)
        assertEquals("  ·  T1:E6 · 14 min", continueLabel(p))
        assertEquals("  ·  88 min", continueLabel(SavedProgress(SavedItem.of(movie), positionSec = 2400.0, durationSec = 7680.0)))
        assertEquals("", continueLabel(SavedProgress(SavedItem.of(movie), positionSec = 10.0, durationSec = 0.0)))
    }

    @Test
    fun `marking a whole season watched and back`() {
        val library = LibraryStore(File(tmp.root, "lib.json"))
        setWatched(library, null, show, (1..3).map { 1 to it }, true)
        assertTrue((1..3).all { library.progressFor(show, 1, it)?.finished == true })
        assertTrue(library.continueWatching().isEmpty())
        setWatched(library, null, show, listOf(1 to 2), false)
        assertNull(library.progressFor(show, 1, 2))
        assertTrue(library.progressFor(show, 1, 3)?.finished == true)
    }

    @Test
    fun `marking a movie watched keeps its known duration`() {
        val library = LibraryStore(File(tmp.root, "lib.json"))
        library.record(SavedProgress(SavedItem.of(movie), positionSec = 300.0, durationSec = 8160.0, updatedAt = 1))
        setWatched(library, null, movie, listOf(null to null), true)
        val p = library.progressFor(movie)!!
        assertTrue(p.finished)
        assertEquals(8160.0, p.durationSec, 0.0)
    }

    @Test
    fun `family requests go to the requester list once approved`() {
        val profiles = ProfileStore(File(tmp.root, "p"))
        val kid = DesktopProfile(name = "Peque", kids = true)
        profiles.save(kid)
        val requests = RequestStore(File(tmp.root, "requests.json"))
        requests.request(kid, movie)
        requests.request(kid, movie)
        assertEquals(1, requests.pending.size)
        assertTrue(requests.isRequested(kid.id, movie))
        requests.approve(requests.pending.single(), profiles)
        assertTrue(requests.pending.isEmpty())
        assertTrue(profiles.library(kid).inMyList(movie))
        // Lo pendiente sobrevive a reiniciar la app.
        requests.request(kid, CatalogItem("1", null, MediaType.MOVIE, "Otra", "", null, null, null, null))
        assertEquals(1, RequestStore(File(tmp.root, "requests.json")).pending.size)
    }

    @Test
    fun `rejected requests just disappear`() {
        val requests = RequestStore(File(tmp.root, "requests.json"))
        requests.request(DesktopProfile(name = "Peque", kids = true), movie)
        requests.reject(requests.pending.single())
        assertTrue(requests.pending.isEmpty())
    }

    @Test
    fun `categories follow the profile and the tab`() {
        val all = CategorySpecs.visible(emptySet(), Catalog.ALL, kids = false)
        assertEquals(Category.entries.size, all.size)
        assertFalse(Category.REALITY in CategorySpecs.visible(emptySet(), Catalog.MOVIES, kids = false))
        assertFalse(Category.CLASSICS in CategorySpecs.visible(emptySet(), Catalog.SHOWS, kids = false))
        assertEquals(Category.KIDS_SAFE.toSet(), CategorySpecs.visible(emptySet(), Catalog.ALL, kids = true).toSet())
        assertFalse(Category.DRAMAS in CategorySpecs.visible(setOf("DRAMAS"), Catalog.ALL, kids = false))
        // Por defecto, las mismas que la tele.
        assertEquals(Category.DEFAULT_VISIBLE, CategorySpecs.visible(Category.DEFAULT_HIDDEN, Catalog.ALL, kids = false).toSet())
    }

    @Test
    fun `home cache keeps rows per tab and can be cleared`() {
        HomeCache.clear()
        HomeCache.put(Catalog.ALL, false, listOf(HomeRow("Tendencias", listOf(movie))))
        assertEquals(1, HomeCache.get(Catalog.ALL, false)?.size)
        assertNull(HomeCache.get(Catalog.ALL, true))
        assertNull(HomeCache.get(Catalog.MOVIES, false))
        HomeCache.clear()
        assertNull(HomeCache.get(Catalog.ALL, false))
    }

    private fun solid(color: Int): androidx.compose.ui.graphics.ImageBitmap {
        val img = java.awt.image.BufferedImage(40, 20, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until 20) for (x in 0 until 40) img.setRGB(x, y, if (x < 20) color else 0)
        return img.toComposeImageBitmap()
    }

    @Test
    fun `dark logos are painted white and bright ones are left alone`() {
        assertTrue(LogoTone.isDark(solid(0xFF1C2E4A.toInt())))
        assertTrue(LogoTone.isDark(solid(0xFF38586E.toInt())))
        assertFalse(LogoTone.isDark(solid(0xFFE8D7A0.toInt())))
        assertFalse(LogoTone.isDark(solid(0xFFE50914.toInt())))
        // Los píxeles transparentes no cuentan.
        assertFalse(LogoTone.isDark(solid(0xFFFFFFFF.toInt())))
    }

    @Test
    fun `tv genres come in spanish`() {
        assertEquals("Ciencia ficción y fantasía", TvGenres.localized("Sci-Fi & Fantasy"))
        assertEquals("Drama", TvGenres.localized("Drama"))
    }
}
