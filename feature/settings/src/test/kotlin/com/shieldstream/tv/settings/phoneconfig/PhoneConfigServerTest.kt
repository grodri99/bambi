package com.shieldstream.tv.settings.phoneconfig

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.domain.DebridService
import com.shieldstream.tv.domain.SourceProvider
import com.shieldstream.tv.strings.Texts
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PhoneConfigServerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val controller = mockk<PhoneConfigController>()
    private val server = PhoneConfigServer(context, controller, Json { ignoreUnknownKeys = true }, mockk<Texts>(relaxed = true))
    private val client = OkHttpClient()

    @After
    fun stopServer() {
        server.stop()
    }

    @Test
    fun `page is public but configuration requires the QR bearer token`() {
        coEvery { controller.snapshot() } returns config()
        val connection = server.start("127.0.0.1")
        val token = connection.url.substringAfter('#')

        client.newCall(Request.Builder().url(connection.displayUrl).build()).execute().use { response ->
            assertEquals(200, response.code)
            val html = response.body!!.string()
            assertTrue(html.contains("Franklix"))
            assertTrue(html.contains("src=\"/logo.png\""))
        }
        client.newCall(Request.Builder().url("${connection.displayUrl}/logo.png").build()).execute().use { response ->
            assertEquals(200, response.code)
            assertEquals("image/png", response.header("Content-Type"))
            assertTrue(response.body!!.bytes().isNotEmpty())
        }
        client.newCall(Request.Builder().url("${connection.displayUrl}/api/config").build()).execute().use { response ->
            assertEquals(401, response.code)
        }
        client.newCall(
            Request.Builder()
                .url("${connection.displayUrl}/api/config")
                .header("Authorization", "Bearer $token")
                .build(),
        ).execute().use { response ->
            assertEquals(200, response.code)
            val body = response.body!!.string()
            assertTrue(body.contains("TMDB"))
            assertFalse(body.contains("complete-secret"))
            assertEquals("no-store", response.header("Cache-Control"))
        }
    }

    @Test
    fun `authorized put accepts a JSON configuration update`() {
        coEvery { controller.update(any()) } returns PhoneConfigUpdateResult.Success(config())
        val connection = server.start("127.0.0.1")
        val token = connection.url.substringAfter('#')
        val body = """{"debrid":"ALLDEBRID","providers":["TORRENTIO"],"lightweightUi":false,"experimentalSubtitleSync":true,"autoNextEpisode":true,"keys":[]}"""
            .toRequestBody("application/json".toMediaType())

        client.newCall(
            Request.Builder()
                .url("${connection.displayUrl}/api/config")
                .header("Authorization", "Bearer $token")
                .put(body)
                .build(),
        ).execute().use { response ->
            val responseBody = response.body!!.string()
            assertEquals(responseBody, 200, response.code)
            assertTrue(responseBody.contains("debridOptions"))
        }
        assertTrue(server.lastSavedConfig.value != null)
    }

    @Test
    fun `setup get publishes completion state for the TV finish action`() {
        coEvery { controller.snapshot(PhoneConfigMode.SETUP) } returns config().copy(
            setup = PhoneSetupDto(audio = "ORIGINAL", subtitles = "OFF", complete = true),
        )
        val connection = server.start("127.0.0.1", PhoneConfigMode.SETUP)
        val token = connection.url.substringAfter('#')

        client.newCall(
            Request.Builder()
                .url("${connection.displayUrl}/api/config")
                .header("Authorization", "Bearer $token")
                .build(),
        ).execute().use { response ->
            assertEquals(200, response.code)
        }
        assertTrue(server.lastConfig.value?.setup?.complete == true)
        assertTrue(server.lastSavedConfig.value == null)
    }

    private fun config() = PhoneConfigDto(
        language = "en",
        debrid = DebridService.ALLDEBRID.name,
        debridOptions = listOf(PhoneOptionDto(DebridService.ALLDEBRID.name, "AllDebrid")),
        keys = listOf(
            PhoneKeyDto(
                service = ApiService.TMDB.name,
                title = "TMDB",
                keyLabel = "API key",
                helpUrl = ApiService.TMDB.url,
                configured = true,
                last4 = "cret",
                required = true,
            ),
        ),
        providerGroups = listOf(PhoneProviderGroupDto("Torrentio", listOf(SourceProvider.TORRENTIO.name), true)),
        preferences = PhonePreferencesDto(lightweightUi = false, experimentalSubtitleSync = true, autoNextEpisode = true),
        addons = emptyList(),
    )
}
