package com.shieldstream.tv.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shieldstream.tv.data.security.TokenStore
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 35])
class ProfileDataCleanerTest {

    private lateinit var context: Context
    private lateinit var tokens: TokenStore
    private lateinit var cleaner: ProfileDataCleaner

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        tokens = mockk(relaxed = true)
        cleaner = ProfileDataCleaner(context, tokens)
        profileStores(DELETED).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        context.getSharedPreferences(TRAKT_SYNC_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `profile deletion clears every local store and Trakt sync marker`() {
        profileStores(DELETED).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().putString("owned", "yes").commit()
        }
        val survivingStore = MyListRepositoryImpl.prefsNameFor(SURVIVOR)
        context.getSharedPreferences(survivingStore, Context.MODE_PRIVATE).edit().putLong("MOVIE_2", 2L).commit()
        val sync = context.getSharedPreferences(TRAKT_SYNC_PREFS, Context.MODE_PRIVATE)
        sync.edit()
            .putString("all_$DELETED", "all")
            .putLong("last_$DELETED", 1L)
            .putStringSet("remote_$DELETED", setOf("movie:1"))
            .putStringSet("wl_$DELETED", setOf("MOVIE_1"))
            .putStringSet("wl_$SURVIVOR", setOf("MOVIE_2"))
            .commit()

        cleaner.delete(DELETED)

        profileStores(DELETED).forEach { name ->
            assertTrue("$name should be empty", context.getSharedPreferences(name, Context.MODE_PRIVATE).all.isEmpty())
        }
        assertTrue(context.getSharedPreferences(survivingStore, Context.MODE_PRIVATE).contains("MOVIE_2"))
        assertFalse(sync.contains("all_$DELETED"))
        assertFalse(sync.contains("last_$DELETED"))
        assertFalse(sync.contains("remote_$DELETED"))
        assertFalse(sync.contains("wl_$DELETED"))
        assertTrue(sync.contains("wl_$SURVIVOR"))
        verify { tokens.setSecret(TraktSession.storeKey(DELETED), null) }
    }

    @Test
    fun `Trakt cleanup preserves local watch data`() {
        profileStores(DELETED).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().putString("owned", "yes").commit()
        }
        val sync = context.getSharedPreferences(TRAKT_SYNC_PREFS, Context.MODE_PRIVATE)
        sync.edit().putStringSet("wl_$DELETED", setOf("MOVIE_1")).commit()

        cleaner.clearTrakt(DELETED)

        profileStores(DELETED).forEach { name ->
            assertTrue(context.getSharedPreferences(name, Context.MODE_PRIVATE).contains("owned"))
        }
        assertFalse(sync.contains("wl_$DELETED"))
        verify { tokens.setSecret(TraktSession.storeKey(DELETED), null) }
    }

    private fun profileStores(profileId: String) = listOf(
        WatchProgressRepositoryImpl.prefsNameFor(profileId),
        MyListRepositoryImpl.prefsNameFor(profileId),
        EpisodeTrackingRepositoryImpl.prefsNameFor(profileId),
    )

    private companion object {
        const val DELETED = "deleted"
        const val SURVIVOR = "survivor"
    }
}
