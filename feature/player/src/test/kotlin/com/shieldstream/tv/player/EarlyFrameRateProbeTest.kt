package com.shieldstream.tv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EarlyFrameRateProbeTest {
    @Test
    fun `estimates from reordered samples and a startup discontinuity`() {
        val timestamps = listOf(
            0L, 708_000L, 375_000L, 42_000L, 83_000L, 125_000L, 167_000L, 208_000L,
            250_000L, 292_000L, 333_000L, 417_000L, 458_000L, 500_000L, 542_000L, 583_000L,
        )

        assertEquals(23.98f, estimateFrameRateFromTimestamps(timestamps)!!, 0.02f)
    }

    @Test
    fun `tolerates one initial timestamp adjustment`() {
        val timestamps = listOf(0L, 40_000L, 86_000L) +
            (3L..15L).map { it * 40_000L + 6_000L }

        assertEquals(25f, estimateFrameRateFromTimestamps(timestamps)!!, 0.001f)
    }

    @Test
    fun `rejects variable rate samples`() {
        val timestamps = (0L..15L).map { it * 40_000L + (it % 3) * 4_000L }

        assertNull(estimateFrameRateFromTimestamps(timestamps))
    }
}
