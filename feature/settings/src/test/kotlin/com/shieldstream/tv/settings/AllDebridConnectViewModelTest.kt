package com.shieldstream.tv.settings

import com.shieldstream.tv.domain.AllDebridAuthPoll
import com.shieldstream.tv.domain.AllDebridAuthRepository
import com.shieldstream.tv.domain.AllDebridPin
import com.shieldstream.tv.domain.ApiKeysRepository
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.strings.Texts
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AllDebridConnectViewModelTest {
    private val pin = AllDebridPin("ABCD", "check", "https://alldebrid.com/pin/?pin=ABCD", 600)
    private val auth = FakeAllDebridAuthRepository(pin)
    private val apiKeys = mockk<ApiKeysRepository>(relaxed = true)
    private val viewModel = AllDebridConnectViewModel(auth, apiKeys, mockk<Texts>(relaxed = true))

    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMainDispatcher() {
        viewModel.stop()
        Dispatchers.resetMain()
    }

    @Test
    fun `a successful authorization saves the returned key and notifies the screen`() = runTest {
        auth.pollResult = AllDebridAuthPoll.Connected("returned-key")
        var connected = false

        viewModel.start { connected = true }

        coVerify(exactly = 1) { apiKeys.set(ApiService.ALLDEBRID, "returned-key") }
        assertTrue(connected)
        assertEquals(AllDebridConnectUiState.Connected, viewModel.uiState.value)
    }

    @Test
    fun `stopping the panel cancels polling`() = runTest {
        viewModel.start {}
        viewModel.stop()

        assertEquals(1, auth.pollCount)
        assertTrue(viewModel.uiState.value is AllDebridConnectUiState.ShowPin)
    }

    private class FakeAllDebridAuthRepository(
        private val pin: AllDebridPin,
    ) : AllDebridAuthRepository {
        var pollResult: AllDebridAuthPoll = AllDebridAuthPoll.Pending
        var pollCount = 0

        override suspend fun requestPin(): Result<AllDebridPin> = Result.success(pin)

        override suspend fun pollPin(pin: AllDebridPin): AllDebridAuthPoll {
            pollCount++
            return pollResult
        }

        override suspend fun unlock(url: String) = error("unused")
    }
}
