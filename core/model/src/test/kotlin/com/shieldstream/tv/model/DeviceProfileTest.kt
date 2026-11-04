package com.shieldstream.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfileTest {
    @Test
    fun nokiaDefaultsToLightweightUiAndSmallerBuffer() {
        assertTrue(DeviceProfile.lightweightUiDefault("Nokia_Streaming_Box_8010"))
        assertEquals(40, DeviceProfile.defaultBufferSeconds("Nokia_Streaming_Box_8010"))
    }

    @Test
    fun otherDevicesKeepFullUiAndStandardBuffer() {
        assertFalse(DeviceProfile.lightweightUiDefault("SHIELD Android TV"))
        assertEquals(80, DeviceProfile.defaultBufferSeconds("SHIELD Android TV"))
    }
}
