package com.shieldstream.tv.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shieldstream.tv.domain.MyListChange
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.domain.WatchProgressClear
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.Profile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileChangeEventsTest {

    @Test
    fun `local change events retain the profile that owned the write`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val first = Profile(id = "first", name = "First", avatar = 0)
            val second = Profile(id = "second", name = "Second", avatar = 1)
            val profiles = FakeProfiles(first, second)
            val myList = MyListRepositoryImpl(context, profiles)
            val progress = WatchProgressRepositoryImpl(context, profiles)
            val listEvents = mutableListOf<MyListChange>()
            val clearEvents = mutableListOf<WatchProgressClear>()
            val listReady = CompletableDeferred<Unit>()
            val clearReady = CompletableDeferred<Unit>()
            backgroundScope.launch {
                myList.changes.onSubscription { listReady.complete(Unit) }.collect { listEvents += it }
            }
            backgroundScope.launch {
                progress.cleared.onSubscription { clearReady.complete(Unit) }.collect { clearEvents += it }
            }
            listReady.await()
            clearReady.await()

            myList.add(MediaType.MOVIE, "1")
            assertEquals(true, myList.contains(MediaType.MOVIE, "1"))
            progress.clear("movie:1")
            yield()
            profiles.select(second.id)
            advanceUntilIdle()
            myList.add(MediaType.MOVIE, "1")
            progress.clear("movie:1")
            yield()
            advanceUntilIdle()

            assertEquals(listOf(first.id, second.id), listEvents.map { it.profileId })
            assertEquals(listOf(first.id, second.id), clearEvents.map { it.profileId })
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeProfiles(vararg initial: Profile) : ProfileRepository {
        override val profiles = MutableStateFlow(initial.toList())
        override val active = MutableStateFlow(initial.first())

        override fun select(id: String) {
            profiles.value.firstOrNull { it.id == id }?.let { active.value = it }
        }

        override fun save(profile: Profile): Profile {
            profiles.value = profiles.value.filterNot { it.id == profile.id } + profile
            if (active.value.id == profile.id) active.value = profile
            return profile
        }

        override fun draft(): Profile = active.value
        override fun delete(id: String) = Unit
    }
}
