package com.shieldstream.tv.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shieldstream.tv.domain.CatalogRepository
import com.shieldstream.tv.domain.EpisodeReleaseRepository
import com.shieldstream.tv.domain.EpisodeTrackingRepository
import com.shieldstream.tv.domain.MyListEntry
import com.shieldstream.tv.domain.MyListRepository
import com.shieldstream.tv.domain.WatchProgressRepository
import com.shieldstream.tv.domain.mapConcurrent
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.model.MediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

enum class MyListFilter { ALL, TV, MOVIES }
enum class MyListSort { NEW_FIRST, RECENT, TITLE }

private data class MyListControls(
    val filter: MyListFilter,
    val sort: MyListSort,
    val hideCompleted: Boolean,
)

sealed interface MyListUiState {
    data object Loading : MyListUiState
    data class Empty(
        val filter: MyListFilter,
        val sort: MyListSort = MyListSort.RECENT,
        val hideCompleted: Boolean = false,
    ) : MyListUiState
    data class Content(
        val filter: MyListFilter,
        val items: List<CatalogItem>,
        val newEpisodeCounts: Map<String, Int> = emptyMap(),
        val sort: MyListSort,
        val hideCompleted: Boolean,
    ) : MyListUiState
}

private data class MyListQuery(
    val entries: List<MyListEntry>,
    val controls: MyListControls,
    val watchedKeys: Set<String>,
    val dismissedKeys: Set<String>,
)

private data class LoadedMyListItem(
    val item: CatalogItem,
    val addedAt: Long,
    val newEpisodes: List<Episode> = emptyList(),
    val complete: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MyListViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val myList: MyListRepository,
    private val watchProgress: WatchProgressRepository,
    private val episodeReleases: EpisodeReleaseRepository,
    private val tracking: EpisodeTrackingRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(MyListFilter.ALL)
    private val sort = MutableStateFlow(MyListSort.RECENT)
    private val hideCompleted = MutableStateFlow(false)
    private val controls = combine(filter, sort, hideCompleted, ::MyListControls)

    private val loaded = ConcurrentHashMap<String, CatalogItem>()

    val uiState: StateFlow<MyListUiState> = combine(
        myList.entries,
        watchProgress.progress,
        tracking.dismissedEpisodeKeys,
        controls,
    ) { entries, progress, dismissed, currentControls ->
        MyListQuery(entries, currentControls, progress.filterValues { it.isWatched }.keys, dismissed)
    }
        .distinctUntilChanged()
        .mapLatest { query -> load(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyListUiState.Loading)

    private suspend fun load(query: MyListQuery): MyListUiState {
        val current = query.controls
        val wanted = query.entries.filter {
            when (current.filter) {
                MyListFilter.ALL -> true
                MyListFilter.TV -> it.type == MediaType.TV
                MyListFilter.MOVIES -> it.type == MediaType.MOVIE
            }
        }
        val result = wanted.mapConcurrent(concurrency = 4) { entry ->
            val status = if (entry.type == MediaType.TV) {
                withTimeoutOrNull(8_000) { runCatching { episodeReleases.status(entry.id) }.getOrNull() }
            } else {
                null
            }
            val item = loaded[entry.key] ?: status?.item ?: withTimeoutOrNull(5_000) {
                runCatching { catalogRepository.getItem(entry.type, entry.id) }.getOrNull()
            }?.also { loaded[entry.key] = it }
            val complete = current.hideCompleted && entry.type == MediaType.TV &&
                runCatching { episodeReleases.isComplete(entry.id) }.getOrDefault(false)
            item?.let { LoadedMyListItem(it, entry.addedAt, status?.availableEpisodes.orEmpty(), complete) }
        }.filterNotNull().filterNot { it.complete }
        val sorted = when (current.sort) {
            MyListSort.NEW_FIRST -> result.sortedWith(
                compareByDescending<LoadedMyListItem> { it.newEpisodes.size }.thenByDescending { it.addedAt },
            )
            MyListSort.RECENT -> result.sortedByDescending { it.addedAt }
            MyListSort.TITLE -> result.sortedBy { it.item.title.lowercase(Lang.locale) }
        }
        if (sorted.isEmpty()) return MyListUiState.Empty(current.filter, current.sort, current.hideCompleted)
        return MyListUiState.Content(
            filter = current.filter,
            items = sorted.map { it.item },
            newEpisodeCounts = sorted.associate { it.item.key to it.newEpisodes.size },
            sort = current.sort,
            hideCompleted = current.hideCompleted,
        )
    }

    fun setFilter(value: MyListFilter) { filter.value = value }
    fun setSort(value: MyListSort) { sort.value = value }
    fun toggleHideCompleted() { hideCompleted.value = !hideCompleted.value }

    fun remove(item: CatalogItem) = myList.remove(item.type, item.id)
}
