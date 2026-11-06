package com.shieldstream.tv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeekFractionTest {

    @Test
    fun `unknown duration yields no fraction instead of a full bar`() {
        assertNull(seekFraction(ms = 49_000, durationMs = 0))
        assertNull(seekFraction(ms = 49_000, durationMs = -1))
    }

    @Test
    fun `known duration yields a clamped fraction`() {
        assertEquals(0f, seekFraction(ms = 0, durationMs = 100_000)!!, 0f)
        assertEquals(0.5f, seekFraction(ms = 50_000, durationMs = 100_000)!!, 0f)
        assertEquals(1f, seekFraction(ms = 150_000, durationMs = 100_000)!!, 0f)
        assertEquals(0f, seekFraction(ms = -5_000, durationMs = 100_000)!!, 0f)
    }
}
