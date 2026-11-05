package com.shieldstream.tv.home

import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shieldstream.tv.domain.CatalogRepository
import com.shieldstream.tv.domain.EpisodeReleaseRepository
import com.shieldstream.tv.domain.EpisodeTrackingRepository
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.domain.MyListRepository
import com.shieldstream.tv.domain.NewEpisodes
import com.shieldstream.tv.domain.ContinueEntry
import com.shieldstream.tv.domain.ContinueWatching
import com.shieldstream.tv.domain.WatchProgressRepository
import com.shieldstream.tv.domain.mapConcurrent
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.CatalogRow
import com.shieldstream.tv.model.ContinueInfo
import com.shieldstream.tv.model.FeaturedItem
import com.shieldstream.tv.model.HomeFilter
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.RowStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Error(val message: String) : HomeUiState
    data class Content(val rows: List<CatalogRow>, val featured: List<FeaturedItem> = emptyList()) : HomeUiState
}

private data class NewEpisodeQuery(
    val showIds: List<String>,
    val watchedKeys: Set<String>,
    val dismissedKeys: Set<String>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val watchProgress: WatchProgressRepository,
    private val episodeReleases: EpisodeReleaseRepository,
    private val tracking: EpisodeTrackingRepository,
    settings: SettingsRepository,
    myList: MyListRepository,
    profiles: com.shieldstream.tv.domain.ProfileRepository,
    private val texts: Texts,
) : ViewModel() {

    val kidsProfile: Boolean = profiles.active.value.kids

    val hiddenCategories: StateFlow<Set<String>> = settings.hiddenCategories
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)

    private val continueRows = MutableStateFlow<List<CatalogRow>?>(null)
    private val filterFlow = MutableStateFlow<HomeFilter?>(null)

    private val newEpisodeRows = MutableStateFlow<List<CatalogRow>?>(null)

    private val myListRows = MutableStateFlow<List<CatalogRow>?>(null)

    val uiState: StateFlow<HomeUiState> = combine(_uiState, continueRows, newEpisodeRows, myListRows) { catalog, continueRow, newRow, listRow ->
        when {
            catalog !is HomeUiState.Content -> catalog
            continueRow == null || newRow == null || listRow == null -> HomeUiState.Loading
            else -> {
                val releases = newRow.flatMap { it.newEpisodes.entries }.associate { it.key to it.value }
                val counts = newRow.flatMap { it.newEpisodeCounts.entries }.associate { it.key to it.value }
                val rows = continueRow + newRow + listRow + catalog.rows
                catalog.copy(
                    rows = rows.map { row ->
                        if (row.style == RowStyle.NEW_EPISODES || releases.isEmpty()) return@map row
                        row.copy(
                            newEpisodes = row.items.mapNotNull { item ->
                                releases[item.key]?.let { episode -> item.key to episode }
                            }.toMap(),
                            newEpisodeCounts = row.items.mapNotNull { item ->
                                counts[item.key]?.let { count -> item.key to count }
                            }.toMap(),
                        )
                    },
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState.Loading)

    private var loadedFilter: HomeFilter? = null

    init {
        viewModelScope.launch {
            combine(filterFlow.filterNotNull(), watchProgress.progress) { filter, all ->
                ContinueWatching.entries(all, filter)
            }
                .distinctUntilChanged()
                .mapLatest { entries -> listOfNotNull(buildContinueRow(entries)) }
                .collect { continueRows.value = it }
        }
        viewModelScope.launch {
            combine(
                filterFlow.filterNotNull(),
                watchProgress.progress,
                myList.entries,
                tracking.followedShowIds,
                tracking.dismissedEpisodeKeys,
            ) { filter, progress, list, followed, dismissed ->
                NewEpisodeQuery(
                    showIds = NewEpisodes.candidateIds(progress, list, filter, followed),
                    watchedKeys = progress.filterValues { it.isWatched }.keys,
                    dismissedKeys = dismissed,
                )
            }
                .distinctUntilChanged()
                .mapLatest { query -> listOfNotNull(buildNewEpisodesRow(query)) }
                .collect { newEpisodeRows.value = it }
        }
        viewModelScope.launch {
            combine(filterFlow.filterNotNull(), myList.entries) { filter, entries ->
                entries.filter {
                    when (filter) {
                        HomeFilter.ALL -> true
                        HomeFilter.MOVIES -> it.type == MediaType.MOVIE
                        HomeFilter.TV -> it.type == MediaType.TV
                    }
                }.take(MY_LIST_MAX)
            }
                .distinctUntilChanged()
                .mapLatest { entries -> listOfNotNull(buildMyListRow(entries)) }
                .collect { myListRows.value = it }
        }
    }

    fun removeFromContinue(item: CatalogItem) {
        ContinueWatching.keysToRemove(watchProgress.progress.value, item.type, item.id)
            .forEach(watchProgress::clear)
    }

    private suspend fun buildContinueRow(entries: List<ContinueEntry>): CatalogRow? {
        if (entries.isEmpty()) return null
        val loaded = entries.mapConcurrent(concurrency = 4) { entry ->
            val item = withTimeoutOrNull(5_000) {
                runCatching { catalogRepository.getItem(entry.type, entry.id) }.getOrNull()
            }
            item?.let { entry to it }
        }.filterNotNull()
        if (loaded.isEmpty()) return null
        return CatalogRow(
            id = CONTINUE_ROW_ID,
            title = texts.get(StringsR.string.home_continue_watching),
            items = loaded.map { it.second },
            style = RowStyle.CONTINUE,
            progress = loaded.associate { (entry, item) ->
                item.key to ContinueInfo(
                    fraction = entry.progress.fraction,
                    label = labelFor(entry),
                    season = entry.season,
                    episode = entry.episode,
                )
            },
        )
    }

    private suspend fun buildNewEpisodesRow(query: NewEpisodeQuery): CatalogRow? {
        if (query.showIds.isEmpty()) return null
        val loaded = query.showIds.mapConcurrent(concurrency = 4) { showId ->
            withTimeoutOrNull(8_000) {
                runCatching { episodeReleases.status(showId) }.getOrNull()
            }?.let { status ->
                val episode = status.availableEpisodes.firstOrNull() ?: return@let null
                if (status.item.posterUrl == null) null else Triple(status.item, episode, status.availableEpisodes.size)
            }
        }.filterNotNull()
        if (loaded.isEmpty()) return null
        val entries = loaded.sortedByDescending { it.second.airDate }.take(NewEpisodes.MAX_ITEMS)
        return CatalogRow(
            id = NEW_EPISODES_ROW_ID,
            title = texts.get(StringsR.string.home_new_episodes),
            items = entries.map { it.first },
            style = RowStyle.NEW_EPISODES,
            newEpisodes = entries.associate { (item, episode, _) -> item.key to episode },
            newEpisodeCounts = entries.associate { (item, _, count) -> item.key to count },
        )
    }

    private suspend fun buildMyListRow(entries: List<com.shieldstream.tv.domain.MyListEntry>): CatalogRow? {
        if (entries.isEmpty()) return null
        val items = entries.mapConcurrent(concurrency = 4) { e ->
            withTimeoutOrNull(5_000) { runCatching { catalogRepository.getItem(e.type, e.id) }.getOrNull() }
        }.filterNotNull()
        if (items.isEmpty()) return null
        return CatalogRow(id = MY_LIST_ROW_ID, title = texts.get(StringsR.string.home_my_list_f0348a), items = items)
    }

    private fun labelFor(entry: ContinueEntry): String {
        val remaining = formatRemaining(entry.progress.remainingMs)
        return if (entry.type == MediaType.TV) {
            texts.get(StringsR.string.home_continue_episode_remaining, entry.season.toString(), entry.episode.toString(), remaining)
        } else {
            texts.get(StringsR.string.home_remaining, remaining)
        }
    }

    private fun formatRemaining(ms: Long): String {
        val totalMin = ((ms + 59_999) / 60_000).toInt()
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h == 0 -> "$m min"
            m == 0 -> "$h h"
            else -> "$h h $m min"
        }
    }

    private companion object {
        const val CONTINUE_ROW_ID = "continue_watching"
        const val NEW_EPISODES_ROW_ID = "new_episodes"
        const val MY_LIST_ROW_ID = "my_list"
        const val MY_LIST_MAX = 40
    }

    private fun prefetchOtherTabs() {
        viewModelScope.launch {
            delay(1_500)
            for (other in listOf(HomeFilter.TV, HomeFilter.MOVIES)) {
                coroutineScope {
                    launch { runCatching { catalogRepository.getFeatured(other) } }
                    runCatching { catalogRepository.getHome(other) }
                }
            }
        }
    }

    fun load(filter: HomeFilter, force: Boolean = false) {
        filterFlow.value = filter
        if (!force && loadedFilter == filter && _uiState.value is HomeUiState.Content) return
        loadedFilter = filter
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            val featured = async { runCatching { catalogRepository.getFeatured(filter) }.getOrDefault(emptyList()) }
            runCatching { catalogRepository.getHome(filter) }
                .onSuccess { rows ->
                    _uiState.value = if (rows.isEmpty()) {
                        HomeUiState.Error(texts.get(StringsR.string.home_couldn_t_load_the_catalog_check_your))
                    } else {
                        HomeUiState.Content(rows, featured.await())
                    }
                    if (filter == HomeFilter.ALL) prefetchOtherTabs()
                }
                .onFailure { e ->
                    _uiState.value = HomeUiState.Error(e.message ?: texts.get(StringsR.string.home_error_loading_the_catalog))
                }
        }
    }
}
