package com.shieldstream.tv.domain

import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.PlaybackMedia
import com.shieldstream.tv.model.StreamSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NextEpisode(val showId: String, val season: Int, val episode: Int, val label: String)

data class EpisodeTarget(val season: Int, val number: Int)

data class DetailsRequest(
    val item: CatalogItem,
    val target: EpisodeTarget? = null,
    val playImmediately: Boolean = false,
)

data class PlaybackRequest(
    val sources: List<StreamSource>,
    val label: String,
    val progressKey: String? = null,
    val fromStart: Boolean = false,
    val media: PlaybackMedia? = null,
    val next: NextEpisode? = null,
)

class SelectedContentHolder {
    private val _details = MutableStateFlow<DetailsRequest?>(null)
    val details: StateFlow<DetailsRequest?> = _details.asStateFlow()

    private val _playback = MutableStateFlow<PlaybackRequest?>(null)
    val playback: StateFlow<PlaybackRequest?> = _playback.asStateFlow()

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

    fun selectItem(item: CatalogItem, target: EpisodeTarget? = null, playImmediately: Boolean = false) {
        _details.value = DetailsRequest(item, target, playImmediately)
        _playback.value = null
        _prefetch.value = null
    }

    fun playQueue(
        sources: List<StreamSource>,
        label: String,
        progressKey: String? = null,
        fromStart: Boolean = false,
        media: PlaybackMedia? = null,
        next: NextEpisode? = null,
    ) {
        _playback.value = PlaybackRequest(sources, label, progressKey, fromStart, media, next)
    }
}
