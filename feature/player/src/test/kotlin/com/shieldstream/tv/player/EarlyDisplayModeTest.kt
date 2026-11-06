package com.shieldstream.tv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EarlyDisplayModeTest {
    private val modes = listOf(
        DisplayModeSpec(1, 3840, 2160, 60f),
        DisplayModeSpec(2, 3840, 2160, 25f),
        DisplayModeSpec(3, 3840, 2160, 23.976025f),
        DisplayModeSpec(4, 1920, 1080, 25f),
    )

    @Test
    fun `selects exact rate at current resolution`() {
        assertEquals(2, selectMatchingDisplayMode(modes, 3840, 2160, 25f)?.modeId)
        assertEquals(3, selectMatchingDisplayMode(modes, 3840, 2160, 23.980816f)?.modeId)
    }

    @Test
    fun `does not switch resolution to find a matching rate`() {
        assertNull(selectMatchingDisplayMode(modes, 1280, 720, 25f))
    }

    @Test
    fun `rejects rates without a close display mode`() {
        assertNull(selectMatchingDisplayMode(modes, 3840, 2160, 24.5f))
    }
}
