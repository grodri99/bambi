package com.shieldstream.tv.data.repository

import com.shieldstream.tv.data.security.TokenStore
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.domain.DebridService
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.domain.SourceProvider
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.Profile
import com.shieldstream.tv.model.StreamSource
import com.shieldstream.tv.network.api.TmdbApi
import com.shieldstream.tv.network.api.TorrentioApi
import com.shieldstream.tv.network.dto.TorrentioStreamDto
import com.shieldstream.tv.network.dto.TorrentioStreamResponse
import com.shieldstream.tv.strings.Texts
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Comportamiento de los buscadores de fuentes visto desde fuera ([CatalogRepositoryImpl.getStreams]): fija lo que
 * hace hoy cada uno para poder reorganizar el código sin cambiarlo.
 */
@RunWith(RobolectricTestRunner::class)
class StreamSearchBehaviorTest {
    private val torrentio = mockk<TorrentioApi>()
    private val providers = MutableStateFlow(setOf<SourceProvider>())
    private val debrid = MutableStateFlow(DebridService.REAL_DEBRID)
    private val settings = mockk<SettingsRepository> {
        every { sourceProviders } returns providers
        every { debridService } returns debrid
        every { spanishFirst } returns MutableStateFlow(false)
    }
    private val secrets = mutableMapOf<String, String>()
    private val store = mockk<TokenStore> {
        every { getSecret(any()) } answers { secrets[firstArg<String>()] }
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
    )

    private suspend fun search(item: CatalogItem, episode: Episode? = null): List<StreamSource> =
        repository.getStreams(item, episode).toList().last().sources

    @Test
    fun `pirate bay accepts season packs only when the app can pick the episode`() = runTest {
        providers.value = setOf(SourceProvider.THEPIRATEBAY)
        val results = """
            [
              {"id":"1","name":"Example Show S01E02 1080p WEB","info_hash":"${hash('a')}","size":"1073741824","seeders":"50"},
              {"id":"2","name":"Example Show S01 Complete 1080p","info_hash":"${hash('b')}","size":"8589934592","seeders":"30"},
              {"id":"3","name":"Other Show S01E02 1080p","info_hash":"${hash('c')}","size":"1073741824","seeders":"90"},
              {"id":"0","name":"No results returned","info_hash":"${"0".repeat(40)}","size":"0","seeders":"0"}
            ]
        """.trimIndent()
        // Una respuesta nueva en cada consulta (el cuerpo solo se puede leer una vez).
        coEvery { torrentio.raw(any()) } coAnswers { results.json() }

        debrid.value = DebridService.ALLDEBRID
        val native = search(show, episode)
        assertEquals(listOf(hash('a'), hash('b')), native.map { it.infoHash })
        assertEquals("THEPIRATEBAY:${hash('a')}:0", native.first().id)
        assertTrue(native.all { it.season == 1 && it.episode == 2 })

        debrid.value = DebridService.PREMIUMIZE
        assertEquals(listOf(hash('a')), search(show, episode).map { it.infoHash })
    }

    @Test
    fun `knaben movie search needs the title followed by the year`() = runTest {
        providers.value = setOf(SourceProvider.KNABEN)
        coEvery { torrentio.postJson("https://api.knaben.org/v1", any()) } returns """
            {"hits":[
              {"title":"Example Movie 2024 1080p","hash":"${hash('a')}","bytes":2147483648,"seeders":12},
              {"title":"Example Movie 1080p","hash":"${hash('b')}","bytes":2147483648,"seeders":40},
              {"title":"Example Movie Returns 2024 1080p","hash":"${hash('c')}","bytes":2147483648,"seeders":40},
              {"title":"Example Movie 2024 720p","hash":"","magnetUrl":"magnet:?xt=urn:btih:${hash('d')}&dn=x","bytes":1073741824,"seeders":3}
            ]}
        """.trimIndent().json()

        val sources = search(movie)

        assertEquals(listOf(hash('a'), hash('d')), sources.map { it.infoHash })
        assertEquals(2L * 1024 * 1024 * 1024, sources.first().sizeBytes)
        assertEquals(12, sources.first().seeders)
    }

    @Test
    fun `torrentio results keep name size seeders languages and file index`() = runTest {
        providers.value = setOf(SourceProvider.TORRENTIO)
        coEvery { torrentio.streams("movie", "tt1234567") } returns TorrentioStreamResponse(
            listOf(
                TorrentioStreamDto(
                    name = "Torrentio\n4k HDR",
                    title = "Example.Movie.2024.2160p.HDR.x265\n👤 25 💾 12.5 GB ⚙️ ThePirateBay\n🇪🇸 / 🇬🇧",
                    infoHash = hash('d'),
                    fileIdx = 3,
                ),
                TorrentioStreamDto(name = "Torrentio\n4k", title = "Example.Movie.2024.2160p.x266\n👤 9", infoHash = hash('e')),
            ),
        )

        val source = search(movie).single()

        assertEquals("TORRENTIO:${hash('d')}:3", source.id)
        assertEquals("Example.Movie.2024.2160p.HDR.x265", source.title)
        assertEquals((12.5 * 1024 * 1024 * 1024).toLong(), source.sizeBytes)
        assertEquals(25, source.seeders)
        assertEquals(listOf("ES", "EN"), source.languages)
        assertEquals(3, source.fileIdx)
        assertFalse(source.isCached)
    }

    @Test
    fun `torrentio through another debrid uses its resolve links`() = runTest {
        providers.value = setOf(SourceProvider.TORRENTIO)
        debrid.value = DebridService.PREMIUMIZE
        secrets[TokenStore.keyNameFor(ApiService.PREMIUMIZE)] = "pm-key"
        val link = "https://torrentio.strem.fun/resolve/premiumize/pm-key/${hash('e')}/null/0/Example.Movie.mkv"
        coEvery { torrentio.streamsWithConfig(any(), "movie", "tt1234567") } returns TorrentioStreamResponse(
            listOf(
                TorrentioStreamDto(name = "[PM+] Torrentio\n1080p", title = "Example.Movie.2024.1080p\n👤 5 💾 1.5 GB", url = link),
                TorrentioStreamDto(name = "[PM download] Torrentio\n720p", title = "Example.Movie.2024.720p\n👤 2", url = "https://example.com/x"),
            ),
        )

        val source = search(movie).single()

        assertEquals(hash('e'), source.infoHash)
        assertEquals(link, source.resolveUrl)
        assertEquals("Premiumize", source.debrid)
        assertTrue(source.isCached)
    }

    @Test
    fun `comet is configured with the active debrid and keeps direct links`() = runTest {
        providers.value = setOf(SourceProvider.COMET)
        debrid.value = DebridService.TORBOX
        secrets[TokenStore.keyNameFor(ApiService.TORBOX)] = "tb-key"
        val url = slot<String>()
        coEvery { torrentio.streamsAt(capture(url)) } returns TorrentioStreamResponse(
            listOf(
                TorrentioStreamDto(name = "[TB⚡] Comet 1080p", title = "Example.Movie.2024.1080p.WEB\n💾 2 GB", url = "https://comet.elfhosted.com/playback/a"),
                TorrentioStreamDto(name = "[TB⬇️] Comet 720p", title = "Example.Movie.2024.720p.WEB\n💾 1 GB", url = "https://comet.elfhosted.com/playback/b"),
            ),
        )

        val sources = search(movie)

        assertTrue(url.captured.startsWith("https://comet.elfhosted.com/"))
        assertTrue(url.captured.endsWith("/stream/movie/tt1234567.json"))
        val config = String(
            android.util.Base64.decode(url.captured.removePrefix("https://comet.elfhosted.com/").substringBefore("/stream/"), android.util.Base64.DEFAULT),
        )
        assertTrue(config, config.contains("\"service\":\"torbox\"") && config.contains("\"apiKey\":\"tb-key\""))
        assertEquals(listOf("https://comet.elfhosted.com/playback/a", "https://comet.elfhosted.com/playback/b"), sources.map { it.resolveUrl })
        assertEquals(listOf(true, false), sources.map { it.isCached })
        assertTrue(sources.all { it.debrid == "Comet" })
    }

    @Test
    fun `comet without a key for the active debrid gives no sources`() = runTest {
        providers.value = setOf(SourceProvider.COMET)
        debrid.value = DebridService.TORBOX

        assertTrue(search(movie).isEmpty())
    }

    private fun hash(c: Char) = c.toString().repeat(40)

    private fun String.json() = toResponseBody("application/json".toMediaType())

    private val movie = CatalogItem(
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

    private val show = movie.copy(id = "not-numeric-tv", imdbId = "tt7654321", type = MediaType.TV, title = "Example Show", year = 2023)

    private val episode = Episode(
        season = 1,
        number = 2,
        title = "Episode 2",
        overview = "",
        stillUrl = null,
        runtimeMinutes = 45,
        airDate = "2023-01-08",
    )
}
