package com.shieldstream.tv.domain

import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.PlaybackMedia
import com.shieldstream.tv.model.StreamSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NextEpisode(val showId: String, val season: Int, val episode: Int, val label: String)

data class EpisodeTarget(val season: Int, val number: Int)

class SelectedContentHolder {
    private val _selectedItem = MutableStateFlow<CatalogItem?>(null)
    val selectedItem: StateFlow<CatalogItem?> = _selectedItem.asStateFlow()

    private val _queue = MutableStateFlow<List<StreamSource>>(emptyList())
    val queue: StateFlow<List<StreamSource>> = _queue.asStateFlow()

    private val _playLabel = MutableStateFlow("")
    val playLabel: StateFlow<String> = _playLabel.asStateFlow()

    private val _progressKey = MutableStateFlow<String?>(null)
    val progressKey: StateFlow<String?> = _progressKey.asStateFlow()

    private val _startFromBeginning = MutableStateFlow(false)
    val startFromBeginning: StateFlow<Boolean> = _startFromBeginning.asStateFlow()

    private val _media = MutableStateFlow<PlaybackMedia?>(null)
    val media: StateFlow<PlaybackMedia?> = _media.asStateFlow()

    private val _next = MutableStateFlow<NextEpisode?>(null)
    val next: StateFlow<NextEpisode?> = _next.asStateFlow()

    @Volatile private var nextRequested: NextEpisode? = null

    fun requestNext(next: NextEpisode) {
        nextRequested = next
    }

    fun consumeNextRequest(): NextEpisode? = nextRequested.also { nextRequested = null }

    private val _prefetch = MutableStateFlow<NextEpisode?>(null)
    val prefetch: StateFlow<NextEpisode?> = _prefetch.asStateFlow()

    fun prefetchNext(next: NextEpisode) {
        _prefetch.value = next
    }

    @Volatile private var episodeTarget: EpisodeTarget? = null
    @Volatile private var playImmediately: Boolean = false

    fun selectItem(item: CatalogItem, target: EpisodeTarget? = null, playImmediately: Boolean = false) {
        episodeTarget = target
        this.playImmediately = playImmediately
        _selectedItem.value = item
        _queue.value = emptyList()
        _prefetch.value = null
    }

    fun consumeEpisodeTarget(): EpisodeTarget? = episodeTarget.also { episodeTarget = null }
    fun consumePlayImmediately(): Boolean = playImmediately.also { playImmediately = false }

    fun playQueue(
        sources: List<StreamSource>,
        label: String,
        progressKey: String? = null,
        fromStart: Boolean = false,
        media: PlaybackMedia? = null,
        next: NextEpisode? = null,
    ) {
        _next.value = next
        _media.value = media
        _queue.value = sources
        _playLabel.value = label
        _progressKey.value = progressKey
        _startFromBeginning.value = fromStart
    }
}
