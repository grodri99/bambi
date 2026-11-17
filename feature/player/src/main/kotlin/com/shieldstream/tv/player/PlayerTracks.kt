package com.shieldstream.tv.player

import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.strings.Texts
import com.shieldstream.tv.strings.rememberTexts
import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.model.SubtitleAppearance
import com.shieldstream.tv.strings.labelResource
import com.shieldstream.tv.model.cycle
import com.shieldstream.tv.model.SubtitleEdge
import com.shieldstream.tv.model.SubtitleFont
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.shieldstream.tv.model.ExternalSubtitle
import com.shieldstream.tv.model.SubtitleLanguages
import com.shieldstream.tv.strings.localizedSubtitleLanguage
import com.shieldstream.tv.ui.component.onBackKey
import com.shieldstream.tv.ui.component.tvClickable
import com.shieldstream.tv.ui.theme.AppColors
import java.util.Locale

private val UiLocale: Locale get() = Lang.locale

private data class TrackOption(
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

private fun speedLabel(speed: Float): String =
    (if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().replace('.', ',')) + "x"

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
private fun ExternalSubtitlesView(
    state: ExternalSubsState,
    onSearch: (String) -> Unit,
    onPick: (ExternalSubtitle) -> Unit,
) {
    val currentLanguage = when (state) {
        is ExternalSubsState.Searching -> state.language
        is ExternalSubsState.Results -> state.language
        is ExternalSubsState.Downloading -> state.language
        is ExternalSubsState.Error -> state.language
        else -> Lang.current.code
    }
    LaunchedEffect(Unit) {
        if (state is ExternalSubsState.Idle) onSearch(Lang.current.code)
    }
    val languageFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { languageFocus.requestFocus() }
    }

    val languageOptions = SubtitleLanguages.all.map { code ->
        TrackOption(
            key = "lang_$code",
            title = localizedSubtitleLanguage(code),
            detail = "",
            selected = code == currentLanguage,
            onSelect = { onSearch(code) },
        )
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 96.dp, vertical = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(64.dp),
    ) {
        OptionColumn(
            title = localizedStringResource(StringsR.string.player_language),
            options = languageOptions,
            emptyText = "",
            onClick = { option -> option.onSelect() },
            focusIndex = languageOptions.indexOfFirst { it.selected }.coerceAtLeast(0),
            focusRequester = languageFocus,
            modifier = Modifier.weight(0.6f),
        )
        Column(Modifier.weight(1.4f)) {
            Text(
                text = localizedStringResource(StringsR.string.player_external_subtitles),
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = "OpenSubtitles · Podnapisi · SubDL",
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(16.dp))
            when (state) {
                ExternalSubsState.Idle, is ExternalSubsState.Attached -> Unit
                is ExternalSubsState.Searching -> StatusText(localizedStringResource(
                    StringsR.string.player_searching_language,
                    localizedSubtitleLanguage(state.language).let { if (Lang.isEnglish) it else it.lowercase(UiLocale) },
                ))
                is ExternalSubsState.Downloading -> StatusText(localizedStringResource(StringsR.string.player_downloading_and_loading_into_the_video))
                is ExternalSubsState.Error -> StatusText(state.message)
                is ExternalSubsState.Results -> if (state.items.isEmpty()) {
                    StatusText(localizedStringResource(
                        StringsR.string.player_no_language_subtitles,
                        localizedSubtitleLanguage(state.language).let { if (Lang.isEnglish) it else it.lowercase(UiLocale) },
                    ))
                } else {
                    val options = state.items.mapIndexed { index, sub ->
                        TrackOption(
                            key = "ext_${sub.provider}_${sub.id}",
                            title = sub.release.ifBlank { localizedStringResource(StringsR.string.player_external_subtitle_title, sub.provider, index + 1) },
                            detail = buildList {
                                add(sub.provider)
                                if (sub.hearingImpaired) add(localizedStringResource(StringsR.string.player_sdh))
                                if (sub.matchScore >= 0.5f) add(localizedStringResource(StringsR.string.player_matches_your_release))
                            }.joinToString(" · "),
                            selected = false,
                            onSelect = { onPick(sub) },
                        )
                    }
                    Column {
                        if (state.evaluating) {
                            StatusText(
                                localizedStringResource(StringsR.string.player_evaluating_candidates, state.candidateCount, state.referenceCues),
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(bottom = 48.dp),
                        ) {
                            itemsIndexed(options, key = { _, o -> o.key }) { _, option ->
                                OptionRow(option = option, onClick = { option.onSelect() })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusText(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun OptionColumn(
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
private fun AppearanceControlRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    onShift: (Int) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val foreground = if (focused) Color.Black else Color.White
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { onShift(-1); true }
                    Key.DirectionRight -> { onShift(1); true }
                    else -> false
                }
            }
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) Color.White else Color.Transparent)
            .tvClickable(onClick = { onShift(1) })
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = title,
            color = foreground,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(text = "‹", color = foreground.copy(alpha = 0.65f), style = MaterialTheme.typography.titleMedium)
        Text(
            text = value,
            color = foreground,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Text(text = "›", color = foreground.copy(alpha = 0.65f), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun OptionRow(option: TrackOption, onClick: () -> Unit, modifier: Modifier = Modifier) {
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

private fun externalOption(hasSpanish: Boolean, texts: Texts, open: () -> Unit) = TrackOption(
    key = "t_external",
    title = texts.get(StringsR.string.player_external_subtitles_6520c3),
    detail = if (hasSpanish) {
        texts.get(StringsR.string.player_search_opensubtitles_podnapisi_and_subdl)
    } else {
        texts.get(StringsR.string.player_this_file_has_no_english_subtitles_search)
    },
    selected = false,
    onSelect = { open() },
)

@OptIn(UnstableApi::class)
private fun hasEmbeddedSubtitle(tracks: Tracks, language: String): Boolean =
    tracks.groups.any { group ->
        group.type == C.TRACK_TYPE_TEXT &&
            (0 until group.length).any { group.getTrackFormat(it).language?.startsWith(language) == true }
    }

@OptIn(UnstableApi::class)
private fun audioOptions(
    tracks: Tracks,
    player: Player,
    texts: Texts,
    onAudioSelected: (String?) -> Unit,
): List<TrackOption> = buildList {
    tracks.groups.forEachIndexed { groupIndex, group ->
        if (group.type != C.TRACK_TYPE_AUDIO) return@forEachIndexed
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            val f = group.getTrackFormat(i)
            val language = languageName(f.language)
            add(
                TrackOption(
                    key = "a_${groupIndex}_$i",
                    title = language ?: f.label ?: texts.get(StringsR.string.player_track_number, size + 1),
                    detail = listOfNotNull(
                        f.label?.takeIf { it != language },
                        audioTech(f, texts),
                    ).distinct().joinToString(" · "),
                    selected = group.isTrackSelected(i),
                    onSelect = {
                        onAudioSelected(f.language)
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                            .build()
                    },
                ),
            )
        }
    }
}

@OptIn(UnstableApi::class)
private fun textOptions(
    tracks: Tracks,
    player: Player,
    externalActive: Boolean,
    onExternalOff: () -> Unit,
    onManualSelection: () -> Unit,
    onSubtitleSelected: (String?) -> Unit,
    onSubtitlesOff: () -> Unit,
    texts: Texts,
): List<TrackOption> {
    val textGroups = tracks.groups.withIndex().filter { it.value.type == C.TRACK_TYPE_TEXT }
    val anySelected = externalActive || textGroups.any { (_, g) -> (0 until g.length).any { g.isTrackSelected(it) } }

    return buildList {
        add(
            TrackOption(
                key = "t_off",
                title = texts.get(StringsR.string.player_off),
                detail = "",
                selected = !anySelected,
                onSelect = {
                    onManualSelection()
                    onExternalOff()
                    onSubtitlesOff()
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                },
            ),
        )
        textGroups.forEach { (groupIndex, group) ->
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) continue
                val f = group.getTrackFormat(i)
                val language = languageName(f.language)
                val forced = f.selectionFlags and C.SELECTION_FLAG_FORCED != 0
                val external = f.id?.contains("ext:") == true
                add(
                    TrackOption(
                        key = "t_${groupIndex}_$i",
                        title = (language ?: f.label ?: texts.get(StringsR.string.player_subtitle_number, size)) +
                            (if (forced) texts.get(StringsR.string.player_forced) else "") +
                            (if (external) texts.get(StringsR.string.player_external) else ""),
                        detail = listOfNotNull(
                            f.label?.takeIf { it != language },
                            subtitleTech(f, texts),
                        ).distinct().joinToString(" · "),
                        selected = !externalActive && group.isTrackSelected(i),
                        onSelect = {
                            onManualSelection()
                            onExternalOff()
                            onSubtitleSelected(f.language)
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                .build()
                        },
                    ),
                )
            }
        }
    }
}

private fun languageName(code: String?): String? {
    if (code.isNullOrBlank() || code == "und") return null
    val name = Locale.forLanguageTag(code).getDisplayLanguage(UiLocale)
    if (name.isBlank() || name.equals(code, ignoreCase = true)) return null
    return name.replaceFirstChar { it.titlecase(UiLocale) }
}

private fun audioTech(f: Format, texts: Texts): String? {
    val codec = when (f.sampleMimeType) {
        MimeTypes.AUDIO_TRUEHD -> "Dolby TrueHD"
        MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Digital+ Atmos"
        MimeTypes.AUDIO_E_AC3 -> "Dolby Digital+"
        MimeTypes.AUDIO_AC3 -> "Dolby Digital"
        MimeTypes.AUDIO_DTS_HD -> "DTS-HD"
        MimeTypes.AUDIO_DTS_EXPRESS -> "DTS Express"
        MimeTypes.AUDIO_DTS -> "DTS"
        MimeTypes.AUDIO_AAC -> "AAC"
        MimeTypes.AUDIO_OPUS -> "Opus"
        MimeTypes.AUDIO_FLAC -> "FLAC"
        MimeTypes.AUDIO_MPEG -> "MP3"
        else -> null
    }
    val channels = when (f.channelCount) {
        1 -> "mono"
        2 -> texts.get(StringsR.string.player_stereo)
        6 -> "5.1"
        8 -> "7.1"
        Format.NO_VALUE -> null
        else -> "${f.channelCount} canales"
    }
    return listOfNotNull(codec, channels).joinToString(" ").ifBlank { null }
}

private fun subtitleTech(f: Format, texts: Texts): String? = when (f.codecs ?: f.sampleMimeType) {
    MimeTypes.APPLICATION_PGS -> texts.get(StringsR.string.player_pgs_image)
    MimeTypes.APPLICATION_SUBRIP -> "SRT"
    MimeTypes.TEXT_SSA -> "ASS/SSA"
    MimeTypes.TEXT_VTT -> "WebVTT"
    MimeTypes.APPLICATION_VOBSUB -> texts.get(StringsR.string.player_vobsub_image)
    else -> null
}

private val TrackColumnWidth = 236.dp
private val PlaybackColumnWidth = 214.dp
private val TracksPanelHeight = 420.dp
private val TracksPanelColor = Color(0xF2181A20)

/**
 * Editor de subtítulos en vivo: pausa el vídeo, deja la imagen a la vista y pinta un subtítulo de ejemplo con el
 * estilo actual mientras se cambian tamaño, posición, fuente, colores, fondo y borde. Al salir, si estaba
 * reproduciendo, sigue.
 */
@OptIn(UnstableApi::class)
@Composable
private fun LiveSubtitleEditor(
    player: Player,
    appearance: SubtitleAppearance,
    onChange: (SubtitleAppearance) -> Unit,
) {
    val texts = rememberTexts()
    DisposableEffect(player) {
        val wasPlaying = player.isPlaying || player.playWhenReady
        player.pause()
        onDispose { if (wasPlaying) player.play() }
    }
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { initialFocus.requestFocus() }
    }
    val sample = texts.get(StringsR.string.subtitle_sample)
    Box(Modifier.fillMaxSize()) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx -> androidx.media3.ui.SubtitleView(ctx) },
            update = { view ->
                view.applySubtitleAppearance(appearance.copy(applyEmbeddedStyles = false))
                view.setCues(listOf(androidx.media3.common.text.Cue.Builder().setText(sample).build()))
            },
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            Modifier
                .fillMaxHeight()
                .width(400.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.92f),
                        0.85f to Color.Black.copy(alpha = 0.75f),
                        1f to Color.Transparent,
                    ),
                )
                .padding(start = 40.dp, end = 32.dp, top = 40.dp),
        ) {
            Text(
                text = localizedStringResource(StringsR.string.subtitle_live_title),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(start = 14.dp),
            )
            Text(
                text = localizedStringResource(StringsR.string.subtitle_live_hint),
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 14.dp, top = 4.dp, bottom = 12.dp),
            )
            val a = appearance
            AppearanceControlRow(
                title = localizedStringResource(StringsR.string.subtitle_size),
                value = localizedStringResource(StringsR.string.subtitle_size_value, a.sizePercent),
                modifier = Modifier.focusRequester(initialFocus),
                onShift = { d ->
                    onChange(a.copy(sizePercent = (a.sizePercent + d * SubtitleAppearance.SIZE_STEP).coerceIn(SubtitleAppearance.MIN_SIZE, SubtitleAppearance.MAX_SIZE)))
                },
            )
            AppearanceControlRow(
                title = localizedStringResource(StringsR.string.subtitle_position),
                value = localizedStringResource(StringsR.string.subtitle_position_value, a.verticalPositionPercent),
                onShift = { d ->
                    onChange(a.copy(verticalPositionPercent = (a.verticalPositionPercent + d * SubtitleAppearance.POSITION_STEP).coerceIn(SubtitleAppearance.MIN_POSITION, SubtitleAppearance.MAX_POSITION)))
                },
            )
            AppearanceControlRow(
                title = localizedStringResource(StringsR.string.subtitle_font),
                value = localizedStringResource(a.font.labelResource),
                onShift = { d -> onChange(a.copy(font = a.font.cycle(SubtitleFont.entries, d))) },
            )
            AppearanceControlRow(
                title = localizedStringResource(StringsR.string.subtitle_text_color),
                value = localizedStringResource(a.textColor.labelResource),
                onShift = { d -> onChange(a.copy(textColor = a.textColor.cycle(SubtitleAppearance.textColors, d))) },
            )
            AppearanceControlRow(
                title = localizedStringResource(StringsR.string.subtitle_background_color),
                value = localizedStringResource(a.backgroundColor.labelResource),
                onShift = { d -> onChange(a.copy(backgroundColor = a.backgroundColor.cycle(SubtitleAppearance.backgroundColors, d))) },
            )
            AppearanceControlRow(
                title = localizedStringResource(StringsR.string.subtitle_outline_color),
                value = localizedStringResource(a.outlineColor.labelResource),
                onShift = { d -> onChange(a.copy(outlineColor = a.outlineColor.cycle(SubtitleAppearance.outlineColors, d))) },
            )
            AppearanceControlRow(
                title = localizedStringResource(StringsR.string.subtitle_edge),
                value = localizedStringResource(a.edge.labelResource),
                onShift = { d -> onChange(a.copy(edge = a.edge.cycle(SubtitleEdge.entries, d))) },
            )
            AppearanceControlRow(
                title = localizedStringResource(StringsR.string.subtitle_embedded_style),
                value = localizedStringResource(
                    if (a.applyEmbeddedStyles) StringsR.string.subtitle_style_original else StringsR.string.subtitle_style_custom,
                ),
                onShift = { onChange(a.copy(applyEmbeddedStyles = !a.applyEmbeddedStyles)) },
            )
            AppearanceControlRow(
                title = localizedStringResource(StringsR.string.subtitle_reset),
                value = "",
                onShift = { onChange(SubtitleAppearance()) },
            )
        }
    }
}
