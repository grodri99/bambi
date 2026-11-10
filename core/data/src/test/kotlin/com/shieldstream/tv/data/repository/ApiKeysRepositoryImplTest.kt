package com.shieldstream.tv.data.repository

import com.shieldstream.tv.data.security.TokenStore
import com.shieldstream.tv.data.subtitles.SubtitleHttp
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.domain.KeyCheck
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.network.api.AllDebridApi
import com.shieldstream.tv.network.api.AllDebridErrorDto
import com.shieldstream.tv.network.api.AllDebridResponse
import com.shieldstream.tv.network.api.AllDebridUserDataDto
import com.shieldstream.tv.network.api.AllDebridUserDto
import com.shieldstream.tv.network.api.RealDebridApi
import com.shieldstream.tv.network.api.TmdbApi
import com.shieldstream.tv.network.api.TorrentioApi
import com.shieldstream.tv.strings.Texts
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiKeysRepositoryImplTest {
    private val allDebrid = mockk<AllDebridApi>()
    private val torrentio = mockk<TorrentioApi>()
    private val texts = mockk<Texts> {
        every { get(any(), *anyVararg()) } returns "account details"
    }
    private val repository = ApiKeysRepositoryImpl(
        store = mockk<TokenStore>(relaxed = true),
        settings = mockk<SettingsRepository>(relaxed = true),
        tmdb = mockk<TmdbApi>(),
        realDebrid = mockk<RealDebridApi>(),
        allDebrid = allDebrid,
        http = mockk<SubtitleHttp>(),
        torrentioApi = torrentio,
        texts = texts,
    )

    @Test
    fun `addon metadata reads name and logo once per manifest`() = runTest {
        coEvery { torrentio.raw("https://one.example/manifest.json") } returns
            """{"name":"Local addon","logo":"/icon.png"}""".toResponseBody()

        val first = repository.addonMetadata("https://one.example/manifest.json")
        val second = repository.addonMetadata("https://one.example/manifest.json")

        assertEquals("Local addon", first?.name)
        assertEquals("https://one.example/icon.png", first?.logoUrl)
        assertEquals(first, second)
        coVerify(exactly = 1) { torrentio.raw("https://one.example/manifest.json") }
    }

    @Test
    fun `AllDebrid validation uses its account API without Torrentio`() = runTest {
        coEvery { allDebrid.user("Bearer valid-key") } returns AllDebridResponse(
            status = "success",
            data = AllDebridUserDataDto(
                AllDebridUserDto(username = "user", isPremium = true, premiumUntil = 4_000_000_000),
            ),
        )

        val result = repository.check(ApiService.ALLDEBRID, " valid-key ")

        assertEquals(KeyCheck.Valid("account details"), result)
        coVerify(exactly = 1) { allDebrid.user("Bearer valid-key") }
        coVerify(exactly = 0) { torrentio.streamsWithConfig(any(), any(), any()) }
    }

    @Test
    fun `AllDebrid API rejection is reported directly`() = runTest {
        coEvery { allDebrid.user("Bearer bad-key") } returns AllDebridResponse(
            status = "error",
            error = AllDebridErrorDto(code = "AUTH_BAD_APIKEY", message = "Invalid AllDebrid key"),
        )

        val result = repository.check(ApiService.ALLDEBRID, "bad-key")

        assertTrue(result is KeyCheck.Invalid)
        assertEquals("Invalid AllDebrid key", (result as KeyCheck.Invalid).message)
        coVerify(exactly = 0) { torrentio.streamsWithConfig(any(), any(), any()) }
    }
}
