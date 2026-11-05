package com.shieldstream.tv.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.shieldstream.tv.domain.EpisodeTrackingRepository
import com.shieldstream.tv.domain.ProfileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpisodeTrackingRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    profiles: ProfileRepository,
) : EpisodeTrackingRepository {

    @Volatile private var prefs: SharedPreferences = prefsFor(profiles.active.value.id)

    private val _followed = MutableStateFlow(prefs.stringSet(FOLLOWED))
    override val followedShowIds: StateFlow<Set<String>> = _followed.asStateFlow()
    private val _dismissed = MutableStateFlow(prefs.stringSet(DISMISSED))
    override val dismissedEpisodeKeys: StateFlow<Set<String>> = _dismissed.asStateFlow()
    private val _notified = MutableStateFlow(prefs.stringSet(NOTIFIED))
    override val notifiedEpisodeKeys: StateFlow<Set<String>> = _notified.asStateFlow()
    private val _notifications = MutableStateFlow(prefs.getBoolean(NOTIFICATIONS, false))
    override val notificationsEnabled: StateFlow<Boolean> = _notifications.asStateFlow()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            profiles.active.distinctUntilChangedBy { it.id }.drop(1).collect { profile ->
                prefs = prefsFor(profile.id)
                _followed.value = prefs.stringSet(FOLLOWED)
                _dismissed.value = prefs.stringSet(DISMISSED)
                _notified.value = prefs.stringSet(NOTIFIED)
                _notifications.value = prefs.getBoolean(NOTIFICATIONS, false)
            }
        }
    }

    override fun isFollowing(showId: String): Boolean = showId in _followed.value

    override fun setFollowing(showId: String, following: Boolean) {
        val updated = if (following) _followed.value + showId else _followed.value - showId
        if (updated == _followed.value) return
        prefs.edit().putStringSet(FOLLOWED, updated).apply()
        _followed.value = updated
    }

    override fun dismissEpisodes(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val updated = _dismissed.value + keys
        prefs.edit().putStringSet(DISMISSED, updated).apply()
        _dismissed.value = updated
    }

    override fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(NOTIFICATIONS, enabled).apply()
        _notifications.value = enabled
    }

    override fun markNotified(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val updated = _notified.value + keys
        prefs.edit().putStringSet(NOTIFIED, updated).apply()
        _notified.value = updated
    }

    private fun prefsFor(profileId: String) = context.getSharedPreferences("episode_tracking_$profileId", Context.MODE_PRIVATE)

    companion object {
        fun prefsNameFor(profileId: String) = "episode_tracking_$profileId"

        private const val FOLLOWED = "followed"
        private const val DISMISSED = "dismissed"
        private const val NOTIFIED = "notified"
        private const val NOTIFICATIONS = "notifications"
    }
}

private fun SharedPreferences.stringSet(key: String): Set<String> = getStringSet(key, emptySet()).orEmpty().toSet()
