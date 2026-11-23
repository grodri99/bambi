package com.shieldstream.tv.desktop

import com.shieldstream.tv.model.Quality
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SourcesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/resolve/ok" -> MockResponse().setResponseCode(302).setHeader("Location", "/cdn/video.mkv")
                "/cdn/video.mkv" -> MockResponse().setResponseCode(206).setHeader("Content-Type", "video/x-matroska").setBody("x")
                "/resolve/page" -> MockResponse().setResponseCode(200).setHeader("Content-Type", "text/html").setBody("<html>")
                "/resolve/calm" -> MockResponse().setResponseCode(429)
                else -> MockResponse().setResponseCode(404)
            }
        }
        start()
    }

    private val resolver = LinkResolver(OkHttpClient())

    @After
    fun tearDown() = server.shutdown()

    private fun url(path: String) = server.url(path).toString()

    private fun source(path: String, title: String = path) = DesktopSource(title, "", Quality.FHD_1080, true, url(path))

    @Test
    fun `follows redirects to the video`() {
        val check = resolver.resolve(url("/resolve/ok"), "Real-Debrid")
        assertEquals(LinkCheck.Ready(url("/cdn/video.mkv")), check)
    }

    @Test
    fun `a web page is not a video`() {
        val check = resolver.resolve(url("/resolve/page"), "Real-Debrid")
        assertTrue(check is LinkCheck.Problem && !check.stop)
    }

    @Test
    fun `too many requests stops trying`() {
        val check = resolver.resolve(url("/resolve/calm"), "Real-Debrid")
        assertTrue(check is LinkCheck.Problem && check.stop)
    }

    @Test
    fun `play skips broken sources and keeps the rest as alternatives`() = runBlocking {
        val tried = ArrayList<String>()
        val pick = pickPlayable(
            listOf(source("/resolve/page", "a"), source("/missing", "b"), source("/resolve/ok", "c"), source("/resolve/ok", "d")),
            "Real-Debrid",
            resolver,
            onTrying = { tried += it.title },
        )
        assertTrue(pick is Pick.Ready)
        pick as Pick.Ready
        assertEquals("c", pick.source.title)
        assertEquals(url("/cdn/video.mkv"), pick.source.url)
        assertEquals(listOf("d"), pick.rest.map { it.title })
        assertEquals(listOf("a", "b", "c"), tried)
    }

    @Test
    fun `play stops when the service asks for calm`() = runBlocking {
        val pick = pickPlayable(listOf(source("/resolve/calm"), source("/resolve/ok")), "Real-Debrid", resolver)
        assertTrue(pick is Pick.Failed)
    }

    @Test
    fun `broken sources are remembered and can be restored`() {
        val file = File(tmp.root, "broken.json")
        BrokenSources(file).discard("Pelicula.2026.1080p.WEB")
        assertTrue(BrokenSources(file).isBroken("Pelicula.2026.1080p.WEB"))
        BrokenSources(file).restore("Pelicula.2026.1080p.WEB")
        assertFalse(BrokenSources(file).isBroken("Pelicula.2026.1080p.WEB"))
    }

    @Test
    fun `exclusions come from the settings`() {
        val cfg = DesktopConfig(excludedQualities = setOf("UHD_2160"), excludedHdr = setOf("DOLBY_VISION"))
        assertEquals(setOf(Quality.UHD_2160), cfg.exclusions.qualities)
        assertEquals(setOf(com.shieldstream.tv.model.HdrType.DOLBY_VISION), cfg.exclusions.hdrTypes)
        assertTrue(DesktopConfig().exclusions.isEmpty)
    }
}
