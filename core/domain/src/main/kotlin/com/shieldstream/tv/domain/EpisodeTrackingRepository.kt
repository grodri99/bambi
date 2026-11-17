package com.shieldstream.tv.domain

import kotlinx.coroutines.flow.StateFlow

interface EpisodeTrackingRepository {
    val followedShowIds: StateFlow<Set<String>>
    val dismissedEpisodeKeys: StateFlow<Set<String>>
    val notifiedEpisodeKeys: StateFlow<Set<String>>
    val notificationsEnabled: StateFlow<Boolean>

    fun isFollowing(showId: String): Boolean
    fun setFollowing(showId: String, following: Boolean)
    fun dismissEpisodes(keys: Collection<String>)
    fun setNotificationsEnabled(enabled: Boolean)

    /** Series que se han quitado de Episodios a mano: no salen aunque se hayan visto o estén en Mi lista. */
    val hiddenShowIds: StateFlow<Set<String>> get() = kotlinx.coroutines.flow.MutableStateFlow(emptySet())
    fun setHidden(showId: String, hidden: Boolean) {}
    fun markNotified(keys: Collection<String>)
}
