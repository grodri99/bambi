package com.shieldstream.tv.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleStyleTest {
    @Test
    fun `the style goes to the player script and comes back`() {
        val style = SubtitleStyle(size = 125, position = 10, font = 2, color = 1, background = 1, edge = 1, original = false)
        assertEquals("125:10:2:1:1:1:0", style.encode())
        assertEquals(style, SubtitleStyle.decode(style.encode()))
        assertEquals("100:6:0:0:0:0:1", SubtitleStyle().encode())
    }

    @Test
    fun `odd values from the script are kept in range`() {
        assertEquals(SubtitleStyle(size = 200, position = 0), SubtitleStyle.decode("999:-3:0:0:0:0:1"))
        assertNull(SubtitleStyle.decode("100:6"))
        assertNull(SubtitleStyle.decode("a:b:c:d:e:f:g"))
    }
}
