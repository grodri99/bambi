package com.shieldstream.tv.details

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailsOverlayTest {

    @Test
    fun `back dismisses actions then sources then Details`() {
        assertEquals(DetailsBackAction.DISMISS_ACTIONS, detailsBackAction(actionsOpen = true, panelOpen = true))
        assertEquals(DetailsBackAction.DISMISS_SOURCES, detailsBackAction(actionsOpen = false, panelOpen = true))
        assertEquals(DetailsBackAction.EXIT_DETAILS, detailsBackAction(actionsOpen = false, panelOpen = false))
    }

    @Test
    fun `sources and actions stay mutually exclusive`() {
        assertTrue(detailsSourcesVisible(panelOpen = true, actionsOpen = false))
        assertFalse(detailsSourcesVisible(panelOpen = true, actionsOpen = true))
        assertFalse(detailsSourcesVisible(panelOpen = false, actionsOpen = true))
    }

    @Test
    fun `more is hidden while playback is pending`() {
        assertTrue(detailsMoreVisible(pendingPlay = false))
        assertFalse(detailsMoreVisible(pendingPlay = true))
    }

    @Test
    fun `release dates are shown only for future episodes`() {
        assertEquals("2026-11-13", upcomingEpisodeDate("2026-11-13", today = "2026-11-06"))
        assertEquals(null, upcomingEpisodeDate("2026-11-06", today = "2026-11-06"))
        assertEquals(null, upcomingEpisodeDate("2026-11-05", today = "2026-11-06"))
        assertEquals(null, upcomingEpisodeDate(null, today = "2026-11-06"))
        assertEquals(null, upcomingEpisodeDate("", today = "2026-11-06"))
    }
}
