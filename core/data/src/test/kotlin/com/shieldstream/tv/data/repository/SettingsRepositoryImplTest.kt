package com.shieldstream.tv.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shieldstream.tv.data.security.TokenStore
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.model.AppLanguage
import com.shieldstream.tv.model.AudioCodec
import com.shieldstream.tv.model.HdrType
import com.shieldstream.tv.model.Profile
import com.shieldstream.tv.model.Quality
import com.shieldstream.tv.model.SourceExclusions
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsRepositoryImplTest {

    private lateinit var context: Context

    @Before
    fun clearSettings() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(AppLanguage.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `home row limit defaults to 30 and persists across reloads`() = runTest {
        assertEquals(
            SettingsRepository.DEFAULT_HOME_ROW_ITEM_LIMIT,
            repository().homeRowItemLimit.first(),
        )

        val settings = repository()
        settings.setHomeRowItemLimit(50)

        assertEquals(50, settings.homeRowItemLimit.first())
        assertEquals(50, repository().homeRowItemLimit.first())
    }

    @Test
    fun `home row limit is clamped to the supported range`() = runTest {
        val repository = repository()

        repository.setHomeRowItemLimit(0)
        assertEquals(SettingsRepository.MIN_HOME_ROW_ITEM_LIMIT, repository.homeRowItemLimit.first())

        repository.setHomeRowItemLimit(1_000)
        assertEquals(SettingsRepository.MAX_HOME_ROW_ITEM_LIMIT, repository.homeRowItemLimit.first())
    }

    @Test
    fun `source exclusions default empty and persist across reloads`() = runTest {
        assertEquals(SourceExclusions(), repository().sourceExclusions.first())
        val exclusions = SourceExclusions(
            qualities = setOf(Quality.UHD_2160, Quality.SD),
            hdrTypes = setOf(HdrType.DOLBY_VISION),
            audioCodecs = setOf(AudioCodec.DOLBY_TRUEHD, AudioCodec.DTS),
        )

        val settings = repository()
        settings.setSourceExclusions(exclusions)

        assertEquals(exclusions, settings.sourceExclusions.first())
        assertEquals(exclusions, repository().sourceExclusions.first())
    }

    @Test
    fun `soft backdrops default off and persist across reloads`() = runTest {
        assertEquals(false, repository().softBackdrops.first())

        repository().setSoftBackdrops(true)

        assertEquals(true, repository().softBackdrops.first())
        assertEquals(true, repository().softBackdropsEnabled)
    }

    @Test
    fun `intro always defaults off and persists across reloads`() = runTest {
        assertEquals(false, repository().introAlways.first())

        repository().setIntroAlways(true)

        assertEquals(true, repository().introAlways.first())
        assertEquals(true, repository().introAlwaysEnabled)
    }

    @Test
    fun `high resolution backdrops default off and persist across reloads`() = runTest {
        assertEquals(false, repository().highResBackdrops.first())

        repository().setHighResBackdrops(true)

        assertEquals(true, repository().highResBackdrops.first())
        assertEquals(true, repository().highResBackdropsEnabled)
    }

    private fun repository(): SettingsRepositoryImpl {
        val tokenStore = mockk<TokenStore> { every { token } returns null }
        val profiles = mockk<ProfileRepository> {
            every { active } returns MutableStateFlow(Profile("main", "Main", 0))
        }
        return SettingsRepositoryImpl(context, tokenStore, profiles)
    }
}
