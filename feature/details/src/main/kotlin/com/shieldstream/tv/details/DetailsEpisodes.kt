package com.shieldstream.tv.details

import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.shieldstream.tv.domain.NewEpisodes
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.strings.localizedCode
import com.shieldstream.tv.model.WatchProgress
import com.shieldstream.tv.ui.component.AppIcons
import com.shieldstream.tv.ui.component.ProvidePivotScroll
import com.shieldstream.tv.ui.component.formatEpisodeDate
import com.shieldstream.tv.ui.component.tvClickable

// Ficha → temporadas y episodios, con lo visto.

internal data class EpisodeWatchedAction(val episode: Episode, val watched: Boolean)

/**
 * The episode the dock can mark watched: the one currently selected. [EpisodeWatchedAction.watched] mirrors
 * [WatchProgress.isWatched] so the action reads "watched" or "mark watched" for that single episode.
 */
internal fun episodeWatchedAction(state: DetailsUiState.Content): EpisodeWatchedAction? {
    if (!state.isSeries) return null
    val episode = state.selectedEpisode ?: return null
    return EpisodeWatchedAction(episode, state.progressOf(episode)?.isWatched == true)
}

internal fun seasonAllWatched(state: DetailsUiState.Content): Boolean {
    val episodes = (state.episodes as? EpisodesState.Ready)?.episodes.orEmpty()
    return episodes.isNotEmpty() && episodes.all { state.progressOf(it)?.isWatched == true }
}

/** What the long-press modal's toggle will do: unwatched episodes get marked, watched ones unmarked. */
internal fun episodeMenuTogglesToWatched(state: DetailsUiState.Content, episode: Episode): Boolean =
    state.progressOf(episode)?.isWatched != true

@Composable
internal fun episodeWatchedLabel(episode: Episode, watched: Boolean): String = localizedStringResource(
    if (watched) StringsR.string.details_episode_watched else StringsR.string.details_mark_episode_watched,
    episode.localizedCode(),
)

@Composable
internal fun seasonWatchedLabel(state: DetailsUiState.Content): String? {
    val season = state.selectedSeason ?: return null
    val episodes = (state.episodes as? EpisodesState.Ready)?.episodes.orEmpty()
    if (episodes.isEmpty()) return null
    return if (seasonAllWatched(state)) {
        localizedStringResource(StringsR.string.details_season_watched, season)
    } else {
        localizedStringResource(StringsR.string.details_mark_season_watched, season)
    }
}

@Composable
internal fun SeasonsAndEpisodes(
    state: DetailsUiState.Content,
    interactive: Boolean,
    onSeason: (Int) -> Unit,
    onEpisode: (Episode) -> Unit,
    onEpisodeLongPress: (Episode) -> Unit,
    onEpisodeFocused: (Episode) -> Unit = {},
) {
    val seasons = state.details.seasons
    val loadedEpisodes = (state.episodes as? EpisodesState.Ready)?.episodes.orEmpty()
    val watched = loadedEpisodes.count { state.progressOf(it)?.isWatched == true }
    Column {
        if (seasons.size > 1) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 56.dp),
            ) {
                ProvidePivotScroll(leading = 0.dp) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(seasons, key = { it.number }) { season ->
                            SeasonChip(
                                label = localizedStringResource(StringsR.string.details_season_number, season.number),
                                selected = season.number == state.selectedSeason,
                                enabled = interactive,
                                onClick = { onSeason(season.number) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 56.dp, bottom = 10.dp),
            ) {
                Text(
                    text = localizedStringResource(StringsR.string.details_episodes),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                if (loadedEpisodes.isNotEmpty()) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = localizedStringResource(StringsR.string.details_watched_count_short, watched, loadedEpisodes.size),
                        color = WatchedCountColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        if (loadedEpisodes.isNotEmpty()) {
            // Con varias temporadas el recuento va aquí: al lado de las pestañas lo tapaban al desplazarlas.
            if (seasons.size > 1) {
                Text(
                    text = localizedStringResource(StringsR.string.details_watched_count_short, watched, loadedEpisodes.size),
                    color = WatchedCountColor,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 56.dp, bottom = 2.dp),
                )
            }
            Text(
                text = localizedStringResource(StringsR.string.details_hold_ok_for_episode_options),
                color = Color.White.copy(alpha = 0.38f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 56.dp),
            )
        }
        when (val eps = state.episodes) {
            EpisodesState.Loading -> Text(localizedStringResource(StringsR.string.details_loading_episodes), color = Grey, modifier = Modifier.padding(start = 56.dp))
            is EpisodesState.Error -> Text(eps.message, color = Grey, modifier = Modifier.padding(start = 56.dp))
            is EpisodesState.Ready -> ProvidePivotScroll(leading = 56.dp) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 56.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(eps.episodes, key = { "${it.season}_${it.number}" }) { ep ->
                        EpisodeCard(
                            episode = ep,
                            progress = state.progressOf(ep),
                            selected = ep == state.selectedEpisode,
                            loading = ep == state.selectedEpisode && state.pendingPlay,
                            enabled = interactive,
                            onClick = { onEpisode(ep) },
                            onLongClick = { onEpisodeLongPress(ep) },
                            onFocused = { onEpisodeFocused(ep) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    // Same filled style as the other chips: faint, brighter when selected, inverted on focus.
    val bg = when {
        focused -> Color.White
        selected -> Color.White.copy(alpha = 0.22f)
        else -> Color.White.copy(alpha = 0.08f)
    }
    Text(
        text = label,
        color = when {
            focused -> Color.Black
            selected -> Color.White
            else -> Color.White.copy(alpha = 0.75f)
        },
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .then(if (!focused && !selected) Modifier.border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(20.dp)) else Modifier)
            .tvClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    )
}

@Composable
private fun EpisodeCard(
    episode: Episode,
    progress: WatchProgress?,
    selected: Boolean,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) { if (focused) onFocused() }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "episodeScale")
    val shape = RoundedCornerShape(6.dp)
    Column(
        Modifier
            .width(200.dp)
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape)
                .background(Color(0xFF232327))
                .then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .then(if (enabled) Modifier.tvClickable(onLongClick = onLongClick, onClick = onClick) else Modifier.tvClickable(enabled = false) {}),
        ) {
            if (episode.stillUrl != null) {
                AsyncImage(
                    model = episode.stillUrl,
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (progress?.isWatched == true) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color.Black.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✓", color = Green, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                }
            } else if (progress != null && progress.fraction > 0.01f) {
                WatchBar(
                    fraction = progress.fraction,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
            if (focused || loading) {
                Box(
                    Modifier.align(Alignment.Center).size(44.dp).clip(RoundedCornerShape(22.dp))
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (loading) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(AppIcons.Play, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${episode.number}. ${episode.title}",
            color = if (selected || focused) Color.White else Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val today = remember { NewEpisodes.dateWindow(System.currentTimeMillis()).second }
        val releaseDate = remember(episode.airDate, today, Lang.current) {
            formatEpisodeDate(upcomingEpisodeDate(episode.airDate, today), includeYear = true)
        }
        val remainingMinutes = progress?.takeIf { it.isResumable }?.let { ((it.remainingMs + 59_999) / 60_000).toInt() }
        val remainingLabel = remainingMinutes?.let {
            localizedStringResource(StringsR.string.details_remaining, formatRuntime(it))
        }
        val metadata = remember(remainingLabel, episode.runtimeMinutes, releaseDate) {
            listOfNotNull(
                remainingLabel ?: episode.runtimeMinutes?.let { "$it min" },
                releaseDate.ifBlank { null },
            ).joinToString(" · ")
        }
        if (metadata.isNotEmpty()) {
            Text(metadata, color = Grey, style = MaterialTheme.typography.bodySmall)
        }
    }
}
