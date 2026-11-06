package com.shieldstream.tv.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shieldstream.tv.data.security.TokenStore
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.model.AppLanguage
import com.shieldstream.tv.model.Profile
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

    private fun repository(): SettingsRepositoryImpl {
        val tokenStore = mockk<TokenStore> { every { token } returns null }
        val profiles = mockk<ProfileRepository> {
            every { active } returns MutableStateFlow(Profile("main", "Main", 0))
        }
        return SettingsRepositoryImpl(context, tokenStore, profiles)
    }
}
