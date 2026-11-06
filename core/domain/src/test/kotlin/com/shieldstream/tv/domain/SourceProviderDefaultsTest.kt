package com.shieldstream.tv.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceProviderDefaultsTest {
    @Test
    fun missingPreferenceDisablesCometAndIthil() {
        val enabled = enabledSourceProviders(null)
        assertFalse(SourceProvider.COMET in enabled)
        assertFalse(SourceProvider.ITHIL in enabled)
        assertTrue(SourceProvider.TORRENTIO in enabled)
        assertTrue(SourceProvider.THEPIRATEBAY in enabled)
    }

    @Test
    fun savedAllEnabledChoiceKeepsCometEnabled() {
        val enabled = enabledSourceProviders(emptySet())
        assertTrue(SourceProvider.COMET in enabled)
        assertTrue(SourceProvider.ITHIL in enabled)
    }

    @Test
    fun peerflixSupportsNativeDebridConfiguration() {
        assertTrue(SourceProvider.PEERFLIX.torrentioStyle)
    }

    @Test
    fun savedDisabledProvidersAreRespected() {
        val enabled = enabledSourceProviders(setOf(SourceProvider.COMET, SourceProvider.NYAA, SourceProvider.ITHIL))
        assertFalse(SourceProvider.COMET in enabled)
        assertFalse(SourceProvider.NYAA in enabled)
        assertFalse(SourceProvider.ITHIL in enabled)
        assertTrue(SourceProvider.TORRENTIO in enabled)
    }
}
