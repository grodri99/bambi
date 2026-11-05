package com.shieldstream.tv.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitlePreferenceTest {
    @Test
    fun `spanish matches language and regional variants`() {
        assertTrue(SubtitlePreference.SPANISH.matches("es"))
        assertTrue(SubtitlePreference.SPANISH.matches("es-ES"))
        assertTrue(SubtitlePreference.SPANISH.matches("spa"))
        assertTrue(SubtitlePreference.SPANISH.matches("SPA-MX"))
        assertFalse(SubtitlePreference.SPANISH.matches("en"))
        assertFalse(SubtitlePreference.SPANISH.matches(null))
    }

    @Test
    fun `english matches language and regional variants`() {
        assertTrue(SubtitlePreference.ENGLISH.matches("en"))
        assertTrue(SubtitlePreference.ENGLISH.matches("en-GB"))
        assertTrue(SubtitlePreference.ENGLISH.matches("eng"))
        assertFalse(SubtitlePreference.ENGLISH.matches("es"))
    }

    @Test
    fun `off never forces a subtitle track`() {
        assertFalse(SubtitlePreference.OFF.matches("es"))
        assertFalse(SubtitlePreference.OFF.matches("en"))
    }
}
