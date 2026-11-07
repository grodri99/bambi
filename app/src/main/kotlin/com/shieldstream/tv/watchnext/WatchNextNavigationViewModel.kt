package com.shieldstream.tv.watchnext

import androidx.lifecycle.ViewModel
import com.shieldstream.tv.domain.CatalogRepository
import com.shieldstream.tv.domain.EpisodeTarget
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.model.CatalogItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class ResolvedWatchNextRequest(
    val item: CatalogItem,
    val target: EpisodeTarget?,
)

@HiltViewModel
class WatchNextNavigationViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val catalog: CatalogRepository,
) : ViewModel() {

    suspend fun resolve(request: WatchNextRequest): ResolvedWatchNextRequest? {
        if (profiles.active.value.id != request.profileId) return null
        val item = runCatching { catalog.getItem(request.type, request.mediaId) }.getOrNull() ?: return null
        return ResolvedWatchNextRequest(item, request.target)
    }
}
