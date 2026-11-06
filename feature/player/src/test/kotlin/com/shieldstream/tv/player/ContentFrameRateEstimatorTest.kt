package com.shieldstream.tv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContentFrameRateEstimatorTest {
    @Test
    fun `estimates 25 fps from presentation timestamps`() {
        val estimator = ContentFrameRateEstimator()
        var estimate: Float? = null
        repeat(15) { index -> estimate = estimator.onNextFrame(index * 40_000L) }

        assertEquals(25f, estimate!!, 0.001f)
    }

    @Test
    fun `estimates 23_976 fps from presentation timestamps`() {
        val estimator = ContentFrameRateEstimator()
        var estimate: Float? = null
        repeat(15) { index -> estimate = estimator.onNextFrame(index * 41_708_333L / 1_000L) }

        assertEquals(23.976f, estimate!!, 0.01f)
    }

    @Test
    fun `does not estimate variable frame rate video`() {
        val estimator = ContentFrameRateEstimator()
        var estimate: Float? = null
        val durations = longArrayOf(40_000L, 33_333L, 50_000L, 20_000L, 41_708L, 25_000L)
        var timestampUs = 0L
        repeat(30) {
            timestampUs += durations[it % durations.size]
            estimate = estimator.onNextFrame(timestampUs)
        }

        assertNull(estimate)
    }

    @Test
    fun `a seek gap restarts estimation`() {
        val estimator = ContentFrameRateEstimator()
        repeat(5) { index -> estimator.onNextFrame(index * 40_000L) }
        estimator.onNextFrame(10_000_000L)
        assertNull(estimator.onNextFrame(10_040_000L))
    }
}
