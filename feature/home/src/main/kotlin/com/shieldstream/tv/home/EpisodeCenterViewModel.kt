package com.shieldstream.tv.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shieldstream.tv.domain.EpisodeReleaseRepository
import com.shieldstream.tv.domain.EpisodeTrackingRepository
import com.shieldstream.tv.domain.MyListRepository
import com.shieldstream.tv.domain.NewEpisodes
import com.shieldstream.tv.domain.WatchProgressRepository
import com.shieldstream.tv.domain.mapConcurrent
import com.shieldstream.tv.model.HomeFilter
import com.shieldstream.tv.model.ShowEpisodeStatus
import com.shieldstream.tv.model.WatchProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

sealed interface EpisodeCenterUiState {
    data object Loading : EpisodeCenterUiState
    data class Content(
        val available: List<ShowEpisodeStatus>,
        val upcoming: List<ShowEpisodeStatus>,
    ) : EpisodeCenterUiState {
        val newEpisodeCount: Int get() = available.sumOf { it.availableEpisodes.size }
    }
}

private data class EpisodeCenterQuery(
    val showIds: List<String>,
    val watchedKeys: Set<String>,
    val dismissedKeys: Set<String>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EpisodeCenterViewModel @Inject constructor(
    private val releases: EpisodeReleaseRepository,
    private val tracking: EpisodeTrackingRepository,
    watchProgress: WatchProgressRepository,
    myList: MyListRepository,
) : ViewModel() {

    private val active = MutableStateFlow(false)

    val uiState: StateFlow<EpisodeCenterUiState> = combine(
        active,
        watchProgress.progress,
        myList.entries,
        tracking.followedShowIds,
        tracking.dismissedEpisodeKeys,
    ) { isActive, progress, list, followed, dismissed ->
        isActive to EpisodeCenterQuery(
            showIds = NewEpisodes.candidateIds(progress, list, HomeFilter.ALL, followed, maxItems = 50),
            watchedKeys = progress.filterValues { it.isWatched }.keys,
            dismissedKeys = dismissed,
        )
    }
        .distinctUntilChanged()
        .mapLatest { (isActive, query) ->
            if (!isActive) return@mapLatest null
            val statuses = query.showIds.mapConcurrent(concurrency = 4) { showId ->
                withTimeoutOrNull(8_000) { runCatching { releases.status(showId) }.getOrNull() }
            }.filterNotNull()
            EpisodeCenterUiState.Content(
                available = statuses.filter { it.availableEpisodes.isNotEmpty() }
                    .sortedByDescending { it.availableEpisodes.first().airDate },
                upcoming = statuses.filter { it.upcomingEpisode != null }
                    .sortedBy { it.upcomingEpisode?.airDate },
            )
        }
        .filterNotNull()
        .stateIn(viewModelScope, SharingStarted.Eagerly, EpisodeCenterUiState.Loading)

    fun activate() {
        active.value = true
    }

    fun dismiss(status: ShowEpisodeStatus) {
        tracking.dismissEpisodes(
            status.availableEpisodes.map {
                WatchProgress.episodeKey(status.item.id, it.season, it.number)
            },
        )
    }
}
