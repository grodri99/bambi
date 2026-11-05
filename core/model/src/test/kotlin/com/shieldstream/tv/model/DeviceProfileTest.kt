package com.shieldstream.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfileTest {
    @Test
    fun nokiaDefaultsToLightweightUiAndSmallerBuffer() {
        assertTrue(DeviceProfile.lightweightUiDefault("Nokia_Streaming_Box_8010"))
        assertTrue(DeviceProfile.lightweightUiDefault("Nokia Streaming Box 8010"))
        assertEquals(40, DeviceProfile.defaultBufferSeconds("Nokia_Streaming_Box_8010"))
        assertEquals(24 * 1024 * 1024, DeviceProfile.imageMemoryCacheBytes("Nokia Streaming Box 8010"))
        assertEquals(150L * 1024 * 1024, DeviceProfile.imageDiskCacheBytes("Nokia Streaming Box 8010"))
    }

    @Test
    fun otherDevicesKeepFullUiAndStandardBuffer() {
        assertFalse(DeviceProfile.lightweightUiDefault("SHIELD Android TV"))
        assertEquals(80, DeviceProfile.defaultBufferSeconds("SHIELD Android TV"))
        assertEquals(64 * 1024 * 1024, DeviceProfile.imageMemoryCacheBytes("SHIELD Android TV"))
        assertEquals(512L * 1024 * 1024, DeviceProfile.imageDiskCacheBytes("SHIELD Android TV"))
    }
}
