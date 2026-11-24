package com.shieldstream.tv.desktop

import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.MediaType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ListsAndLayoutTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun movie(id: String) = CatalogItem(id, null, MediaType.MOVIE, "Peli $id", "", "https://p/$id.jpg", null, 2020, 7f)

    @Test
    fun `custom lists are saved per profile and survive a restart`() {
        val file = File(tmp.root, "library-p.json")
        val library = LibraryStore(file)
        val list = library.createList("Terror", first = movie("1"))
        library.toggleInList(list.id, movie("2"))
        library.toggleInList(list.id, movie("1"))
        val again = LibraryStore(file).list(list.id)!!
        assertEquals("Terror", again.name)
        assertEquals(listOf("2"), again.items.map { it.id })
        library.renameList(list.id, "Miedo")
        assertEquals("Miedo", LibraryStore(file).list(list.id)!!.name)
        library.deleteList(list.id)
        assertNull(LibraryStore(file).list(list.id))
    }

    @Test
    fun `trakt lists are replaced when refreshed and cannot be edited`() {
        val library = LibraryStore(File(tmp.root, "library-q.json"))
        val first = library.saveTraktList("/lists/42", "Marvel", "juan", listOf(movie("1")))
        val second = library.saveTraktList("/lists/42", "Marvel", "juan", listOf(movie("1"), movie("3")))
        assertEquals(first.id, second.id)
        assertEquals(1, library.lists.size)
        library.toggleInList(second.id, movie("9"))
        assertEquals(listOf("1", "3"), library.list(second.id)!!.items.map { it.id })
    }

    @Test
    fun `home layout hides, reorders and keeps new rows`() {
        val rows = listOf("continue", "mylist", "top10", "row:Acción")
        var layout = HomeLayout()
        assertEquals(rows, layout.arrange(rows))
        layout = layout.toggle("mylist").move("top10", -1, rows)
        assertEquals(listOf("continue", "top10", "row:Acción"), layout.arrange(rows))
        assertEquals(listOf("top10", "continue", "row:Acción"), layout.move("top10", -1, rows).arrange(rows))
        // Una lista nueva aparece al final, a la vista.
        assertEquals(listOf("continue", "top10", "row:Acción", "list:x"), layout.arrange(rows + "list:x"))
        // Lo que ya no existe (una lista borrada) no sale.
        assertFalse("row:Acción" in layout.arrange(rows - "row:Acción"))
        val json = Json { ignoreUnknownKeys = true }
        val profile = json.decodeFromString(DesktopProfile.serializer(), """{"name":"Ana"}""")
        assertEquals(HomeLayout(), profile.homeLayout)
    }

    @Test
    fun `trakt list links and responses are read`() {
        assertEquals("/users/juan/lists/terror-2024", TraktLists.pathFromUrl("https://trakt.tv/users/juan/lists/terror-2024?sort=rank,asc"))
        assertEquals("/lists/12345", TraktLists.pathFromUrl("trakt.tv/lists/12345"))
        assertNull(TraktLists.pathFromUrl("https://www.themoviedb.org/movie/1"))
        val info = TraktLists.info(
            Json.parseToJsonElement(
                """{"name":"Marvel","description":"MCU","item_count":30,"likes":5,"ids":{"trakt":777,"slug":"marvel"},"user":{"username":"Juan","ids":{"slug":"juan"}}}""",
            ) as kotlinx.serialization.json.JsonObject,
        )!!
        assertEquals("/lists/777", info.path)
        assertEquals("Juan", info.user)
        assertEquals(30, info.count)
        val items = TraktLists.items(
            Json.parseToJsonElement(
                """[{"type":"movie","movie":{"ids":{"tmdb":10}}},{"type":"show","show":{"ids":{"tmdb":20}}},{"type":"person","person":{}}]""",
            ),
        )
        assertEquals(listOf(MediaType.MOVIE to "10", MediaType.TV to "20"), items)
        assertTrue(TraktLists.items(null).isEmpty())
    }
}
