package com.shieldstream.tv.data.repository

import com.shieldstream.tv.domain.ApiKeysRepository
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.domain.DebridLink
import com.shieldstream.tv.domain.KeyCheck
import com.shieldstream.tv.network.api.AllDebridApi
import com.shieldstream.tv.network.api.AllDebridFilesDataDto
import com.shieldstream.tv.network.api.AllDebridMessageDto
import com.shieldstream.tv.network.api.AllDebridPinCheckDto
import com.shieldstream.tv.network.api.AllDebridPinDto
import com.shieldstream.tv.network.api.AllDebridResponse
import com.shieldstream.tv.network.api.AllDebridStatusDataDto
import com.shieldstream.tv.network.api.AllDebridUploadDataDto
import com.shieldstream.tv.network.api.AllDebridUserDataDto
import com.shieldstream.tv.network.api.AllDebridUnlockDto
import com.shieldstream.tv.strings.Texts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AllDebridLinkResolverImplTest {
    private val api = FakeApi()
    private val keys = FakeKeys()

    @Test
    fun unlockUsesBambiAndTheUsersKey() = runTest {
        val resolver = AllDebridAuthRepositoryImpl(api, FakeTexts, keys)

        val result = resolver.unlock("https://hoster.test/?abc")

        assertEquals("Bambi", api.agent)
        assertEquals("user-key", api.key)
        assertEquals("https://hoster.test/?abc", api.link)
        assertEquals(DebridLink.Ready("https://cdn.test/video"), result)
    }

    @Test
    fun apiErrorBecomesSourceFailureAndMissingKeySkipsApi() = runTest {
        api.response = AllDebridResponse(status = "error", error = com.shieldstream.tv.network.api.AllDebridErrorDto(message = "host unavailable"))
        val resolver = AllDebridAuthRepositoryImpl(api, FakeTexts, keys)
        assertEquals("host unavailable", (resolver.unlock("https://hoster.test/?abc") as DebridLink.Problem).message)

        keys.values.value = emptyMap()
        val calls = api.calls
        resolver.unlock("https://hoster.test/?abc")
        assertEquals(calls, api.calls)
    }

    private class FakeApi : AllDebridApi {
        var response = AllDebridResponse(status = "success", data = AllDebridUnlockDto("https://cdn.test/video"))
        var agent = ""
        var key = ""
        var link = ""
        var calls = 0

        override suspend fun unlock(agent: String, apiKey: String, link: String): AllDebridResponse<AllDebridUnlockDto> {
            calls++
            this.agent = agent
            this.key = apiKey
            this.link = link
            return response
        }

        override suspend fun getPin(): AllDebridResponse<AllDebridPinDto> = error("unused")
        override suspend fun checkPin(pin: String, check: String): AllDebridResponse<AllDebridPinCheckDto> = error("unused")
        override suspend fun user(authorization: String): AllDebridResponse<AllDebridUserDataDto> = error("unused")
        override suspend fun uploadMagnet(authorization: String, magnet: String): AllDebridResponse<AllDebridUploadDataDto> = error("unused")
        override suspend fun magnetStatus(authorization: String, id: String): AllDebridResponse<AllDebridStatusDataDto> = error("unused")
        override suspend fun magnetFiles(authorization: String, id: String): AllDebridResponse<AllDebridFilesDataDto> = error("unused")
        override suspend fun deleteMagnet(authorization: String, id: String): AllDebridResponse<AllDebridMessageDto> = error("unused")
    }

    private class FakeKeys : ApiKeysRepository {
        val values = MutableStateFlow(mapOf<ApiService, String?>(ApiService.ALLDEBRID to "user-key"))
        override val keys: StateFlow<Map<ApiService, String?>> = values
        override suspend fun set(service: ApiService, value: String?) = Unit
        override suspend fun check(service: ApiService, value: String): KeyCheck = error("unused")
    }

    private object FakeTexts : Texts {
        override fun get(id: Int, vararg formatArgs: Any): String = "error-$id"
        override fun quantity(id: Int, count: Int, vararg formatArgs: Any): String = "error-$id"
    }
}
