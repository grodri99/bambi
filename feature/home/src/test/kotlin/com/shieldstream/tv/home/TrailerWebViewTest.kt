package com.shieldstream.tv.home

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Looper
import android.view.View
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowWebView
import java.time.Duration

/** Ciclo de vida del WebView del tráiler sin un TV: creación, carga, sondeo, destrucción y caídas. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrailerWebViewTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun container(): TrailerContainer {
        val view = createTrailerView(context, "abc123", onPlaying = {}, onGone = {})
        return view as TrailerContainer
    }

    @Test
    fun `creates a WebView with the muted embed and TV-safe settings`() {
        val container = container()
        val web = container.getChildAt(0) as WebView

        assertEquals(embedUrl("abc123"), shadowOf(web).lastLoadedUrl)
        assertTrue(container.clipChildren)
        assertTrue(web.settings.javaScriptEnabled)
        assertTrue(web.settings.domStorageEnabled)
        assertFalse(web.settings.mediaPlaybackRequiresUserGesture)
        assertFalse(web.settings.builtInZoomControls)
        assertFalse(web.isFocusable)
        assertFalse(web.isClickable)
        assertFalse(web.isLongClickable)
    }

    @Test
    fun `page finish probes the video element`() {
        val client = TrailerClient(onPlaying = {}, onGone = {})
        val web = WebView(context)

        client.onPageFinished(web, null)

        assertEquals(PlaybackProbe, shadowOf(web).lastEvaluatedJavascript)
    }

    @Test
    fun `playing video reports once`() {
        var playing = 0
        val client = TrailerClient(onPlaying = { playing++ }, onGone = {})
        val web = WebView(context)

        client.onPageFinished(web, null)
        shadowOf(web).lastEvaluatedJavascriptCallback?.onReceiveValue("yes")

        assertEquals(1, playing)
    }

    @Test
    fun `video that is not ready yet is probed again`() {
        var playing = 0
        val client = TrailerClient(onPlaying = { playing++ }, onGone = {})
        val web = WebView(context)

        client.onPageFinished(web, null)
        shadowOf(web).lastEvaluatedJavascriptCallback?.onReceiveValue("no")
        assertEquals(0, playing)

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ProbeIntervalMs))

        shadowOf(web).lastEvaluatedJavascriptCallback?.onReceiveValue("yes")
        assertEquals(1, playing)
    }

    @Test
    fun `released client stops probing`() {
        var playing = 0
        val client = TrailerClient(onPlaying = { playing++ }, onGone = {})
        val web = WebView(context)

        client.released = true
        client.onPageFinished(web, null)

        assertNull(shadowOf(web).lastEvaluatedJavascript)
        assertEquals(0, playing)
    }

    @Test
    fun `release during a retry stops the probe`() {
        var playing = 0
        val client = TrailerClient(onPlaying = { playing++ }, onGone = {})
        val web = WebView(context)

        client.onPageFinished(web, null)
        shadowOf(web).lastEvaluatedJavascriptCallback?.onReceiveValue("no")
        client.released = true
        // El callback viejo ya no puede avisar, y el reintento programado tampoco.
        shadowOf(web).lastEvaluatedJavascriptCallback?.onReceiveValue("yes")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ProbeIntervalMs))

        assertEquals(0, playing)
    }

    @Test
    fun `render process gone is absorbed and reported`() {
        var gone = 0
        val client = TrailerClient(onPlaying = {}, onGone = { gone++ })

        val handled = client.onRenderProcessGone(null, null)

        assertTrue(handled)
        assertTrue(client.released)
        assertEquals(1, gone)
    }

    @Test
    fun `release removes the WebView and destroys it`() {
        val container = container()
        val web = container.getChildAt(0) as WebView

        releaseTrailer(container)

        assertEquals(0, container.childCount)
        assertTrue(container.client.released)
        assertTrue(shadowOf(web).wasDestroyCalled())
        assertEquals("about:blank", shadowOf(web).lastLoadedUrl)
    }

    @Test
    fun `release tolerates a view that is not the trailer container`() {
        releaseTrailer(View(context))
    }

    @Test
    fun `webview availability follows the system WebView package`() {
        ShadowWebView.setCurrentWebViewPackage(null)

        assertFalse(isWebViewAvailable())

        ShadowWebView.setCurrentWebViewPackage(PackageInfo().apply { packageName = "com.google.android.webview" })

        assertTrue(isWebViewAvailable())
    }

    @Test
    fun `release tolerates a container without a child`() {
        val client = TrailerClient(onPlaying = {}, onGone = {})
        val container = TrailerContainer(context, client)

        releaseTrailer(container)

        assertTrue(client.released)
        assertEquals(0, container.childCount)
    }
}
