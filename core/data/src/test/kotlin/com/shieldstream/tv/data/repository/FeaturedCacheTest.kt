package com.shieldstream.tv.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shieldstream.tv.data.security.TokenStore
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.model.AppLanguage
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.FeaturedBadge
import com.shieldstream.tv.model.FeaturedItem
import com.shieldstream.tv.model.HomeFilter
import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.Profile
import com.shieldstream.tv.network.api.TmdbApi
import com.shieldstream.tv.network.api.TorrentioApi
import com.shieldstream.tv.strings.Texts
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * La portada pasa por la caché de memoria y de disco de `CatalogRepositoryImpl`: el `trailerKey` tiene que
 * sobrevivir a las dos, no solo a `HomeDiskCache`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FeaturedCacheTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val cache = HomeDiskCache(context)

    @Before
    fun clearCache() {
        File(context.filesDir, "home_cache").deleteRecursively()
        Lang.current = AppLanguage.ES
    }

    @After
    fun restoreLanguage() {
        Lang.current = AppLanguage.ES
    }

    @Test
    fun `featured trailer key survives a reload from disk`() = runTest {
        val extras = mockk<HomeExtrasSource> {
            coEvery { featured(HomeFilter.ALL) } returns listOf(featured("trailer-abc"))
        }
        val first = repository(extras)

        assertEquals(listOf("trailer-abc"), first.getFeatured(HomeFilter.ALL).map { it.trailerKey })
        coVerify(exactly = 1) { extras.featured(HomeFilter.ALL) }

        // Otro proceso/repositorio: sin caché en memoria, solo el disco.
        val offlineExtras = mockk<HomeExtrasSource> {
            coEvery { featured(any()) } throws IllegalStateException("sin red")
        }
        val second = repository(offlineExtras)

        assertEquals(listOf("trailer-abc"), second.getFeatured(HomeFilter.ALL).map { it.trailerKey })
        coVerify(exactly = 0) { offlineExtras.featured(any()) }
    }

    @Test
    fun `featured without trailer also survives the disk cache`() = runTest {
        val extras = mockk<HomeExtrasSource> {
            coEvery { featured(HomeFilter.MOVIES) } returns listOf(featured(null))
        }
        repository(extras).getFeatured(HomeFilter.MOVIES)

        val offlineExtras = mockk<HomeExtrasSource> { coEvery { featured(any()) } throws IllegalStateException("sin red") }
        val restored = repository(offlineExtras).getFeatured(HomeFilter.MOVIES)

        assertEquals(listOf<String?>(null), restored.map { it.trailerKey })
    }

    @Test
    fun `kids featured trailer key is cached too`() = runTest {
        val extras = mockk<HomeExtrasSource> {
            coEvery { kidsFeatured(HomeFilter.MOVIES) } returns listOf(featured("kids-trailer"))
        }
        repository(extras, kids = true).getFeatured(HomeFilter.MOVIES)

        val offlineExtras = mockk<HomeExtrasSource> { coEvery { kidsFeatured(any()) } throws IllegalStateException("sin red") }
        val restored = repository(offlineExtras, kids = true).getFeatured(HomeFilter.MOVIES)

        assertEquals(listOf("kids-trailer"), restored.map { it.trailerKey })
        coVerify(exactly = 0) { offlineExtras.kidsFeatured(any()) }
    }

    private fun repository(extras: HomeExtrasSource, kids: Boolean = false) = CatalogRepositoryImpl(
        tmdb = mockk<TmdbApi>(relaxed = true),
        torrentio = mockk<TorrentioApi>(relaxed = true),
        extras = extras,
        store = mockk<TokenStore>(relaxed = true),
        settings = mockk<SettingsRepository>(relaxed = true),
        profiles = mockk<ProfileRepository> {
            every { active } returns MutableStateFlow(Profile(id = "main", name = "Main", avatar = 0, kids = kids))
        },
        texts = mockk<Texts>(relaxed = true),
        diskCache = cache,
    )

    private fun featured(trailerKey: String?) = FeaturedItem(
        item = CatalogItem(
            id = "1",
            imdbId = null,
            type = MediaType.MOVIE,
            title = "Título",
            overview = "",
            posterUrl = "/poster.jpg",
            backdropUrl = "/backdrop.jpg",
            year = 2024,
            rating = 7.5f,
        ),
        badge = FeaturedBadge.POPULAR,
        logoUrl = null,
        genre = null,
        durationText = null,
        certification = null,
        trailerKey = trailerKey,
    )
}
