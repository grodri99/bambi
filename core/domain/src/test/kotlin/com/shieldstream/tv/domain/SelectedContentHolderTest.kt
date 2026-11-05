package com.shieldstream.tv.domain

import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.PlaybackMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelectedContentHolderTest {

    @Test
    fun `selecting an item replaces details and clears stale playback state`() {
        val holder = SelectedContentHolder()
        val next = NextEpisode("show", 2, 1, "Next")
        holder.prefetchNext(next)
        holder.playQueue(emptyList(), "Old", progressKey = "old")
        val item = item()
        val target = EpisodeTarget(2, 3)

        holder.selectItem(item, target, playImmediately = true)

        assertEquals(DetailsRequest(item, target, playImmediately = true), holder.details.value)
        assertNull(holder.playback.value)
        assertNull(holder.prefetch.value)
    }

    @Test
    fun `playback fields are published as one request`() {
        val holder = SelectedContentHolder()
        val media = PlaybackMedia(MediaType.TV, "tt1", "Show", "Show", 2026, season = 2, episode = 3)
        val next = NextEpisode("show", 2, 4, "Next")

        holder.playQueue(
            sources = emptyList(),
            label = "Show S02E03",
            progressKey = "tv:show:2:3",
            fromStart = true,
            media = media,
            next = next,
        )

        val playback = holder.playback.value
        assertEquals("Show S02E03", playback?.label)
        assertEquals("tv:show:2:3", playback?.progressKey)
        assertEquals(true, playback?.fromStart)
        assertEquals(media, playback?.media)
        assertEquals(next, playback?.next)
        assertEquals(emptyList<Nothing>(), playback?.sources)
    }

    @Test
    fun `next episode request is consumed once`() {
        val holder = SelectedContentHolder()
        val next = NextEpisode("show", 2, 4, "Next")
        holder.requestNext(next)

        assertEquals(next, holder.consumeNextRequest())
        assertNull(holder.consumeNextRequest())
    }

    private fun item() = CatalogItem(
        id = "show",
        imdbId = "tt1",
        type = MediaType.TV,
        title = "Show",
        overview = "",
        posterUrl = null,
        backdropUrl = null,
        year = 2026,
        rating = null,
    )
}
