package com.shieldstream.tv.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IthilUnlockGestureTest {
    @Test
    fun threeTapsWithinWindowUnlockOnce() {
        val gesture = IthilUnlockGesture()

        assertFalse(gesture.registerTap(1_000))
        assertFalse(gesture.registerTap(2_000))
        assertTrue(gesture.registerTap(3_000))
        assertFalse(gesture.registerTap(3_100))
    }

    @Test
    fun slowSequenceStartsOver() {
        val gesture = IthilUnlockGesture()

        assertFalse(gesture.registerTap(0))
        assertFalse(gesture.registerTap(1_000))
        assertFalse(gesture.registerTap(4_001))
        assertFalse(gesture.registerTap(5_000))
        assertTrue(gesture.registerTap(6_000))
    }
}
