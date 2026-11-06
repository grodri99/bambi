package com.shieldstream.tv.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shieldstream.tv.domain.IthilLibraryRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class IthilAccessRepositoryImplTest {
    private lateinit var context: Context
    private lateinit var library: IthilLibraryRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("ithil_access", Context.MODE_PRIVATE).edit().clear().commit()
        library = mockk()
        every { library.isReady() } returns false
    }

    @Test
    fun unlockPersistsAcrossRepositoryRecreation() {
        val access = IthilAccessRepositoryImpl(context, library)
        assertFalse(access.isUnlocked)

        access.unlock()

        assertTrue(access.unlocked.value)
        assertTrue(IthilAccessRepositoryImpl(context, library).isUnlocked)
    }

    @Test
    fun existingIndexUnlocksAndRemainsUnlockedAfterItsRemoval() {
        every { library.isReady() } returns true

        assertTrue(IthilAccessRepositoryImpl(context, library).isUnlocked)
        every { library.isReady() } returns false
        assertTrue(IthilAccessRepositoryImpl(context, library).isUnlocked)
    }
}
