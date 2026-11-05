package com.shieldstream.tv.data.repository

import com.shieldstream.tv.domain.AllDebridAuthPoll
import com.shieldstream.tv.network.api.AllDebridApi
import com.shieldstream.tv.network.api.AllDebridErrorDto
import com.shieldstream.tv.network.api.AllDebridPinCheckDto
import com.shieldstream.tv.network.api.AllDebridPinDto
import com.shieldstream.tv.network.api.AllDebridResponse
import com.shieldstream.tv.strings.Texts
import io.mockk.coEvery
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AllDebridAuthRepositoryImplTest {
    private val api = mockk<AllDebridApi>()
    private val repository = AllDebridAuthRepositoryImpl(api, mockk<Texts>(relaxed = true))
    private val pin = com.shieldstream.tv.domain.AllDebridPin(
        pin = "ABCD",
        check = "check-token",
        verificationUrl = "https://alldebrid.com/pin/?pin=ABCD",
        expiresInSeconds = 600,
    )

    @Test
    fun `request pin maps the verification URL and polling token`() = runTest {
        coEvery { api.getPin() } returns AllDebridResponse(
            status = "success",
            data = AllDebridPinDto(
                pin = pin.pin,
                check = pin.check,
                expires_in = pin.expiresInSeconds,
                user_url = pin.verificationUrl,
            ),
        )

        assertEquals(pin, repository.requestPin().getOrThrow())
    }

    @Test
    fun `an inactive pin is pending`() = runTest {
        coEvery { api.checkPin(pin.pin, pin.check) } returns AllDebridResponse(
            status = "success",
            data = AllDebridPinCheckDto(activated = false, expires_in = 580),
        )

        assertEquals(AllDebridAuthPoll.Pending, repository.pollPin(pin))
    }

    @Test
    fun `an activated pin returns its API key`() = runTest {
        coEvery { api.checkPin(pin.pin, pin.check) } returns AllDebridResponse(
            status = "success",
            data = AllDebridPinCheckDto(activated = true, expires_in = 570, apikey = "returned-key"),
        )

        assertEquals(AllDebridAuthPoll.Connected("returned-key"), repository.pollPin(pin))
    }

    @Test
    fun `an expired pin ends polling`() = runTest {
        coEvery { api.checkPin(pin.pin, pin.check) } returns AllDebridResponse<AllDebridPinCheckDto>(
            status = "error",
            error = AllDebridErrorDto(code = "PIN_EXPIRED", message = "Expired"),
        )

        assertEquals(AllDebridAuthPoll.Expired, repository.pollPin(pin))
    }

    @Test
    fun `a network failure keeps polling until the local deadline`() = runTest {
        coEvery { api.checkPin(pin.pin, pin.check) } throws IOException("offline")

        assertEquals(AllDebridAuthPoll.Pending, repository.pollPin(pin))
    }

    @Test
    fun `an activated response without a key fails instead of saving a blank token`() = runTest {
        coEvery { api.checkPin(pin.pin, pin.check) } returns AllDebridResponse(
            status = "success",
            data = AllDebridPinCheckDto(activated = true, expires_in = 570),
        )

        assertTrue(repository.pollPin(pin) is AllDebridAuthPoll.Failed)
    }
}
