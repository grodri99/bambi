package com.shieldstream.tv.data.repository

import com.shieldstream.tv.data.security.TokenStore
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.domain.DebridService
import com.shieldstream.tv.domain.IthilAccessRepository
import com.shieldstream.tv.domain.IthilLibraryRepository
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.domain.SourceProvider
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.Profile
import com.shieldstream.tv.network.api.TmdbApi
import com.shieldstream.tv.network.api.TorrentioApi
import com.shieldstream.tv.strings.Texts
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CatalogRepositoryStreamsTest {
    private val torrentio = mockk<TorrentioApi>()
    private val settings = mockk<SettingsRepository> {
        every { sourceProviders } returns MutableStateFlow(setOf(SourceProvider.TORRENTIO, SourceProvider.KNABEN))
        every { debridService } returns MutableStateFlow(DebridService.ALLDEBRID)
        every { spanishFirst } returns MutableStateFlow(false)
    }
    private val store = mockk<TokenStore> {
        every { getSecret(any()) } answers {
            if (firstArg<String>() == TokenStore.keyNameFor(ApiService.ALLDEBRID)) "test-key" else null
        }
    }
    private val profiles = mockk<ProfileRepository> {
        every { active } returns MutableStateFlow(Profile(id = "test", name = "Test", avatar = 0))
    }
    private val texts = mockk<Texts> {
        every { get(any(), *anyVararg()) } answers { "text-${firstArg<Int>()}" }
    }
    private val repository = CatalogRepositoryImpl(
        tmdb = mockk<TmdbApi>(),
        torrentio = torrentio,
        extras = mockk<HomeExtrasSource>(),
        store = store,
        settings = settings,
        profiles = profiles,
        texts = texts,
        ithil = mockk<IthilLibraryRepository>(),
        ithilAccess = mockk<IthilAccessRepository>(),
    )

    @Test
    fun `AllDebrid scraper result survives a Torrentio failure`() = runTest {
        coEvery { torrentio.streamsWithConfig(any(), "movie", "tt1234567") } throws IOException("Torrentio unavailable")
        coEvery { torrentio.postJson("https://api.knaben.org/v1", any()) } returns """
            {"hits":[{"title":"Example Movie 2024 1080p","hash":"0123456789abcdef0123456789abcdef01234567","bytes":1073741824,"seeders":12}]}
        """.trimIndent().toResponseBody("application/json".toMediaType())

        val states = repository.getStreams(item).toList()
        val result = states.last()

        assertEquals(listOf("0123456789abcdef0123456789abcdef01234567"), result.sources.map { it.infoHash })
        assertEquals("KNABEN:0123456789abcdef0123456789abcdef01234567:0", result.sources.single().id)
        assertTrue(result.pending.isEmpty())
        coVerify(exactly = 1) { torrentio.streamsWithConfig(any(), "movie", "tt1234567") }
        coVerify(exactly = 1) { torrentio.postJson("https://api.knaben.org/v1", any()) }
    }

    private val item = CatalogItem(
        id = "not-numeric",
        imdbId = "tt1234567",
        type = MediaType.MOVIE,
        title = "Example Movie",
        overview = "",
        posterUrl = null,
        backdropUrl = null,
        year = 2024,
        rating = null,
    )
}
