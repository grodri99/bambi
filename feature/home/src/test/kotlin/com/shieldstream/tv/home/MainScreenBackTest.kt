package com.shieldstream.tv.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainScreenBackTest {

    @Test
    fun `exit confirmation requires the navigation rail to be focused`() {
        assertTrue(shouldRequestExit(categoryOpen = false, railFocused = true))
        assertFalse(shouldRequestExit(categoryOpen = false, railFocused = false))
        assertFalse(shouldRequestExit(categoryOpen = true, railFocused = true))
        assertFalse(shouldRequestExit(categoryOpen = true, railFocused = false))
    }
}
