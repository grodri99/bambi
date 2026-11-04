package com.shieldstream.tv.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackBufferProfileTest {
    @Test
    fun fortySecondsUsesLowMemoryProfile() {
        val profile = PlaybackBufferProfile.forSeconds(40)
        assertEquals(40_000, profile.maxBufferMs)
        assertEquals(20_000, profile.minBufferMs)
        assertEquals(3_000, profile.bufferForPlaybackMs)
        assertEquals(5_000, profile.backBufferMs)
        assertEquals(64 * 1024 * 1024, profile.targetBufferBytes)
    }

    @Test
    fun eightySecondsUsesStandardProfile() {
        val profile = PlaybackBufferProfile.forSeconds(80)
        assertEquals(80_000, profile.maxBufferMs)
        assertEquals(50_000, profile.minBufferMs)
        assertEquals(5_000, profile.bufferForPlaybackMs)
        assertEquals(15_000, profile.backBufferMs)
        assertEquals(112 * 1024 * 1024, profile.targetBufferBytes)
    }

    @Test
    fun valuesAreClampedToSupportedRange() {
        assertEquals(20_000, PlaybackBufferProfile.forSeconds(0).maxBufferMs)
        assertEquals(100_000, PlaybackBufferProfile.forSeconds(500).maxBufferMs)
    }
}
