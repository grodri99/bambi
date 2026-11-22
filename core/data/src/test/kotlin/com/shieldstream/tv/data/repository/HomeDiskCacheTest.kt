package com.shieldstream.tv.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.CatalogRow
import com.shieldstream.tv.model.FeaturedBadge
import com.shieldstream.tv.model.FeaturedItem
import com.shieldstream.tv.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * La portada se guarda en disco: sin `trailerKey` la caché antigua debe ignorarse **una vez** sin
 * tirar las filas, o el tráiler no aparecería hasta que caducase el Inicio entero.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeDiskCacheTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val cache = HomeDiskCache(context)

    @Before
    fun clearCacheDir() {
        File(context.filesDir, "home_cache").deleteRecursively()
    }

    @Test
    fun `featured round trip keeps the trailer key`() {
        cache.writeFeatured("featured_ALL", listOf(featured(trailerKey = "trailer-abc")))

        val entry = cache.readFeatured("featured_ALL")

        assertEquals(listOf("trailer-abc"), entry?.value?.map { it.trailerKey })
    }

    @Test
    fun `featured without trailer round trips with no key`() {
        cache.writeFeatured("featured_MOVIES", listOf(featured(trailerKey = null)))

        val restored = cache.readFeatured("featured_MOVIES")?.value?.single()

        assertNull(restored?.trailerKey)
        assertEquals("Título", restored?.item?.title)
    }

    @Test
    fun `featured cache written before trailers is ignored so it is fetched again`() {
        legacyFeaturedFile("featured_TV")

        assertNull(cache.readFeatured("featured_TV"))
    }

    @Test
    fun `old featured cache does not throw away the cached rows`() {
        cache.writeRows(
            "home_ALL_p2",
            listOf(CatalogRow(id = "trending", title = "Tendencias", items = listOf(item("tt1")))),
        )
        legacyFeaturedFile("featured_ALL")

        assertNull(cache.readFeatured("featured_ALL"))
        assertEquals(listOf("tt1"), cache.readRows("home_ALL_p2")?.value?.single()?.items?.map { it.id })
    }

    /** Caché tal y como la escribía la versión anterior: sin la marca `featuredTrailers`. */
    private fun legacyFeaturedFile(key: String) {
        val dir = File(context.filesDir, "home_cache").apply { mkdirs() }
        File(dir, "$key.json").writeText("""{"v":1,"savedAt":123,"featured":[]}""")
    }

    private fun item(id: String) = CatalogItem(
        id = id,
        imdbId = null,
        type = MediaType.MOVIE,
        title = "Título",
        overview = "",
        posterUrl = "/poster.jpg",
        backdropUrl = "/backdrop.jpg",
        year = 2024,
        rating = 7.5f,
    )

    private fun featured(trailerKey: String?) = FeaturedItem(
        item = item("1"),
        badge = FeaturedBadge.POPULAR,
        logoUrl = "/logo.png",
        genre = "Drama",
        durationText = "1 h 40 min",
        certification = "+13",
        trailerKey = trailerKey,
    )
}
