package com.shieldstream.tv.details

import com.shieldstream.tv.ui.theme.AppColors
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.domain.TraktRatingTarget
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shieldstream.tv.model.Episode
import androidx.compose.ui.unit.sp
import com.shieldstream.tv.ui.component.requestFocusWithRetry
import com.shieldstream.tv.model.DownloadQuality
import com.shieldstream.tv.model.DownloadStatus
import com.shieldstream.tv.model.Download
import com.shieldstream.tv.strings.localizedCode
import com.shieldstream.tv.model.WatchProgress
import com.shieldstream.tv.ui.component.ActionMenuDialog
import com.shieldstream.tv.ui.component.ActionMenuItem
import com.shieldstream.tv.ui.component.AppIcons
import com.shieldstream.tv.ui.component.LineIcons
import com.shieldstream.tv.ui.component.onBackKey
import com.shieldstream.tv.ui.component.tvClickable
import java.util.Locale

// Ficha → botones de acción, panel «Más» y descargas.

@Composable
internal fun DetailsActions(
    state: DetailsUiState.Content,
    onToggleSeasonWatched: () -> Unit,
    onToggleMovieWatched: () -> Unit,
    onToggleEpisodeWatched: (Episode) -> Unit,
    onToggleMyList: () -> Unit,
    onToggleFollowing: () -> Unit,
    onRate: (TraktRatingTarget) -> Unit,
    onRandomEpisode: () -> Unit,
    downloads: List<Download>,
    onDownload: (List<Episode>, DownloadQuality) -> Unit,
    onDownloadAction: (Download) -> Unit,
    onDismiss: () -> Unit,
) {
    var downloadMenu by remember { mutableStateOf(false) }
    MoreSidePanel(
        title = state.details.item.title,
        subtitle = state.selectedEpisode?.takeIf { state.isSeries }?.let { "${it.localizedCode()} · ${it.title}" },
        onBack = { if (downloadMenu) downloadMenu = false else onDismiss() },
    ) {
        if (downloadMenu) {
            DownloadOptions(state = state, downloads = downloads, onDownload = onDownload)
            return@MoreSidePanel
        }
        val requester = remember { FocusRequester() }
        LaunchedEffect(Unit) { requester.requestFocusWithRetry() }
        val item = state.details.item
        val ratings = state.ratings as? TraktRatingsState.Ready
        val episode = state.selectedEpisode?.takeIf { state.isSeries }
        val key = if (episode != null) WatchProgress.episodeKey(item.id, episode.season, episode.number) else WatchProgress.movieKey(item.id)
        val download = downloads.firstOrNull { it.id == key }
        var firstAssigned = false
        fun first(): Modifier = if (!firstAssigned) { firstAssigned = true; Modifier.focusRequester(requester) } else Modifier

        if (state.isSeries) {
            if (episode != null) {
                MoreSectionHeader(localizedStringResource(StringsR.string.details_more_episode, episode.localizedCode()))
                val watched = state.progressOf(episode)?.isWatched == true
                MoreRow(
                    text = episodeWatchedLabel(episode, watched),
                    icon = if (watched) AppIcons.Check else LineIcons.Eye,
                    highlighted = watched,
                    onClick = { onToggleEpisodeWatched(episode) },
                    modifier = first(),
                )
                // La descarga de ESTE episodio siempre va aquí: descargarlo, o pausar/borrar si ya está. Varios episodios
                // o la temporada van en la sección de la serie ("Descargar varios…").
                if (download == null || download.status == DownloadStatus.FAILED) {
                    MoreRow(
                        text = localizedStringResource(StringsR.string.downloads_download_episode),
                        icon = LineIcons.Download,
                        detail = download?.error,
                        onClick = { onDownload(listOf(episode), DownloadQuality.BEST) },
                    )
                } else {
                    DownloadMoreRow(download, onOpenOptions = { downloadMenu = true }, onDownloadAction = onDownloadAction)
                }
                val target = TraktRatingTarget.Episode(item.id, episode.season, episode.number)
                MoreRow(
                    text = ratingTargetLabel(state, target) + ratingSuffix(ratings?.episodeRating),
                    icon = LineIcons.Star,
                    onClick = { onRate(target) },
                )
            }
            MoreSectionHeader(localizedStringResource(StringsR.string.details_more_series))
            seasonWatchedLabel(state)?.let { label ->
                val all = seasonAllWatched(state)
                MoreRow(label, if (all) AppIcons.Check else LineIcons.Eye, onToggleSeasonWatched, highlighted = all, modifier = first())
            }
            MoreRow(
                text = if (state.inMyList) localizedStringResource(StringsR.string.details_in_my_list) else localizedStringResource(StringsR.string.details_my_list),
                icon = if (state.inMyList) AppIcons.Check else AppIcons.Add,
                highlighted = state.inMyList,
                onClick = onToggleMyList,
                modifier = first(),
            )
            MoreRow(
                text = if (state.following) localizedStringResource(StringsR.string.details_following) else localizedStringResource(StringsR.string.details_follow),
                icon = if (state.following) AppIcons.Check else LineIcons.Bookmark,
                highlighted = state.following,
                onClick = onToggleFollowing,
            )
            MoreRow(
                text = localizedStringResource(StringsR.string.downloads_download_several),
                icon = LineIcons.Download,
                detail = localizedStringResource(StringsR.string.downloads_options_hint),
                onClick = { downloadMenu = true },
            )
            val show = TraktRatingTarget.Show(item.id)
            MoreRow(ratingTargetLabel(state, show) + ratingSuffix(ratings?.overallRating), LineIcons.Star, { onRate(show) })
            if (state.details.seasons.isNotEmpty()) {
                MoreRow(localizedStringResource(StringsR.string.details_random_episode), AppIcons.Bolt, onRandomEpisode)
            }
        } else {
            val watched = state.mainProgress?.isWatched == true
            MoreRow(
                text = localizedStringResource(if (watched) StringsR.string.details_watched else StringsR.string.details_mark_watched),
                icon = if (watched) AppIcons.Check else LineIcons.Eye,
                highlighted = watched,
                onClick = onToggleMovieWatched,
                modifier = first(),
            )
            MoreRow(
                text = if (state.inMyList) localizedStringResource(StringsR.string.details_in_my_list) else localizedStringResource(StringsR.string.details_my_list),
                icon = if (state.inMyList) AppIcons.Check else AppIcons.Add,
                highlighted = state.inMyList,
                onClick = onToggleMyList,
            )
            DownloadMoreRow(download, onOpenOptions = { downloadMenu = true }, onDownloadAction = onDownloadAction)
            val movie = TraktRatingTarget.Movie(item.id)
            MoreRow(ratingTargetLabel(state, movie) + ratingSuffix(ratings?.overallRating), LineIcons.Star, { onRate(movie) })
        }
    }
}

/** Fila de descarga del episodio/película: según el estado, descargar (con opciones), pausar, reanudar o borrar. */
@Composable
private fun DownloadMoreRow(download: Download?, onOpenOptions: () -> Unit, onDownloadAction: (Download) -> Unit) {
    when {
        download == null || download.status == DownloadStatus.FAILED -> MoreRow(
            text = localizedStringResource(StringsR.string.downloads_download_options),
            icon = LineIcons.Download,
            detail = download?.error,
            onClick = onOpenOptions,
        )
        download.isDone -> MoreRow(
            text = localizedStringResource(StringsR.string.downloads_delete),
            icon = LineIcons.Delete,
            detail = localizedStringResource(StringsR.string.downloads_downloaded),
            onClick = { onDownloadAction(download) },
        )
        download.isActive -> MoreRow(
            text = localizedStringResource(StringsR.string.downloads_pause),
            icon = AppIcons.Pause,
            detail = localizedStringResource(StringsR.string.downloads_downloading, (download.progress * 100).toInt()),
            progress = download.progress,
            onClick = { onDownloadAction(download) },
        )
        else -> MoreRow(
            text = localizedStringResource(StringsR.string.downloads_resume),
            icon = LineIcons.Download,
            detail = localizedStringResource(StringsR.string.downloads_paused),
            progress = download.progress,
            onClick = { onDownloadAction(download) },
        )
    }
}

/**
 * Opciones de descarga: este episodio, los siguientes, la temporada entera o solo lo que no has visto; y la calidad
 * (lo mejor, o 1080p/720p para que ocupe menos).
 */
@Composable
private fun DownloadOptions(
    state: DetailsUiState.Content,
    downloads: List<Download>,
    onDownload: (List<Episode>, DownloadQuality) -> Unit,
) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { requester.requestFocusWithRetry() }
    var quality by remember { mutableStateOf(DownloadQuality.BEST) }
    val qualityLabel = localizedStringResource(
        when (quality) {
            DownloadQuality.BEST -> StringsR.string.downloads_quality_best
            DownloadQuality.FHD -> StringsR.string.downloads_quality_1080
            DownloadQuality.HD -> StringsR.string.downloads_quality_720
        },
    )
    MoreSectionHeader(localizedStringResource(StringsR.string.downloads_download_options))
    var assigned = false
    fun first(): Modifier = if (!assigned) { assigned = true; Modifier.focusRequester(requester) } else Modifier
    if (!state.isSeries) {
        MoreRow(
            text = localizedStringResource(StringsR.string.downloads_download_movie),
            icon = LineIcons.Download,
            onClick = { onDownload(emptyList(), quality) },
            modifier = first(),
        )
    } else {
        val today = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) }
        val season = (state.episodes as? EpisodesState.Ready)?.episodes.orEmpty()
            .filter { it.airDate.isNullOrBlank() || it.airDate!! <= today }
        val downloadedKeys = downloads.filter { it.status != DownloadStatus.FAILED }.map { it.id }.toSet()
        fun key(e: Episode) = WatchProgress.episodeKey(state.details.item.id, e.season, e.number)
        val pending = season.filter { key(it) !in downloadedKeys }
        val current = state.selectedEpisode
        if (current != null && key(current) !in downloadedKeys) {
            MoreRow(
                text = localizedStringResource(StringsR.string.downloads_option_episode, current.localizedCode()),
                icon = LineIcons.Download,
                detail = current.title,
                onClick = { onDownload(listOf(current), quality) },
                modifier = first(),
            )
        }
        if (current != null) {
            val next = season.dropWhile { it != current }.drop(1).filter { key(it) !in downloadedKeys }.take(NEXT_EPISODES)
            if (next.size > 1) {
                MoreRow(
                    text = localizedStringResource(StringsR.string.downloads_option_next, next.size),
                    icon = LineIcons.Download,
                    detail = "${next.first().localizedCode()} – ${next.last().localizedCode()}",
                    onClick = { onDownload(next, quality) },
                    modifier = first(),
                )
            }
        }
        state.selectedSeason?.let { number ->
            if (pending.isNotEmpty()) {
                MoreRow(
                    text = localizedStringResource(StringsR.string.downloads_option_season, number),
                    icon = LineIcons.Download,
                    detail = localizedStringResource(StringsR.string.downloads_option_count, pending.size),
                    onClick = { onDownload(pending, quality) },
                    modifier = first(),
                )
            }
            if (pending.isEmpty()) {
                Text(
                    localizedStringResource(StringsR.string.downloads_season_all_done, number),
                    color = Grey,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                )
            }
            val unwatched = pending.filter { state.progressOf(it)?.isWatched != true }
            if (unwatched.isNotEmpty() && unwatched.size != pending.size) {
                MoreRow(
                    text = localizedStringResource(StringsR.string.downloads_option_unwatched, number),
                    icon = LineIcons.Eye,
                    detail = localizedStringResource(StringsR.string.downloads_option_count, unwatched.size),
                    onClick = { onDownload(unwatched, quality) },
                )
            }
        }
    }
    MoreSectionHeader(localizedStringResource(StringsR.string.downloads_quality))
    MoreRow(
        text = "‹  $qualityLabel  ›",
        icon = AppIcons.Hdr,
        detail = localizedStringResource(StringsR.string.downloads_quality_hint),
        onClick = { quality = DownloadQuality.entries[(quality.ordinal + 1) % DownloadQuality.entries.size] },
        onShift = { d -> quality = DownloadQuality.entries[((quality.ordinal + d) % 3 + 3) % 3] },
        modifier = first(),
    )
}

private const val NEXT_EPISODES = 5

/** Panel lateral de la derecha (como el de fuentes), con el resto de la ficha atenuada detrás. */
@Composable
private fun MoreSidePanel(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .onBackKey(enabled = true, onBack = onBack),
    ) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(480.dp)
                .background(AppColors.BackgroundRaised.copy(alpha = 0.97f))
                .verticalScroll(rememberScrollState())
                .padding(top = 36.dp, start = 24.dp, end = 24.dp, bottom = 36.dp),
        ) {
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp),
            )
            subtitle?.let {
                Text(it, color = Grey, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp, top = 2.dp))
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun MoreSectionHeader(text: String) {
    Text(
        text.uppercase(java.util.Locale.getDefault()),
        color = Color.White.copy(alpha = 0.45f),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
        modifier = Modifier.padding(start = 12.dp, top = 18.dp, bottom = 6.dp),
    )
}

@Composable
private fun MoreRow(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    highlighted: Boolean = false,
    progress: Float? = null,
    onShift: ((Int) -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val fg = if (focused) Color.Black else Color.White
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (onShift != null) {
                    Modifier.onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionLeft -> { onShift(-1); true }
                            Key.DirectionRight -> { onShift(1); true }
                            else -> false
                        }
                    }
                } else {
                    Modifier
                },
            )
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Color.White else Color.Transparent)
            .tvClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    when {
                        focused -> Color.Black.copy(alpha = 0.08f)
                        highlighted -> AppColors.Accent.copy(alpha = 0.25f)
                        else -> Color.White.copy(alpha = 0.08f)
                    },
                ),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = if (highlighted && !focused) Color.White else fg, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(text, color = fg, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            detail?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = if (focused) Color.Black.copy(alpha = 0.6f) else Grey, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            progress?.let { p ->
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(fg.copy(alpha = 0.2f))) {
                    Box(Modifier.fillMaxWidth(p).height(3.dp).background(if (focused) Color.Black else AppColors.Accent))
                }
            }
        }
    }
}

/**
 * Long-press on an episode card opens this modal instead of silently toggling the watched flag: the
 * gesture had no feedback, so an accidental hold could mark or unmark an episode with no way to tell.
 */
@Composable
internal fun EpisodeActions(
    state: DetailsUiState.Content,
    episode: Episode,
    download: Download?,
    onDownload: () -> Unit,
    onPlay: () -> Unit,
    onToggleWatched: () -> Unit,
    onDismiss: () -> Unit,
) {
    val watched = state.progressOf(episode)?.isWatched == true
    ActionMenuDialog(
        title = listOf(episode.localizedCode(), episode.title).filter { it.isNotBlank() }.joinToString(" · "),
        onDismiss = onDismiss,
        scrimAlpha = 0.88f,
        consumeOpeningKeyRelease = true,
        actions = listOf(
            ActionMenuItem(localizedStringResource(StringsR.string.details_cancel), LineIcons.Close, onDismiss),
            ActionMenuItem(localizedStringResource(StringsR.string.details_episode_play), AppIcons.Play, onPlay),
            ActionMenuItem(
                localizedStringResource(
                    when {
                        download?.isDone == true -> StringsR.string.downloads_delete
                        download?.isActive == true -> StringsR.string.downloads_already_running
                        else -> StringsR.string.downloads_download_episode
                    },
                ),
                if (download?.isDone == true) LineIcons.Delete else LineIcons.Download,
                onDownload,
            ),
            ActionMenuItem(
                text = localizedStringResource(
                    if (episodeMenuTogglesToWatched(state, episode)) {
                        StringsR.string.details_episode_mark_watched
                    } else {
                        StringsR.string.details_episode_mark_unwatched
                    },
                ),
                icon = if (watched) AppIcons.Check else LineIcons.Eye,
                onClick = onToggleWatched,
            ),
        ),
    )
}
