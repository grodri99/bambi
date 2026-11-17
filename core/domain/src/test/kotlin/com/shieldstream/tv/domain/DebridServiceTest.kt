package com.shieldstream.tv.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DebridServiceTest {
    @Test
    fun `services with an in-app magnet resolver`() {
        val supported = DebridService.entries.filter { it.supportsDirectMagnetResolution }.toSet()

        assertEquals(setOf(DebridService.REAL_DEBRID, DebridService.ALLDEBRID, DebridService.TORBOX), supported)
    }
}
