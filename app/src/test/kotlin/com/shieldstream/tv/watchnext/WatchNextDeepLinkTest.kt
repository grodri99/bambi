package com.shieldstream.tv.watchnext

import android.content.Intent
import android.net.Uri
import com.shieldstream.tv.domain.EpisodeTarget
import com.shieldstream.tv.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WatchNextDeepLinkTest {

    @Test
    fun `parses a movie resume request`() {
        val request = parse("shieldstream://resume/movie/603?profile=kids")

        assertEquals(MediaType.MOVIE, request?.type)
        assertEquals("603", request?.mediaId)
        assertEquals("kids", request?.profileId)
        assertNull(request?.target)
    }

    @Test
    fun `parses an episode resume request`() {
        val request = parse("shieldstream://resume/tv/1399?profile=default&season=2&episode=7")

        assertEquals(MediaType.TV, request?.type)
        assertEquals("1399", request?.mediaId)
        assertEquals(EpisodeTarget(season = 2, number = 7), request?.target)
    }

    @Test
    fun `rejects a different host`() {
        assertNull(parse("shieldstream://details/movie/603?profile=default"))
    }

    @Test
    fun `rejects a missing profile`() {
        assertNull(parse("shieldstream://resume/movie/603"))
    }

    @Test
    fun `rejects an episode without a complete valid target`() {
        assertNull(parse("shieldstream://resume/tv/1399?profile=default&season=2"))
        assertNull(parse("shieldstream://resume/tv/1399?profile=default&season=-1&episode=7"))
        assertNull(parse("shieldstream://resume/tv/1399?profile=default&season=2&episode=0"))
    }

    @Test
    fun `rejects intents that are not view actions`() {
        val intent = Intent(
            Intent.ACTION_MAIN,
            Uri.parse("shieldstream://resume/movie/603?profile=default"),
        )

        assertNull(parseWatchNextRequest(intent))
    }

    private fun parse(uri: String): WatchNextRequest? =
        parseWatchNextRequest(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
}
