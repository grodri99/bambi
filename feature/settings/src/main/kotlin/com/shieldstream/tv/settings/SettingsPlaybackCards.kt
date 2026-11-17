package com.shieldstream.tv.settings

import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.model.DeviceCapabilities
import com.shieldstream.tv.model.DeviceOptimization
import com.shieldstream.tv.model.MemoryProfile
import com.shieldstream.tv.model.Quality
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shieldstream.tv.ui.theme.DisplayFont

// Ajustes → reproducción: siguiente episodio, fuentes al pulsar Reproducir, calidad, búfer, frecuencia de la pantalla y avance.

@Composable
internal fun AutoNextCard(viewModel: SettingsViewModel = hiltViewModel()) {
    val enabled by viewModel.autoNextEpisode.collectAsStateWithLifecycle()
    FocusCard(
        onClick = { viewModel.setAutoNextEpisode(!enabled) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { viewModel.setAutoNextEpisode(true); true }
                Key.DirectionRight -> { viewModel.setAutoNextEpisode(false); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_autoplay_next_episode),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    localizedStringResource(StringsR.string.settings_shows_during_the_credits_next_episode_appears),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettingsMuted,
                )
            }
            Spacer(Modifier.width(24.dp))
            Row(
                Modifier
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                    .padding(4.dp),
            ) {
                SegmentOption(localizedStringResource(StringsR.string.settings_on), selected = enabled)
                SegmentOption(localizedStringResource(StringsR.string.settings_off_9847b4), selected = !enabled)
            }
        }
    }
}

@Composable
internal fun SwitchPickedSourceCard(viewModel: SettingsViewModel = hiltViewModel()) {
    val enabled by viewModel.switchFromPickedSource.collectAsStateWithLifecycle()
    FocusCard(
        onClick = { viewModel.setSwitchFromPickedSource(!enabled) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { viewModel.setSwitchFromPickedSource(true); true }
                Key.DirectionRight -> { viewModel.setSwitchFromPickedSource(false); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_switch_picked_source),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    localizedStringResource(StringsR.string.settings_switch_picked_source_detail),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettingsMuted,
                )
            }
            Spacer(Modifier.width(24.dp))
            Row(
                Modifier
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                    .padding(4.dp),
            ) {
                SegmentOption(localizedStringResource(StringsR.string.settings_on), selected = enabled)
                SegmentOption(localizedStringResource(StringsR.string.settings_off_9847b4), selected = !enabled)
            }
        }
    }
}

@Composable
internal fun TracksPanelStyleCard(viewModel: SettingsViewModel = hiltViewModel()) {
    val enabled by viewModel.tracksPanelCard.collectAsStateWithLifecycle()
    FocusCard(
        onClick = { viewModel.setTracksPanelCard(!enabled) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { viewModel.setTracksPanelCard(false); true }
                Key.DirectionRight -> { viewModel.setTracksPanelCard(true); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_tracks_panel),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    localizedStringResource(StringsR.string.settings_tracks_panel_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettingsMuted,
                )
            }
            Spacer(Modifier.width(24.dp))
            Row(
                Modifier
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                    .padding(4.dp),
            ) {
                SegmentOption(localizedStringResource(StringsR.string.settings_tracks_panel_transparent), selected = !enabled)
                SegmentOption(localizedStringResource(StringsR.string.settings_tracks_panel_card), selected = enabled)
            }
        }
    }
}

@Composable
internal fun AutoSkipIntroCard(viewModel: SettingsViewModel = hiltViewModel()) {
    val enabled by viewModel.autoSkipIntro.collectAsStateWithLifecycle()
    FocusCard(
        onClick = { viewModel.setAutoSkipIntro(!enabled) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { viewModel.setAutoSkipIntro(true); true }
                Key.DirectionRight -> { viewModel.setAutoSkipIntro(false); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_auto_skip_intro),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    localizedStringResource(StringsR.string.settings_auto_skip_intro_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettingsMuted,
                )
            }
            Spacer(Modifier.width(24.dp))
            Row(
                Modifier
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                    .padding(4.dp),
            ) {
                SegmentOption(localizedStringResource(StringsR.string.settings_on), selected = enabled)
                SegmentOption(localizedStringResource(StringsR.string.settings_off_9847b4), selected = !enabled)
            }
        }
    }
}

@Composable
internal fun MatchContentFrameRateCard(enabled: Boolean, onChange: (Boolean) -> Unit) {
    FocusCard(
        onClick = { onChange(!enabled) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { onChange(true); true }
                Key.DirectionRight -> { onChange(false); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_match_content_frame_rate),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    localizedStringResource(StringsR.string.settings_match_content_frame_rate_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettingsMuted,
                )
            }
            Spacer(Modifier.width(24.dp))
            Row(
                Modifier
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                    .padding(4.dp),
            ) {
                SegmentOption(localizedStringResource(StringsR.string.settings_on), selected = enabled)
                SegmentOption(localizedStringResource(StringsR.string.settings_off_9847b4), selected = !enabled)
            }
        }
    }
}

@Composable
internal fun ExperimentalSyncCard(enabled: Boolean, onChange: (Boolean) -> Unit) {
    FocusCard(
        onClick = { onChange(!enabled) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { onChange(true); true }
                Key.DirectionRight -> { onChange(false); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_experimental_subtitle_sync),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    localizedStringResource(StringsR.string.settings_sync_demux_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettingsMuted,
                )
            }
            Spacer(Modifier.width(24.dp))
            Row(
                Modifier
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                    .padding(4.dp),
            ) {
                SegmentOption(localizedStringResource(StringsR.string.settings_on), selected = enabled)
                SegmentOption(localizedStringResource(StringsR.string.settings_off_9847b4), selected = !enabled)
            }
        }
    }
}

@Composable
internal fun DeviceOptimizationCard(
    optimization: DeviceOptimization,
    capabilities: DeviceCapabilities,
    memoryProfile: MemoryProfile,
    onChange: (DeviceOptimization) -> Unit,
) {
    FocusCard(
        onClick = {
            onChange(if (optimization == DeviceOptimization.AUTOMATIC) DeviceOptimization.CUSTOM else DeviceOptimization.AUTOMATIC)
        },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { onChange(DeviceOptimization.AUTOMATIC); true }
                Key.DirectionRight -> { onChange(DeviceOptimization.CUSTOM); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_device_optimization),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    if (optimization == DeviceOptimization.AUTOMATIC) {
                        localizedStringResource(StringsR.string.settings_bambi_adapts_memory_buffering_thumbnails_and_sources)
                    } else {
                        localizedStringResource(StringsR.string.settings_uses_your_manual_settings_and_shield_compatible)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettingsMuted,
                )
                val codecs = capabilities.hardwareVideoCodecs
                    .sortedBy { it.ordinal }
                    .joinToString(" · ") { it.label }
                    .ifBlank { localizedStringResource(StringsR.string.settings_none_detected) }
                val hdr = capabilities.supportedHdr
                    .sortedBy { it.rank }
                    .joinToString(" · ") { it.label }
                    .ifBlank { localizedStringResource(StringsR.string.settings_no_hdr_detected) }
                val memory = when {
                    optimization == DeviceOptimization.AUTOMATIC && memoryProfile == MemoryProfile.CONSTRAINED ->
                        " · " + localizedStringResource(StringsR.string.settings_constrained_memory)
                    else -> ""
                }
                Text(
                    "${capabilities.model} · ${capabilities.heapLimitBytes / (1024 * 1024)} MB$memory\n$codecs\n$hdr",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.width(24.dp))
            Row(
                Modifier
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                    .padding(4.dp),
            ) {
                SegmentOption(localizedStringResource(StringsR.string.settings_automatic), selected = optimization == DeviceOptimization.AUTOMATIC)
                SegmentOption(localizedStringResource(StringsR.string.settings_custom), selected = optimization == DeviceOptimization.CUSTOM)
            }
        }
    }
}

@Composable
internal fun SeekModeCard(thumbnails: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    FocusCard(
        onClick = { if (enabled) onChange(!thumbnails) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { onChange(true); true }
                Key.DirectionRight -> { onChange(false); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_seeking_in_the_player),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    if (!enabled) {
                        localizedStringResource(StringsR.string.settings_chosen_automatically_for_this_device)
                    } else if (thumbnails) {
                        localizedStringResource(StringsR.string.settings_pause_and_show_thumbnails_of_the_chosen)
                    } else {
                        localizedStringResource(StringsR.string.settings_skip_forward_or_back_10_s_instantly)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettingsMuted,
                )
            }
            Spacer(Modifier.width(24.dp))
            Row(
                Modifier
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                    .padding(4.dp),
            ) {
                SegmentOption(localizedStringResource(StringsR.string.settings_thumbnails), selected = thumbnails)
                SegmentOption(localizedStringResource(StringsR.string.settings_10_s_skips), selected = !thumbnails)
            }
        }
    }
}

@Composable
internal fun qualityLabel(quality: Quality): String = if (quality == Quality.UNKNOWN) {
    localizedStringResource(StringsR.string.settings_unknown_quality)
} else {
    quality.label
}

@Composable
internal fun BufferCard(seconds: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    FocusCard(
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> {
                    if (seconds > MIN_BUFFER) onChange(-10)
                    true
                }
                Key.DirectionRight -> {
                    if (seconds < MAX_BUFFER) onChange(10)
                    true
                }
                else -> false
            }
        },
    ) { focused ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_player_buffer),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    when {
                        !enabled -> localizedStringResource(StringsR.string.settings_chosen_automatically_from_the_available_memory)
                        seconds <= 40 -> localizedStringResource(StringsR.string.settings_lightweight_5_s_back_buffer_up_to)
                        else -> localizedStringResource(StringsR.string.settings_seconds_of_video_buffered_ahead_use_to)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettingsMuted,
                )
            }
            Spacer(Modifier.width(24.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Arrow("◀", enabled = enabled && focused && seconds > MIN_BUFFER)
                    Text(
                        "$seconds s",
                        fontFamily = DisplayFont,
                        fontSize = 34.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 18.dp),
                    )
                    Arrow("▶", enabled = enabled && focused && seconds < MAX_BUFFER)
                }
                Spacer(Modifier.height(6.dp))
                if (enabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (MIN_BUFFER..MAX_BUFFER step 10).forEach { step ->
                            Box(
                                Modifier
                                    .width(22.dp)
                                    .height(4.dp)
                                    .background(
                                        if (step <= seconds) Color.White else Color.White.copy(alpha = 0.18f),
                                        RoundedCornerShape(2.dp),
                                    ),
                            )
                        }
                    }
                } else {
                    Text(localizedStringResource(StringsR.string.settings_automatic_8e7638), style = MaterialTheme.typography.bodySmall, color = SettingsMuted)
                }
            }
        }
    }
}

private const val MIN_BUFFER = 20

private const val MAX_BUFFER = 100

/** Ajustes → Al pulsar Reproducir: calidad primero, castellano primero o solo castellano (del perfil activo). */
@Composable
internal fun PlaybackPriorityCard(viewModel: SettingsViewModel = hiltViewModel()) {
    val priority by viewModel.playbackPriority.collectAsStateWithLifecycle()
    val all = com.shieldstream.tv.model.PlaybackPriority.entries
    CyclingSettingCard(
        title = localizedStringResource(StringsR.string.settings_play_priority),
        hint = localizedStringResource(StringsR.string.settings_play_priority_hint),
        value = localizedStringResource(
            when (priority) {
                com.shieldstream.tv.model.PlaybackPriority.QUALITY_FIRST -> StringsR.string.settings_play_priority_quality
                com.shieldstream.tv.model.PlaybackPriority.SPANISH_FIRST -> StringsR.string.settings_play_priority_spanish
                com.shieldstream.tv.model.PlaybackPriority.SPANISH_ONLY -> StringsR.string.settings_play_priority_spanish_only
            },
        ),
        onShift = { d -> viewModel.setPlaybackPriority(all[((all.indexOf(priority) + d) % all.size + all.size) % all.size]) },
    )
}

/** Ajustes → Calidad preferida: la mejor que no pase de este tope va primero. */
@Composable
internal fun PreferredQualityCard(viewModel: SettingsViewModel = hiltViewModel()) {
    val quality by viewModel.preferredQuality.collectAsStateWithLifecycle()
    val all = com.shieldstream.tv.model.PreferredQuality.entries
    CyclingSettingCard(
        title = localizedStringResource(StringsR.string.settings_preferred_quality),
        hint = localizedStringResource(StringsR.string.settings_preferred_quality_hint),
        value = localizedStringResource(
            when (quality) {
                com.shieldstream.tv.model.PreferredQuality.DV_4K -> StringsR.string.settings_quality_dv_4k
                com.shieldstream.tv.model.PreferredQuality.HDR_4K -> StringsR.string.settings_quality_hdr_4k
                com.shieldstream.tv.model.PreferredQuality.UHD_SDR -> StringsR.string.settings_quality_4k_sdr
                com.shieldstream.tv.model.PreferredQuality.FHD -> StringsR.string.settings_quality_1080
                com.shieldstream.tv.model.PreferredQuality.HD -> StringsR.string.settings_quality_720
            },
        ),
        onShift = { d -> viewModel.setPreferredQuality(all[((all.indexOf(quality) + d) % all.size + all.size) % all.size]) },
    )
}
