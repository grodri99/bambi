package com.shieldstream.tv.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.model.Profile
import com.shieldstream.tv.model.WatchProgress
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WatchProgressRepositoryImplTest {

    private lateinit var context: Context
    private lateinit var profiles: ProfileRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(WatchProgressRepositoryImpl.prefsNameFor(Profile.DEFAULT_ID), Context.MODE_PRIVATE)
            .edit().clear().commit()
        profiles = mockk {
            every { active } returns MutableStateFlow(Profile(Profile.DEFAULT_ID, "Default", 0))
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `source selection survives reloads and later progress saves`() {
        val repository = WatchProgressRepositoryImpl(context, profiles)

        repository.save(KEY, 10_000, 120_000, sourceId = "provider:release|file")
        repository.save(KEY, 20_000, 120_000)

        assertEquals("provider:release|file", repository.get(KEY)?.sourceId)
        val reloaded = WatchProgressRepositoryImpl(context, profiles)
        assertEquals("provider:release|file", reloaded.get(KEY)?.sourceId)
        assertEquals(20_000L, reloaded.get(KEY)?.positionMs)
    }

    @Test
    fun `remote progress imports preserve the local source selection`() {
        val repository = WatchProgressRepositoryImpl(context, profiles)
        repository.save(KEY, 10_000, 120_000, sourceId = "provider:release")

        repository.importRemote(mapOf(KEY to WatchProgress(30_000, 120_000, updatedAt = 2)))

        assertEquals(30_000L, repository.get(KEY)?.positionMs)
        assertEquals("provider:release", repository.get(KEY)?.sourceId)
    }

    @Test
    fun `legacy progress entries without a source remain readable`() {
        context.getSharedPreferences(WatchProgressRepositoryImpl.prefsNameFor(Profile.DEFAULT_ID), Context.MODE_PRIVATE)
            .edit().putString(KEY, "10000|120000|1").commit()

        val stored = WatchProgressRepositoryImpl.readAll(context, Profile.DEFAULT_ID)[KEY]

        assertEquals(10_000L, stored?.positionMs)
        assertNull(stored?.sourceId)
    }

    private companion object {
        const val KEY = "movie:42"
    }
}
