package com.shieldstream.tv.desktop

import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.network.dto.TmdbReleaseCountryDto
import com.shieldstream.tv.network.dto.TmdbReleaseDateDto
import com.shieldstream.tv.network.dto.TmdbReleaseDatesDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReleaseGateTest {
    private val today = LocalDate.of(2026, 11, 23)

    private fun dates(vararg countries: Pair<String, List<Pair<Int, String>>>) = TmdbReleaseDatesDto(
        countries.map { (c, list) -> TmdbReleaseCountryDto(c, list.map { (type, date) -> TmdbReleaseDateDto(type = type, release_date = "${date}T00:00:00.000Z") }) },
    )

    @Test
    fun `in cinemas in spain is not available until the digital release`() {
        val inCinemas = dates("ES" to listOf(3 to "2026-11-01"), "US" to listOf(3 to "2026-10-20", 4 to "2026-11-10"))
        assertFalse(ReleaseGate.available(inCinemas, today))
        val digital = dates("ES" to listOf(3 to "2026-09-01", 4 to "2026-11-20"))
        assertTrue(ReleaseGate.available(digital, today))
        val announced = dates("ES" to listOf(4 to "2026-12-15"))
        assertFalse(ReleaseGate.available(announced, today))
        // TMDB solo tiene la fecha de cines en España, pero hace más de tres meses: ya estará en digital.
        assertTrue(ReleaseGate.available(dates("ES" to listOf(3 to "2026-07-01")), today))
    }

    @Test
    fun `without spanish dates any country counts`() {
        assertTrue(ReleaseGate.available(dates("US" to listOf(4 to "2026-11-10")), today))
        assertFalse(ReleaseGate.available(dates("US" to listOf(3 to "2026-11-10")), today))
    }

    @Test
    fun `old films and shows are never checked and answers are cached`() = runBlocking {
        var calls = 0
        val gate = ReleaseGate(fetch = { calls++; dates("ES" to listOf(3 to "2026-11-01")) }, file = null, today = { today })
        val old = CatalogItem("1", null, MediaType.MOVIE, "Vieja", "", null, null, 2019, null)
        val show = CatalogItem("2", null, MediaType.TV, "Serie", "", null, null, 2026, null)
        val new = CatalogItem("3", null, MediaType.MOVIE, "Nueva", "", null, null, 2026, null)
        assertEquals(listOf(old, show), gate.filter(listOf(old, show, new)))
        gate.filter(listOf(new))
        assertEquals(1, calls)
    }
}
