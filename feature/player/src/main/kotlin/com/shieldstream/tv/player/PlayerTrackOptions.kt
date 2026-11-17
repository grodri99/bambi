package com.shieldstream.tv.player

import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import androidx.annotation.OptIn
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import java.util.Locale

// Opciones de audio y subtítulos del panel de pistas: qué se ofrece y cómo se nombra.

internal fun speedLabel(speed: Float): String =
    (if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().replace('.', ',')) + "x"

internal fun externalOption(hasSpanish: Boolean, texts: Texts, open: () -> Unit) = TrackOption(
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
internal fun hasEmbeddedSubtitle(tracks: Tracks, language: String): Boolean =
    tracks.groups.any { group ->
        group.type == C.TRACK_TYPE_TEXT &&
            (0 until group.length).any { group.getTrackFormat(it).language?.startsWith(language) == true }
    }

@OptIn(UnstableApi::class)
internal fun audioOptions(
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
internal fun textOptions(
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
    val name = Locale.forLanguageTag(code).getDisplayLanguage(TrackUiLocale)
    if (name.isBlank() || name.equals(code, ignoreCase = true)) return null
    return name.replaceFirstChar { it.titlecase(TrackUiLocale) }
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
