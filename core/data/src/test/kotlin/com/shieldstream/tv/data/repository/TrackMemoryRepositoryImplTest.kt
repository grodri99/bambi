package com.shieldstream.tv.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.domain.TrackMemoryRepository
import com.shieldstream.tv.model.Profile
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrackMemoryRepositoryImplTest {

    private lateinit var context: Context
    private val active = MutableStateFlow(Profile(id = "one", name = "One", avatar = 0))

    @Before
    fun clear() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("track_memory_one", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("track_memory_two", Context.MODE_PRIVATE).edit().clear().commit()
        active.value = Profile(id = "one", name = "One", avatar = 0)
    }

    private fun repository(): TrackMemoryRepositoryImpl {
        val profiles = mockk<ProfileRepository>()
        every { profiles.active } returns active
        return TrackMemoryRepositoryImpl(context, profiles)
    }

    @Test
    fun `audio and subtitle choices persist and are profile specific`() {
        val repository = repository()
        repository.saveAudio("tt1", "spa")
        repository.saveSubtitle("tt1", "eng")

        assertEquals("spa", repository.audio("tt1"))
        assertEquals("eng", repository.subtitle("tt1"))

        active.value = Profile(id = "two", name = "Two", avatar = 1)
        assertNull(repository.audio("tt1"))
    }

    @Test
    fun `subtitles off is remembered explicitly`() {
        val repository = repository()
        repository.saveSubtitle("tt1", "spa")
        repository.saveSubtitlesOff("tt1")

        assertEquals(TrackMemoryRepository.SUBTITLES_OFF, repository.subtitle("tt1"))
    }
}
