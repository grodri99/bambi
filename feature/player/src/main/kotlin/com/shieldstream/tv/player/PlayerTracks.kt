package com.shieldstream.tv.player

import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.model.tr
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
import com.shieldstream.tv.ui.component.onBackKey
import com.shieldstream.tv.ui.component.tvClickable
import java.util.Locale

private val UiLocale: Locale get() = Lang.locale
private val NetflixRed = Color(0xFFE50914)

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
    onExternalOff: () -> Unit = {},
    onManualSubtitleSelection: () -> Unit = {},
    onOpenSync: () -> Unit = {},
) {
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

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .onBackKey(enabled = showExternal) {
                showExternal = false
                onCloseExternal()
            },
    ) {
        if (showExternal) {
            ExternalSubtitlesView(
                state = external,
                onSearch = onSearchExternal,
                onPick = onPickExternal,
            )
        } else {
            val hasSpanish = remember(tracks) { hasEmbeddedSubtitle(tracks, Lang.current.code) }
            val audio = remember(tracks) { audioOptions(tracks, player) }
            val text = remember(tracks, hasSpanish, externalLabel, syncDetail) {
                buildList {
                    add(externalOption(hasSpanish) { showExternal = true })
                    if (externalLabel != null) {
                        add(TrackOption("t_ext_active", externalLabel, tr("Subtítulo externo", "External subtitle"), selected = true, onSelect = {}))
                        add(TrackOption("t_sync", tr("Sincronizar subtítulos ›", "Sync subtitles ›"), syncDetail, selected = false, onSelect = onOpenSync))
                    }
                    addAll(
                        textOptions(
                            tracks,
                            player,
                            externalActive = externalLabel != null,
                            onExternalOff = onExternalOff,
                            onManualSelection = onManualSubtitleSelection,
                        ),
                    )
                }
            }
            TracksView(audio = audio, text = text)
        }
        Text(
            text = if (showExternal) tr("Atrás para volver a Audio y subtítulos", "Back to return to Audio & subtitles") else tr("Atrás para volver a la película", "Back to return to the movie"),
            color = Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun TracksView(audio: List<TrackOption>, text: List<TrackOption>) {
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { initialFocus.requestFocus() }
    }
    val audioFocusIndex = audio.indexOfFirst { it.selected }.coerceAtLeast(0)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 96.dp, vertical = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(64.dp),
    ) {
        OptionColumn(
            title = "Audio",
            options = audio,
            emptyText = tr("Sin pistas de audio", "No audio tracks"),
            onClick = { it.onSelect() },
            focusIndex = audioFocusIndex,
            focusRequester = initialFocus,
            modifier = Modifier.weight(1f),
        )
        OptionColumn(
            title = tr("Subtítulos", "Subtitles"),
            options = text,
            emptyText = "",
            onClick = { it.onSelect() },
            focusIndex = -1,
            focusRequester = null,
            modifier = Modifier.weight(1f),
        )
    }
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

    val languageOptions = SubtitleLanguages.all.map { (code, label) ->
        TrackOption(
            key = "lang_$code",
            title = label,
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
            title = tr("Idioma", "Language"),
            options = languageOptions,
            emptyText = "",
            onClick = { option -> option.onSelect() },
            focusIndex = languageOptions.indexOfFirst { it.selected }.coerceAtLeast(0),
            focusRequester = languageFocus,
            modifier = Modifier.weight(0.6f),
        )
        Column(Modifier.weight(1.4f)) {
            Text(
                text = tr("Subtítulos externos", "External subtitles"),
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
                is ExternalSubsState.Searching -> StatusText(tr("Buscando subtítulos en ${SubtitleLanguages.label(state.language).lowercase(UiLocale)}…", "Searching ${SubtitleLanguages.label(state.language)} subtitles…"))
                is ExternalSubsState.Downloading -> StatusText(tr("Descargando y cargando en el vídeo…", "Downloading and loading into the video…"))
                is ExternalSubsState.Error -> StatusText(state.message)
                is ExternalSubsState.Results -> if (state.items.isEmpty()) {
                    StatusText(tr("No se han encontrado subtítulos en ${SubtitleLanguages.label(state.language).lowercase(UiLocale)}", "No ${SubtitleLanguages.label(state.language)} subtitles found"))
                } else {
                    val options = state.items.mapIndexed { index, sub ->
                        TrackOption(
                            key = "ext_${sub.provider}_${sub.id}",
                            title = sub.release.ifBlank { tr("${sub.provider} · subtítulo ${index + 1}", "${sub.provider} · subtitle ${index + 1}") },
                            detail = buildList {
                                add(sub.provider)
                                if (sub.hearingImpaired) add(tr("para sordos", "SDH"))
                                if (sub.matchScore >= 0.5f) add(tr("coincide con tu versión", "matches your release"))
                            }.joinToString(" · "),
                            selected = false,
                            onSelect = { onPick(sub) },
                        )
                    }
                    Column {
                        if (state.evaluating) {
                            StatusText(
                                tr(
                                    "Probando ${state.candidateCount} candidatos · ${state.referenceCues} frases de referencia",
                                    "Testing ${state.candidateCount} candidates · ${state.referenceCues} reference cues",
                                ),
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
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(16.dp))
        if (options.isEmpty()) {
            Text(emptyText, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyLarge)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 48.dp),
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (option.selected) "✓" else "",
            color = if (focused) Color.Black else NetflixRed,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.width(28.dp),
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

private fun externalOption(hasSpanish: Boolean, open: () -> Unit) = TrackOption(
    key = "t_external",
    title = tr("Subtítulos externos ›", "External subtitles ›"),
    detail = if (hasSpanish) {
        tr("Buscar en OpenSubtitles, Podnapisi y SubDL", "Search OpenSubtitles, Podnapisi and SubDL")
    } else {
        tr("Este archivo no trae subtítulos en español: búscalos aquí", "This file has no English subtitles: search for them here")
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
private fun audioOptions(tracks: Tracks, player: Player): List<TrackOption> = buildList {
    tracks.groups.forEachIndexed { groupIndex, group ->
        if (group.type != C.TRACK_TYPE_AUDIO) return@forEachIndexed
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            val f = group.getTrackFormat(i)
            val language = languageName(f.language)
            add(
                TrackOption(
                    key = "a_${groupIndex}_$i",
                    title = language ?: f.label ?: tr("Pista ${size + 1}", "Track ${size + 1}"),
                    detail = listOfNotNull(
                        f.label?.takeIf { it != language },
                        audioTech(f),
                    ).distinct().joinToString(" · "),
                    selected = group.isTrackSelected(i),
                    onSelect = {
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
): List<TrackOption> {
    val textGroups = tracks.groups.withIndex().filter { it.value.type == C.TRACK_TYPE_TEXT }
    val anySelected = externalActive || textGroups.any { (_, g) -> (0 until g.length).any { g.isTrackSelected(it) } }

    return buildList {
        add(
            TrackOption(
                key = "t_off",
                title = tr("Desactivados", "Off"),
                detail = "",
                selected = !anySelected,
                onSelect = {
                    onManualSelection()
                    onExternalOff()
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
                        title = (language ?: f.label ?: tr("Subtítulo $size", "Subtitle $size")) +
                            (if (forced) tr(" (forzados)", " (forced)") else "") +
                            (if (external) tr(" · externo", " · external") else ""),
                        detail = listOfNotNull(
                            f.label?.takeIf { it != language },
                            subtitleTech(f),
                        ).distinct().joinToString(" · "),
                        selected = !externalActive && group.isTrackSelected(i),
                        onSelect = {
                            onManualSelection()
                            onExternalOff()
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

private fun audioTech(f: Format): String? {
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
        2 -> tr("estéreo", "stereo")
        6 -> "5.1"
        8 -> "7.1"
        Format.NO_VALUE -> null
        else -> "${f.channelCount} canales"
    }
    return listOfNotNull(codec, channels).joinToString(" ").ifBlank { null }
}

private fun subtitleTech(f: Format): String? = when (f.codecs ?: f.sampleMimeType) {
    MimeTypes.APPLICATION_PGS -> tr("PGS (imagen)", "PGS (image)")
    MimeTypes.APPLICATION_SUBRIP -> "SRT"
    MimeTypes.TEXT_SSA -> "ASS/SSA"
    MimeTypes.TEXT_VTT -> "WebVTT"
    MimeTypes.APPLICATION_VOBSUB -> tr("VobSub (imagen)", "VobSub (image)")
    else -> null
}
