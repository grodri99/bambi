package com.shieldstream.tv.desktop

import java.awt.event.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class GamepadTest {
    @Test
    fun `the left stick works as the d-pad`() {
        assertEquals(0x0001, Gamepad.buttons(0, 0, 30_000))
        assertEquals(0x0002, Gamepad.buttons(0, 0, -30_000))
        assertEquals(0x0004 or 0x1000, Gamepad.buttons(0x1000, -30_000, 0))
        assertEquals(0, Gamepad.buttons(0, 8_000, -8_000))
    }

    @Test
    fun `buttons map to the keys bambi already understands`() {
        assertEquals(KeyEvent.VK_ENTER, Gamepad.MAPPING[0x1000])
        assertEquals(KeyEvent.VK_ESCAPE, Gamepad.MAPPING[0x2000])
        assertEquals(KeyEvent.VK_S, Gamepad.MAPPING[0x4000])
        assertEquals(KeyEvent.VK_RIGHT, Gamepad.MAPPING[0x0008])
    }
}
