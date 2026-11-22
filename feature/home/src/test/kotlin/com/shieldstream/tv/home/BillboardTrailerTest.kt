package com.shieldstream.tv.home

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Decisiones y tiempos del tráiler mudo de la portada (sin Compose ni TV). */
@OptIn(ExperimentalCoroutinesApi::class)
class BillboardTrailerTest {

    @Test
    fun `header timing is the Netflix-like 2_5 s, 45 s cap and 10 s rotation`() {
        assertEquals(2_500L, BillboardTrailerTiming.START_DELAY_MS)
        assertEquals(45_000L, BillboardTrailerTiming.MAX_DURATION_MS)
        assertEquals(10_000L, BillboardTrailerTiming.ROTATION_MS)
    }

    @Test
    fun `trailer only plays with every condition met`() {
        for (enabled in listOf(true, false)) {
            for (lightweight in listOf(true, false)) {
                for (focused in listOf(true, false)) {
                    for (resumed in listOf(true, false)) {
                        val expected = "key".takeIf { enabled && !lightweight && focused && resumed }
                        assertEquals(
                            "enabled=$enabled lightweight=$lightweight focused=$focused resumed=$resumed",
                            expected,
                            trailerToPlay("key", enabled, lightweight, focused, resumed),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `missing or blank keys never play`() {
        assertNull(trailerToPlay(null, true, false, true, true))
        assertNull(trailerToPlay("", true, false, true, true))
        assertNull(trailerToPlay("   ", true, false, true, true))
    }

    @Test
    fun `carousel rotates only with several items, normal UI and no trailer`() {
        assertTrue(shouldRotateCarousel(lightweight = false, itemCount = 2, trailerActive = false))
        assertFalse(shouldRotateCarousel(lightweight = true, itemCount = 2, trailerActive = false))
        assertFalse(shouldRotateCarousel(lightweight = false, itemCount = 1, trailerActive = false))
        assertFalse(shouldRotateCarousel(lightweight = false, itemCount = 0, trailerActive = false))
        assertFalse(shouldRotateCarousel(lightweight = false, itemCount = 2, trailerActive = true))
    }

    @Test
    fun `trailer waits for the start delay and stops at the cap`() = runTest {
        val events = mutableListOf<String?>()
        val session = BillboardTrailerSession(backgroundScope) { events += it }

        session.request("key")
        at(2_499)
        assertEquals(emptyList<String?>(), events)
        assertNull(session.playingKey)

        at(2_500)
        assertEquals(listOf("key"), events)
        assertEquals("key", session.playingKey)

        at(2_500 + 45_000 - 1)
        assertEquals(listOf("key"), events)
        assertEquals("key", session.playingKey)

        at(2_500 + 45_000)
        assertEquals(listOf("key", null), events)
        assertNull(session.playingKey)
    }

    @Test
    fun `requesting null before the delay cancels the pending start`() = runTest {
        val events = mutableListOf<String?>()
        val session = BillboardTrailerSession(backgroundScope) { events += it }

        session.request("key")
        at(1_000)
        session.request(null)
        at(2_500)

        assertEquals(emptyList<String?>(), events)
        assertNull(session.playingKey)
    }

    @Test
    fun `changing the key stops the current trailer and starts the new one after the delay`() = runTest {
        val events = mutableListOf<String?>()
        val session = BillboardTrailerSession(backgroundScope) { events += it }

        session.request("first")
        at(2_500)
        assertEquals(listOf("first"), events)

        session.request("second")
        assertEquals(listOf("first", null), events)
        assertNull(session.playingKey)

        at(2_500 + 2_499)
        assertEquals(listOf("first", null), events)
        at(2_500 + 2_500)
        assertEquals(listOf("first", null, "second"), events)
        assertEquals("second", session.playingKey)
    }

    @Test
    fun `requesting null while playing stops immediately`() = runTest {
        val events = mutableListOf<String?>()
        val session = BillboardTrailerSession(backgroundScope) { events += it }

        session.request("key")
        at(2_500)
        session.request(null)

        assertEquals(listOf("key", null), events)
        assertNull(session.playingKey)
    }

    @Test
    fun `dispose stops the trailer and any pending start`() = runTest {
        val events = mutableListOf<String?>()
        val session = BillboardTrailerSession(backgroundScope) { events += it }

        session.request("key")
        at(2_500)
        session.dispose()

        assertEquals(listOf("key", null), events)
        assertNull(session.playingKey)

        at(2_500 + 45_000)
        assertEquals(listOf("key", null), events)
    }

    @Test
    fun `dispose before the delay never plays`() = runTest {
        val events = mutableListOf<String?>()
        val session = BillboardTrailerSession(backgroundScope) { events += it }

        session.request("key")
        session.dispose()
        at(2_500)

        assertEquals(emptyList<String?>(), events)
        assertNull(session.playingKey)
    }

    /** Avanza el reloj virtual hasta el instante exacto (inclusive). */
    private fun TestScope.at(timeMs: Long) {
        advanceTimeBy(timeMs - testScheduler.currentTime)
        runCurrent()
    }
}
