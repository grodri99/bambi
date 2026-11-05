package com.shieldstream.tv.domain

import com.shieldstream.tv.model.MemoryProfile
import com.shieldstream.tv.model.PlaybackPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPolicyTest {
    @Test
    fun `automatic full profile preserves previous player defaults`() {
        val policy = PlaybackPolicy.automatic(512L * MIB)

        assertEquals(MemoryProfile.FULL, policy.memoryProfile)
        assertEquals(50, policy.minBufferSeconds)
        assertEquals(80, policy.maxBufferSeconds)
        assertEquals(112 * MIB, policy.targetBufferBytes)
        assertTrue(policy.seekThumbnails)
    }

    @Test
    fun `automatic constrained profile reduces memory use`() {
        val policy = PlaybackPolicy.automatic(256L * MIB)

        assertEquals(MemoryProfile.CONSTRAINED, policy.memoryProfile)
        assertEquals(30, policy.minBufferSeconds)
        assertEquals(40, policy.maxBufferSeconds)
        assertEquals(40 * MIB, policy.targetBufferBytes)
        assertFalse(policy.seekThumbnails)
    }

    @Test
    fun `custom policy keeps manual preferences`() {
        val policy = PlaybackPolicy.custom(maxBufferSeconds = 90, seekThumbnails = false)

        assertEquals(90, policy.maxBufferSeconds)
        assertEquals(112 * MIB, policy.targetBufferBytes)
        assertFalse(policy.seekThumbnails)
    }

    @Test
    fun `custom low memory buffer preserves 1_0_8 timings`() {
        val policy = PlaybackPolicy.custom(maxBufferSeconds = 40, seekThumbnails = true)

        assertEquals(20, policy.minBufferSeconds)
        assertEquals(40, policy.maxBufferSeconds)
        assertEquals(64 * MIB, policy.targetBufferBytes)
        assertEquals(3_000, policy.bufferForPlaybackMs)
        assertEquals(5_000, policy.backBufferMs)
    }

    private companion object {
        const val MIB = 1024 * 1024
    }
}
