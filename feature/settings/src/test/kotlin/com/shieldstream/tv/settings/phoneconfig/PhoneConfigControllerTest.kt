package com.shieldstream.tv.settings.phoneconfig

import com.shieldstream.tv.domain.ApiKeysRepository
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.domain.DebridService
import com.shieldstream.tv.domain.KeyCheck
import com.shieldstream.tv.domain.IthilAccessRepository
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.domain.SourceProvider
import com.shieldstream.tv.domain.StremioAddonMetadata
import com.shieldstream.tv.domain.StremioAddons
import com.shieldstream.tv.model.AppLanguage
import com.shieldstream.tv.model.SubtitlePreference
import com.shieldstream.tv.settings.SetupAudioPreference
import com.shieldstream.tv.strings.Texts
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PhoneConfigControllerTest {
    private val apiKeys = mockk<ApiKeysRepository>(relaxed = true)
    private val settings = mockk<SettingsRepository>(relaxed = true)
    private val ithilAccess = mockk<IthilAccessRepository>(relaxed = true)
    private val controller = PhoneConfigController(apiKeys, settings, FakeTexts(), ithilAccess)

    init {
        every { apiKeys.keys } returns MutableStateFlow(
            mapOf(
                ApiService.TMDB to "tmdb-complete-secret",
                ApiService.ALLDEBRID to "alldebrid-complete-secret",
            ),
        )
        every { settings.language } returns AppLanguage.EN
        every { settings.debridService } returns MutableStateFlow(DebridService.ALLDEBRID)
        every { settings.sourceProviders } returns MutableStateFlow(SourceProvider.entries.toSet())
        every { ithilAccess.unlocked } returns MutableStateFlow(true)
        every { settings.lightweightUi } returns MutableStateFlow(false)
        every { settings.experimentalSubtitleSync } returns MutableStateFlow(true)
        every { settings.autoNextEpisode } returns MutableStateFlow(true)
        every { settings.spanishOnlySources } returns MutableStateFlow(false)
        every { settings.spanishFirst } returns MutableStateFlow(true)
        every { settings.subtitlePreference } returns MutableStateFlow(SubtitlePreference.SPANISH)
    }

    @Test
    fun `snapshot contains configuration and key suffixes but never complete secrets`() = runTest {
        val config = controller.snapshot()
        val encoded = Json.encodeToString(config)

        assertEquals(DebridService.ALLDEBRID.name, config.debrid)
        assertTrue(config.keys.first { it.service == ApiService.TMDB.name }.configured)
        assertEquals("cret", config.keys.first { it.service == ApiService.TMDB.name }.last4)
        assertFalse(encoded.contains("tmdb-complete-secret"))
        assertFalse(encoded.contains("alldebrid-complete-secret"))
        assertTrue(encoded.contains("\"addons\":[]"))
    }

    @Test
    fun `valid update saves keys and preferences`() = runTest {
        coEvery { apiKeys.check(ApiService.TMDB, "new-key") } returns KeyCheck.Valid()

        val result = controller.update(update(keys = listOf(PhoneKeyUpdateDto(ApiService.TMDB.name, "new-key"))))

        assertTrue(result is PhoneConfigUpdateResult.Success)
        coVerify(exactly = 1) { apiKeys.set(ApiService.TMDB, "new-key") }
        coVerify(exactly = 1) { settings.setDebridService(DebridService.ALLDEBRID) }
        coVerify(exactly = 1) { settings.setLightweightUi(false) }
        coVerify(exactly = 1) { settings.setExperimentalSubtitleSync(true) }
        coVerify(exactly = 1) { settings.setAutoNextEpisode(true) }
    }

    @Test
    fun `an invalid key rejects the whole update`() = runTest {
        coEvery { apiKeys.check(ApiService.TMDB, "bad-key") } returns KeyCheck.Invalid("Invalid key")

        val result = controller.update(
            update(
                lightweightUi = true,
                keys = listOf(PhoneKeyUpdateDto(ApiService.TMDB.name, "bad-key")),
            ),
        )

        assertEquals(PhoneConfigUpdateResult.ValidationFailed(mapOf(ApiService.TMDB.name to "Invalid key")), result)
        coVerify(exactly = 0) { apiKeys.set(any(), any()) }
        coVerify(exactly = 0) { settings.setDebridService(any()) }
        coVerify(exactly = 0) { settings.setLightweightUi(any()) }
    }

    @Test
    fun `omitted keys stay unchanged without validation`() = runTest {
        val result = controller.update(update())

        assertTrue(result is PhoneConfigUpdateResult.Success)
        coVerify(exactly = 0) { apiKeys.check(any(), any()) }
        coVerify(exactly = 0) { apiKeys.set(any(), any()) }
    }

    @Test
    fun `a required key cannot be removed`() = runTest {
        val result = controller.update(
            update(keys = listOf(PhoneKeyUpdateDto(ApiService.TMDB.name, remove = true))),
        )

        assertTrue(result is PhoneConfigUpdateResult.ValidationFailed)
        coVerify(exactly = 0) { apiKeys.set(any(), any()) }
        coVerify(exactly = 0) { settings.setDebridService(any()) }
    }

    @Test
    fun `at least one source provider must remain enabled`() = runTest {
        try {
            controller.update(update(providers = emptyList()))
            fail("Expected an invalid request")
        } catch (_: IllegalArgumentException) {
            coVerify(exactly = 0) { settings.setSourceProviders(any()) }
        }
    }

    @Test
    fun `locked Ithil is hidden and cannot be enabled from phone configuration`() = runTest {
        every { ithilAccess.unlocked } returns MutableStateFlow(false)

        assertTrue(controller.snapshot().providerGroups.none { SourceProvider.ITHIL.name in it.providers })
        val result = controller.update(
            update(providers = listOf(SourceProvider.ITHIL.name, SourceProvider.TORRENTIO.name)),
        )

        assertTrue(result is PhoneConfigUpdateResult.Success)
        coVerify(exactly = 1) { settings.setSourceProviders(setOf(SourceProvider.TORRENTIO)) }
    }

    @Test
    fun `addon snapshot exposes stable metadata but not manifest values`() = runTest {
        every { apiKeys.keys } returns MutableStateFlow(
            mapOf(ApiService.AIOSTREAMS to "https://one.example/manifest.json https://two.example/manifest.json"),
        )

        val first = controller.snapshot()
        val second = controller.snapshot()
        val encoded = Json.encodeToString(first)

        assertEquals(listOf("one.example", "two.example"), first.addons.map { it.title })
        assertEquals(first.addons.map { it.id }, second.addons.map { it.id })
        assertFalse(encoded.contains("manifest.json"))
        assertFalse(encoded.contains("one.example/manifest"))
    }

    @Test
    fun `addon snapshot includes manifest name and logo metadata`() = runTest {
        val existing = StremioAddons.parse("https://one.example/manifest.json").first()
        every { apiKeys.keys } returns MutableStateFlow(mapOf(ApiService.AIOSTREAMS to existing.manifestUrl))
        coEvery { apiKeys.addonMetadata(existing.manifestUrl) } returns StremioAddonMetadata(
            name = "Local addon",
            logoUrl = "https://one.example/icon.png",
        )

        val addon = controller.snapshot().addons.single()

        assertEquals("Local addon", addon.title)
        assertEquals("https://one.example/icon.png", addon.logoUrl)
    }

    @Test
    fun `adding an addon preserves every existing manifest value`() = runTest {
        val existing = "stremio://one.example/manifest.json https://two.example/manifest.json"
        every { apiKeys.keys } returns MutableStateFlow(mapOf(ApiService.AIOSTREAMS to existing))
        coEvery { apiKeys.check(ApiService.AIOSTREAMS, "https://new.example/manifest.json") } returns KeyCheck.Valid()

        val result = controller.update(
            update().copy(
                addons = listOf(PhoneAddonUpdateDto(value = "https://new.example/manifest.json")),
            ),
        )

        assertTrue(result is PhoneConfigUpdateResult.Success)
        coVerify(exactly = 1) {
            apiKeys.set(ApiService.AIOSTREAMS, "$existing https://new.example/manifest.json")
        }
    }

    @Test
    fun `replace and remove operations leave unmodified addons intact`() = runTest {
        val first = StremioAddons.parse("https://one.example/manifest.json").first()
        val second = StremioAddons.parse("https://two.example/manifest.json").first()
        every { apiKeys.keys } returns MutableStateFlow(
            mapOf(ApiService.AIOSTREAMS to "${first.manifestUrl} ${second.manifestUrl}"),
        )
        coEvery { apiKeys.check(ApiService.AIOSTREAMS, "https://new.example/manifest.json") } returns KeyCheck.Valid()

        val result = controller.update(
            update().copy(
                addons = listOf(
                    PhoneAddonUpdateDto(id = first.id, value = "https://new.example/manifest.json"),
                    PhoneAddonUpdateDto(id = second.id, remove = true),
                ),
            ),
        )

        assertTrue(result is PhoneConfigUpdateResult.Success)
        coVerify(exactly = 1) { apiKeys.set(ApiService.AIOSTREAMS, "https://new.example/manifest.json") }
    }

    @Test
    fun `invalid addon replacement does not modify existing configuration`() = runTest {
        val existing = StremioAddons.parse("https://one.example/manifest.json").first()
        every { apiKeys.keys } returns MutableStateFlow(mapOf(ApiService.AIOSTREAMS to existing.manifestUrl))
        coEvery { apiKeys.check(ApiService.AIOSTREAMS, "https://bad.example/manifest.json") } returns KeyCheck.Invalid("Offline")

        val result = controller.update(
            update().copy(
                addons = listOf(PhoneAddonUpdateDto(id = existing.id, value = "https://bad.example/manifest.json")),
            ),
        )

        assertEquals(PhoneConfigUpdateResult.ValidationFailed(mapOf("addon:0" to "Offline")), result)
        coVerify(exactly = 0) { apiKeys.set(any(), any()) }
    }

    @Test
    fun `testing an addon returns its detail without changing configuration`() = runTest {
        val existing = StremioAddons.parse("https://one.example/manifest.json").first()
        every { apiKeys.keys } returns MutableStateFlow(mapOf(ApiService.AIOSTREAMS to existing.manifestUrl))
        coEvery { apiKeys.check(ApiService.AIOSTREAMS, existing.manifestUrl) } returns KeyCheck.Valid("12 sources")

        val result = controller.update(
            update().copy(addons = listOf(PhoneAddonUpdateDto(id = existing.id, test = true))),
        )

        assertTrue(result is PhoneConfigUpdateResult.Success)
        assertEquals("12 sources", (result as PhoneConfigUpdateResult.Success).config.addons.single().detail)
        coVerify(exactly = 0) { apiKeys.set(any(), any()) }
        coVerify(exactly = 0) { settings.setDebridService(any()) }
        coVerify(exactly = 0) { settings.setSourceProviders(any()) }
    }

    @Test
    fun `setup snapshot includes playback choices and required completion`() = runTest {
        val config = controller.snapshot(PhoneConfigMode.SETUP)

        assertEquals(SetupAudioPreference.SPANISH.name, config.setup?.audio)
        assertEquals(SubtitlePreference.SPANISH.name, config.setup?.subtitles)
        assertTrue(config.setup?.complete == true)
        assertTrue(config.keys.first { it.service == ApiService.ALLDEBRID.name }.required)
        assertTrue(config.keys.none { it.service == ApiService.AIOSTREAMS.name })
    }

    @Test
    fun `setup update saves language and playback preferences without changing the running language`() = runTest {
        val result = controller.update(
            update().copy(language = "ES", audio = "ORIGINAL", subtitles = "OFF"),
            PhoneConfigMode.SETUP,
        )

        assertTrue(result is PhoneConfigUpdateResult.Success)
        assertEquals("es", (result as PhoneConfigUpdateResult.Success).config.language)
        coVerify(exactly = 1) { settings.setLanguage(AppLanguage.ES) }
        coVerify(exactly = 1) { settings.setSpanishFirst(false) }
        coVerify(exactly = 1) { settings.setSpanishOnlySources(false) }
        coVerify(exactly = 1) { settings.setSubtitlePreference(SubtitlePreference.OFF) }
    }

    @Test
    fun `setup cannot remove the selected debrid key`() = runTest {
        val result = controller.update(
            update(keys = listOf(PhoneKeyUpdateDto(ApiService.ALLDEBRID.name, remove = true))),
            PhoneConfigMode.SETUP,
        )

        assertTrue(result is PhoneConfigUpdateResult.ValidationFailed)
        coVerify(exactly = 0) { apiKeys.set(any(), any()) }
    }

    @Test
    fun `edit mode rejects setup-only fields`() = runTest {
        try {
            controller.update(update().copy(language = "ES"))
            fail("Expected an invalid request")
        } catch (_: IllegalArgumentException) {
            coVerify(exactly = 0) { settings.setLanguage(any()) }
        }
    }

    private fun update(
        providers: List<String> = SourceProvider.entries.map { it.name },
        lightweightUi: Boolean = false,
        keys: List<PhoneKeyUpdateDto> = emptyList(),
    ) = PhoneConfigUpdateDto(
        debrid = DebridService.ALLDEBRID.name,
        providers = providers,
        lightweightUi = lightweightUi,
        experimentalSubtitleSync = true,
        autoNextEpisode = true,
        keys = keys,
    )

    private class FakeTexts : Texts {
        override fun get(id: Int, vararg formatArgs: Any): String = id.toString()
        override fun quantity(id: Int, count: Int, vararg formatArgs: Any): String = id.toString()
    }
}
