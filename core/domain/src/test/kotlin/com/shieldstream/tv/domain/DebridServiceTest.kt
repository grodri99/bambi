package com.shieldstream.tv.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DebridServiceTest {
    @Test
    fun `only services with an in-app magnet resolver enable independent scrapers`() {
        val supported = DebridService.entries.filter { it.supportsDirectMagnetResolution }.toSet()

        assertEquals(setOf(DebridService.REAL_DEBRID, DebridService.ALLDEBRID), supported)
    }
}
