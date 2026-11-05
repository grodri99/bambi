package com.shieldstream.tv.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainScreenBackTest {

    @Test
    fun `exit confirmation requires the root top bar to be focused`() {
        assertTrue(shouldRequestExit(categoryOpen = false, topBarFocused = true))
        assertFalse(shouldRequestExit(categoryOpen = false, topBarFocused = false))
        assertFalse(shouldRequestExit(categoryOpen = true, topBarFocused = true))
        assertFalse(shouldRequestExit(categoryOpen = true, topBarFocused = false))
    }
}
