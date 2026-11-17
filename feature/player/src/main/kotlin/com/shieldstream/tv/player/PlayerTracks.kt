package com.shieldstream.tv.player

import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.strings.rememberTexts
import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.model.SubtitleAppearance
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.shieldstream.tv.model.ExternalSubtitle
import com.shieldstream.tv.ui.component.onBackKey
import com.shieldstream.tv.ui.component.tvClickable
import com.shieldstream.tv.ui.theme.AppColors
import java.util.Locale

internal val TrackUiLocale: Locale get() = Lang.locale

internal data class TrackOption(
    val key: String,
    val title: String,
    val detail: String,
    val selected: Boolean,
    val onSelect: () -> Unit,
)

@OptIn(UnstableApi::class)
@Composable
fun TracksPanel(
    player: Player,
    external: ExternalSubsState,
    onSearchExternal: (String) -> Unit,
    onPickExternal: (ExternalSubtitle) -> Unit,
    onCloseExternal: () -> Unit,
    externalLabel: String? = null,
    syncDetail: String = "",
    appearance: SubtitleAppearance,
    onAppearanceChange: (SubtitleAppearance) -> Unit,
    onExternalOff: () -> Unit = {},
    onManualSubtitleSelection: () -> Unit = {},
    onOpenSync: () -> Unit = {},
    playbackSpeed: Float = 1f,
    onCycleSpeed: () -> Unit = {},
    videoFit: com.shieldstream.tv.model.VideoFit = com.shieldstream.tv.model.VideoFit.FIT,
    onCycleVideoFit: () -> Unit = {},
    sleepTimer: SleepTimer = SleepTimer.Off,
    sleepTimerRemainingMinutes: Int? = null,
    onCycleSleepTimer: () -> Unit = {},
    cardStyle: Boolean = false,
    onAudioSelected: (String?) -> Unit = {},
    onSubtitleSelected: (String?) -> Unit = {},
    onSubtitlesOff: () -> Unit = {},
) {
    val texts = rememberTexts()
    var tracks by remember(player) { mutableStateOf(player.currentTracks) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    var showExternal by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            // Editando subtítulos se deja ver el vídeo (en pausa) para ver el cambio sobre la imagen real.
            .background(if (showAppearance) Color.Transparent else Color.Black.copy(alpha = 0.85f))
            .onBackKey(enabled = showExternal || showAppearance) {
                if (showExternal) {
                    showExternal = false
                    onCloseExternal()
                } else {
                    showAppearance = false
                }
            },
    ) {
        when {
            showExternal -> ExternalSubtitlesView(
                state = external,
                onSearch = onSearchExternal,
                onPick = onPickExternal,
            )
            showAppearance -> LiveSubtitleEditor(
                player = player,
                appearance = appearance,
                onChange = onAppearanceChange,
            )
            else -> {
            val hasSpanish = remember(tracks) { hasEmbeddedSubtitle(tracks, Lang.current.code) }
            val externalSubtitleText = texts.get(StringsR.string.player_external_subtitle)
            val syncSubtitlesText = texts.get(StringsR.string.player_sync_subtitles_3af0a4)
            val audio = remember(tracks) { audioOptions(tracks, player, texts, onAudioSelected) }
            val text = remember(tracks, hasSpanish, externalLabel, syncDetail, appearance) {
                buildList {
                    add(externalOption(hasSpanish, texts) { showExternal = true })
                    if (externalLabel != null) {
                        add(TrackOption("t_ext_active", externalLabel, externalSubtitleText, selected = true, onSelect = {}))
                        add(TrackOption("t_sync", syncSubtitlesText, syncDetail, selected = false, onSelect = onOpenSync))
                    }
                    addAll(
                        textOptions(
                            tracks,
                            player,
                            externalActive = externalLabel != null,
                            onExternalOff = onExternalOff,
                            onManualSelection = onManualSubtitleSelection,
                            onSubtitleSelected = onSubtitleSelected,
                            onSubtitlesOff = onSubtitlesOff,
                            texts = texts,
                        ),
                    )
                    add(
                        TrackOption(
                            key = "t_appearance",
                            title = texts.get(StringsR.string.subtitle_appearance) + " ›",
                            detail = texts.get(StringsR.string.subtitle_size_value, appearance.sizePercent) +
                                " · " + texts.get(StringsR.string.subtitle_position_value, appearance.verticalPositionPercent),
                            selected = false,
                            onSelect = { showAppearance = true },
                        ),
                    )
                }
            }
            val playback = remember(playbackSpeed, sleepTimer, sleepTimerRemainingMinutes, videoFit) {
                listOf(
                    TrackOption(
                        key = "p_speed",
                        title = texts.get(StringsR.string.player_speed),
                        detail = speedLabel(playbackSpeed) +
                            if (playbackSpeed != 1f) " · " + texts.get(StringsR.string.player_speed_passthrough_note) else "",
                        selected = playbackSpeed != 1f,
                        onSelect = onCycleSpeed,
                    ),
                    TrackOption(
                        key = "p_sleep",
                        title = texts.get(StringsR.string.player_sleep_timer),
                        detail = when (val timer = sleepTimer) {
                            SleepTimer.Off -> texts.get(StringsR.string.player_sleep_timer_off)
                            SleepTimer.EndOfEpisode -> texts.get(StringsR.string.player_sleep_timer_end_of_episode)
                            is SleepTimer.Minutes -> texts.get(
                                StringsR.string.player_sleep_timer_minutes,
                                sleepTimerRemainingMinutes ?: timer.minutes,
                            )
                        },
                        selected = sleepTimer != SleepTimer.Off,
                        onSelect = onCycleSleepTimer,
                    ),
                    TrackOption(
                        key = "p_fit",
                        title = texts.get(StringsR.string.player_video_fit),
                        detail = when (videoFit) {
                            com.shieldstream.tv.model.VideoFit.FIT -> texts.get(StringsR.string.player_video_fit_original)
                            com.shieldstream.tv.model.VideoFit.ZOOM -> texts.get(StringsR.string.player_video_fit_zoom)
                            com.shieldstream.tv.model.VideoFit.STRETCH -> texts.get(StringsR.string.player_video_fit_stretch)
                        },
                        selected = videoFit != com.shieldstream.tv.model.VideoFit.FIT,
                        onSelect = onCycleVideoFit,
                    ),
                )
            }
            TracksView(audio = audio, text = text, playback = playback, cardStyle = cardStyle)
            }
        }
        Text(
            text = if (showExternal || showAppearance) localizedStringResource(StringsR.string.player_back_to_return_to_audio_subtitles) else localizedStringResource(StringsR.string.player_back_to_return_to_the_movie),
            color = Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun TracksView(audio: List<TrackOption>, text: List<TrackOption>, playback: List<TrackOption>, cardStyle: Boolean) {
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { initialFocus.requestFocus() }
    }
    val audioFocusIndex = audio.indexOfFirst { it.selected }.coerceAtLeast(0)

    // Un único panel compacto en el centro, con las tres columnas juntas y separadas por una línea fina
    // (antes las columnas se repartían todo el ancho de la tele y quedaban muy lejos unas de otras).
    Box(Modifier.fillMaxSize().padding(bottom = 32.dp), contentAlignment = Alignment.Center) {
    // Dos estilos (Ajustes → Panel del reproductor): tarjeta oscura, o transparente sobre el vídeo atenuado, como antes.
    Row(
        Modifier
            .height(TracksPanelHeight)
            .then(if (cardStyle) Modifier.background(TracksPanelColor, RoundedCornerShape(18.dp)) else Modifier)
            .padding(horizontal = 20.dp, vertical = 22.dp),
    ) {
        OptionColumn(
            title = "Audio",
            options = audio,
            emptyText = localizedStringResource(StringsR.string.player_no_audio_tracks),
            onClick = { it.onSelect() },
            focusIndex = audioFocusIndex,
            focusRequester = initialFocus,
            modifier = Modifier.width(TrackColumnWidth),
        )
        ColumnDivider()
        OptionColumn(
            title = localizedStringResource(StringsR.string.player_subtitles),
            options = text,
            emptyText = "",
            onClick = { it.onSelect() },
            focusIndex = -1,
            focusRequester = null,
            modifier = Modifier.width(TrackColumnWidth),
        )
        ColumnDivider()
        OptionColumn(
            title = localizedStringResource(StringsR.string.player_playback),
            options = playback,
            emptyText = "",
            onClick = { it.onSelect() },
            focusIndex = -1,
            focusRequester = null,
            modifier = Modifier.width(PlaybackColumnWidth),
        )
    }
    }
}

@Composable
private fun ColumnDivider() {
    Box(
        Modifier
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .width(1.dp)
            .fillMaxHeight()
            .background(Color.White.copy(alpha = 0.10f)),
    )
}

@Composable
internal fun OptionColumn(
    title: String,
    options: List<TrackOption>,
    emptyText: String,
    onClick: (TrackOption) -> Unit,
    focusIndex: Int,
    focusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(start = 14.dp),
        )
        Spacer(Modifier.height(10.dp))
        if (options.isEmpty()) {
            Text(emptyText, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyLarge)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
                modifier = Modifier.weight(1f),
            ) {
                itemsIndexed(options, key = { _, o -> o.key }) { index, option ->
                    OptionRow(
                        option = option,
                        onClick = { onClick(option) },
                        modifier = if (index == focusIndex && focusRequester != null) {
                            Modifier.focusRequester(focusRequester)
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun OptionRow(option: TrackOption, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val fg = if (focused) Color.Black else Color.White
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) Color.White else Color.Transparent)
            .tvClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (option.selected) "✓" else "",
            color = if (focused) Color.Black else AppColors.Accent,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.width(24.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = option.title,
                color = fg,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (option.selected) FontWeight.Bold else FontWeight.Normal,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (option.detail.isNotBlank()) {
                Text(
                    text = option.detail,
                    color = if (focused) Color.Black.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// ---------------------------------------------------------------------------

private val TrackColumnWidth = 236.dp

private val PlaybackColumnWidth = 214.dp

private val TracksPanelHeight = 420.dp

private val TracksPanelColor = Color(0xF2181A20)
