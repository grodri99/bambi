package com.shieldstream.tv.data.repository

import android.content.Context
import android.os.Build
import com.shieldstream.tv.data.security.TokenStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

internal const val TRAKT_SYNC_PREFS = "trakt_sync"

@Singleton
class ProfileDataCleaner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokens: TokenStore,
) {
    fun delete(profileId: String) {
        for (name in listOf(
            WatchProgressRepositoryImpl.prefsNameFor(profileId),
            MyListRepositoryImpl.prefsNameFor(profileId),
            EpisodeTrackingRepositoryImpl.prefsNameFor(profileId),
        )) {
            if (Build.VERSION.SDK_INT >= 24) {
                context.deleteSharedPreferences(name)
            } else {
                context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()
            }
        }
        clearTrakt(profileId)
    }

    fun clearTrakt(profileId: String) {
        tokens.setSecret(TraktSession.storeKey(profileId), null)
        context.getSharedPreferences(TRAKT_SYNC_PREFS, Context.MODE_PRIVATE).edit()
            .remove("all_$profileId")
            .remove("last_$profileId")
            .remove("remote_$profileId")
            .remove("wl_$profileId")
            .apply()
    }
}
