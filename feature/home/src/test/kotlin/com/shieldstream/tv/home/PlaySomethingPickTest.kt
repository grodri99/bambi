package com.shieldstream.tv.home

import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.CatalogRow
import com.shieldstream.tv.model.ContinueInfo
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.RowStyle
import com.shieldstream.tv.model.UpNextInfo
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaySomethingPickTest {
    private fun item(id: String, type: MediaType = MediaType.MOVIE, poster: String? = "p") =
        CatalogItem(id, null, type, id, "", poster, null, 2020, 8f)

    private fun episode(season: Int = 1, number: Int = 2) =
        Episode(season, number, "Episode", "", null, 45)

    @Test
    fun `prefers the next episode of a followed show`() {
        val show = item("show", MediaType.TV)
        val movie = item("movie")
        val target = episode(2, 4)
        val rows = listOf(
            CatalogRow(
                "up_next", "A continuación", listOf(show),
                style = RowStyle.UP_NEXT,
                upNext = mapOf(show.key to UpNextInfo(target, lastWatched = 0L, isNewEpisode = false, isNewSeason = false)),
            ),
            CatalogRow(
                "continue", "Seguir viendo", listOf(movie),
                style = RowStyle.CONTINUE,
                progress = mapOf(movie.key to ContinueInfo(0.5f, "20 min")),
            ),
        )

        val pick = playSomethingPick(rows, emptyList(), Random(0))

        assertTrue(pick is PlaySomethingPick.UpNext)
        assertEquals(show, (pick as PlaySomethingPick.UpNext).item)
        assertEquals(target, pick.episode)
    }

    @Test
    fun `uses a recent episode when there is no up next`() {
        val show = item("show", MediaType.TV)
        val target = episode()
        val rows = listOf(
            CatalogRow(
                "new", "Estrenos", listOf(show),
                style = RowStyle.NEW_EPISODES,
                newEpisodes = mapOf(show.key to target),
            ),
        )

        val pick = playSomethingPick(rows, emptyList(), Random(0))

        assertTrue(pick is PlaySomethingPick.NewEpisode)
        assertEquals(target, (pick as PlaySomethingPick.NewEpisode).episode)
    }

    @Test
    fun `falls back to continue watching`() {
        val movie = item("movie")
        val info = ContinueInfo(0.25f, "30 min")
        val rows = listOf(
            CatalogRow(
                "continue", "Seguir viendo", listOf(movie),
                style = RowStyle.CONTINUE,
                progress = mapOf(movie.key to info),
            ),
        )

        val pick = playSomethingPick(rows, emptyList(), Random(0))

        assertTrue(pick is PlaySomethingPick.Continue)
        assertEquals(movie, (pick as PlaySomethingPick.Continue).item)
        assertEquals(info, pick.info)
    }

    @Test
    fun `falls back to my list and returns null when everything is empty`() {
        val saved = item("saved")
        val rows = listOf(CatalogRow("my_list", "Mi lista", listOf(saved), style = RowStyle.MY_LIST))

        val pick = playSomethingPick(rows, emptyList(), Random(0))

        assertTrue(pick is PlaySomethingPick.Title)
        assertEquals(saved, (pick as PlaySomethingPick.Title).item)
        assertNull(playSomethingPick(emptyList(), emptyList()))
    }
}
