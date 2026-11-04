package com.shieldstream.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileManagementTest {
    @Test
    fun unlockedProfilesDoNotRequireManagementPin() {
        assertNull(ProfileManagement.pinProfile(listOf(profile("adult"), profile("kids", kids = true))))
    }

    @Test
    fun adultPinIsPreferredForManagement() {
        val kids = profile("kids", kids = true, locked = true)
        val adult = profile("adult", locked = true)
        assertEquals(adult, ProfileManagement.pinProfile(listOf(kids, adult)))
    }

    @Test
    fun anyPinProtectsManagementWhenNoAdultPinExists() {
        val kids = profile("kids", kids = true, locked = true)
        assertEquals(kids, ProfileManagement.pinProfile(listOf(profile("adult"), kids)))
    }

    @Test
    fun profilePinsMatchOnlyTheirOwnProfileAndPin() {
        val first = profile("first", locked = true)
        val second = profile("second", locked = true)
        assertTrue(ProfilePin.matches(first, "1234"))
        assertFalse(ProfilePin.matches(first, "0000"))
        assertNotEquals(ProfilePin.hash(first.id, "1234"), ProfilePin.hash(second.id, "1234"))
    }

    private fun profile(id: String, kids: Boolean = false, locked: Boolean = false) = Profile(
        id = id,
        name = id,
        avatar = 0,
        kids = kids,
        pinHash = if (locked) ProfilePin.hash(id, "1234") else null,
    )
}
