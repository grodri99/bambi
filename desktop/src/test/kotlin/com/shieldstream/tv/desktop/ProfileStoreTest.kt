package com.shieldstream.tv.desktop

import com.shieldstream.tv.model.Profile
import com.shieldstream.tv.model.ProfileTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProfileStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `first start creates Principal`() {
        val store = ProfileStore(tmp.root)
        assertEquals(listOf("Principal"), store.profiles.map { it.name })
        assertEquals(Profile.DEFAULT_ID, store.profiles.single().id)
        assertTrue(File(tmp.root, "profiles.json").isFile)
    }

    @Test
    fun `library from a version without profiles moves to Principal`() {
        File(tmp.root, "library.json").writeText(
            """{"myList":[{"id":"603","type":"MOVIE","title":"The Matrix"}],"progress":[]}""",
        )
        val store = ProfileStore(tmp.root)
        val library = store.library(store.profiles.single())
        assertEquals(listOf("The Matrix"), library.myList.map { it.title })
        assertFalse(File(tmp.root, "library.json").exists())
        // Al volver a abrir no se repite la migración ni se pierde nada.
        val again = ProfileStore(tmp.root)
        assertEquals(1, again.profiles.size)
        assertEquals(1, again.library(again.profiles.single()).myList.size)
    }

    @Test
    fun `at most five profiles`() {
        val store = ProfileStore(tmp.root)
        repeat(4) { assertTrue(store.save(DesktopProfile(name = "P$it"))) }
        assertFalse(store.canAdd)
        assertFalse(store.save(DesktopProfile(name = "Sexto")))
        assertEquals(5, store.profiles.size)
        // Editar uno existente sigue funcionando con el máximo alcanzado.
        val first = store.profiles.first()
        assertTrue(store.save(first.copy(name = "Renombrado")))
        assertEquals("Renombrado", store.byId(first.id)?.name)
    }

    @Test
    fun `delete removes its library but never the last profile`() {
        val store = ProfileStore(tmp.root)
        val other = DesktopProfile(name = "Invitado")
        store.save(other)
        File(tmp.root, "library-${other.id}.json").writeText("{}")
        assertTrue(store.delete(other.id))
        assertFalse(File(tmp.root, "library-${other.id}.json").exists())
        assertFalse(store.delete(store.profiles.single().id))
        assertEquals(1, store.profiles.size)
    }

    @Test
    fun `pin is stored hashed and checked`() {
        val profile = DesktopProfile(id = "abc", name = "Gonza").withPin("1234")
        assertTrue(profile.locked)
        assertNotEquals("1234", profile.pinHash)
        assertTrue(profile.pinMatches("1234"))
        assertFalse(profile.pinMatches("4321"))
        val open = profile.withPin(null)
        assertNull(open.pinHash)
        assertTrue(open.pinMatches("0000"))
        assertTrue(DesktopProfile.isValidPin("0420"))
        assertFalse(DesktopProfile.isValidPin("12a4"))
        assertFalse(DesktopProfile.isValidPin("123"))
    }

    @Test
    fun `pin hash is the same as on the TV`() {
        val profile = DesktopProfile(id = "abc", name = "Gonza").withPin("1234")
        assertEquals(com.shieldstream.tv.model.ProfilePin.hash("abc", "1234"), profile.pinHash)
    }

    @Test
    fun `profile settings survive a restart`() {
        val store = ProfileStore(tmp.root)
        val kid = DesktopProfile(name = "Peque", kids = true, theme = ProfileTheme.OCEAN.name).withPin("0000")
        store.save(kid)
        val loaded = ProfileStore(tmp.root).byId(kid.id)!!
        assertTrue(loaded.kids)
        assertEquals(ProfileTheme.OCEAN, loaded.profileTheme)
        assertTrue(loaded.pinMatches("0000"))
    }

    @Test
    fun `unknown theme falls back to Bambi`() {
        assertEquals(ProfileTheme.BAMBI, DesktopProfile(name = "x", theme = "NOPE").profileTheme)
    }

    @Test
    fun `old profiles json without new fields still loads`() {
        File(tmp.root, "profiles.json").writeText("""[{"id":"p1","name":"Gonzalo","avatar":2}]""")
        val p = ProfileStore(tmp.root).profiles.single()
        assertEquals("Gonzalo", p.name)
        assertFalse(p.locked)
        assertFalse(p.kids)
        assertEquals(ProfileTheme.BAMBI, p.profileTheme)
    }
}
