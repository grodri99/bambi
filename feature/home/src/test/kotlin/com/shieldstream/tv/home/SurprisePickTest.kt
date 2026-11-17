package com.shieldstream.tv.home

import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.CatalogRow
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.RowStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class SurprisePickTest {
    private fun item(id: String, rating: Float, poster: String? = "p") =
        CatalogItem(id, null, MediaType.MOVIE, id, "", poster, null, 2020, rating)

    @Test
    fun `skips continue watching and my list rows and low ratings`() {
        val rows = listOf(
            CatalogRow("continue", "Seguir", listOf(item("watching", 9f)), style = RowStyle.CONTINUE),
            CatalogRow("mylist", "Mi lista", listOf(item("saved", 9f)), style = RowStyle.MY_LIST),
            CatalogRow("popular", "Populares", listOf(item("bad", 4f), item("good", 8f), item("noposter", 9f, poster = null))),
        )
        repeat(20) { seed -> assertEquals("good", surprisePick(rows, emptyList(), Random(seed))?.id) }
    }

    @Test
    fun `uses anything when nothing is well rated and null when empty`() {
        val rows = listOf(CatalogRow("r", "R", listOf(item("meh", 5f))))
        assertEquals("meh", surprisePick(rows, emptyList())?.id)
        assertNull(surprisePick(emptyList(), emptyList()))
    }
}
