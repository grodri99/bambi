package com.shieldstream.tv.data.repository

import com.shieldstream.tv.domain.ApiKeysRepository
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.network.api.AllDebridApi
import com.shieldstream.tv.network.api.AllDebridFileDto
import com.shieldstream.tv.network.api.AllDebridFilesDataDto
import com.shieldstream.tv.network.api.AllDebridMagnetFilesDto
import com.shieldstream.tv.network.api.AllDebridResponse
import com.shieldstream.tv.network.api.AllDebridStatusDataDto
import com.shieldstream.tv.network.api.AllDebridStatusDto
import com.shieldstream.tv.network.api.AllDebridUploadDataDto
import com.shieldstream.tv.network.api.AllDebridUploadMagnetDto
import com.shieldstream.tv.strings.Texts
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AllDebridRepositoryImplTest {
    private val api = mockk<AllDebridApi>()
    private val keys = mockk<ApiKeysRepository> {
        every { keys } returns MutableStateFlow(mapOf(ApiService.ALLDEBRID to "user-key"))
    }
    private val repository = AllDebridRepositoryImpl(api, keys, mockk<Texts>(relaxed = true))

    @Test
    fun `upload sends the magnet with bearer authentication`() = runTest {
        coEvery { api.uploadMagnet("Bearer user-key", "magnet:?xt=urn:btih:hash") } returns AllDebridResponse(
            status = "success",
            data = AllDebridUploadDataDto(
                magnets = listOf(AllDebridUploadMagnetDto(id = 123, ready = true)),
            ),
        )

        assertEquals("123", repository.addMagnet("magnet:?xt=urn:btih:hash"))
    }

    @Test
    fun `ready magnet flattens nested files and keeps their individual links`() = runTest {
        coEvery { api.magnetStatus("Bearer user-key", "123") } returns AllDebridResponse(
            status = "success",
            data = AllDebridStatusDataDto(
                magnets = listOf(AllDebridStatusDto(id = 123, status = "Ready", statusCode = 4)),
            ),
        )
        coEvery { api.magnetFiles("Bearer user-key", "123") } returns AllDebridResponse(
            status = "success",
            data = AllDebridFilesDataDto(
                magnets = listOf(
                    AllDebridMagnetFilesDto(
                        files = listOf(
                            AllDebridFileDto(
                                n = "Season 1",
                                e = listOf(
                                    AllDebridFileDto(n = "Show.S01E01.mkv", s = 100, l = "https://files.test/e1"),
                                    AllDebridFileDto(n = "Show.S01E02.mkv", s = 200, l = "https://files.test/e2"),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val info = repository.torrentInfo("123")

        assertEquals(listOf("Season 1/Show.S01E01.mkv", "Season 1/Show.S01E02.mkv"), info.files.map { it.name })
        assertEquals("https://files.test/e2", info.linkFor(info.files[1]))
        assertTrue(info.files.all { it.selected })
    }

    @Test
    fun `processing magnet does not request file links`() = runTest {
        coEvery { api.magnetStatus("Bearer user-key", "123") } returns AllDebridResponse(
            status = "success",
            data = AllDebridStatusDataDto(
                magnets = listOf(AllDebridStatusDto(id = 123, status = "Downloading", statusCode = 1)),
            ),
        )

        val info = repository.torrentInfo("123")

        assertEquals("Downloading", info.status)
        assertTrue(info.files.isEmpty())
        coVerify(exactly = 0) { api.magnetFiles(any(), any()) }
    }
}
