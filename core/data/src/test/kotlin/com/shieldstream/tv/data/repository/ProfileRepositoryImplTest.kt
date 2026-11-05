package com.shieldstream.tv.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shieldstream.tv.model.Profile
import com.shieldstream.tv.strings.Texts
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileRepositoryImplTest {

    private lateinit var context: Context

    @Before
    fun clearProfiles() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("profiles", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `deleting a profile invokes centralized data cleanup`() {
        val cleanup = mockk<ProfileDataCleaner>(relaxed = true)
        val repository = ProfileRepositoryImpl(context, cleanup, mockk<Texts>(relaxed = true))
        val deleted = Profile(id = "deleted", name = "Deleted", avatar = 1)
        repository.save(deleted)

        repository.delete(deleted.id)

        assertFalse(repository.profiles.value.any { it.id == deleted.id })
        verify { cleanup.delete(deleted.id) }
    }
}
