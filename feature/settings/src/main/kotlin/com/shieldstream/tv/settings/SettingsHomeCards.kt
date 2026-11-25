package com.shieldstream.tv.settings

import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.shieldstream.tv.model.AppLanguage
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.ui.component.tvClickable
import com.shieldstream.tv.ui.theme.DisplayFont

// Ajustes → Inicio, apariencia, idioma, perfiles y avisos.

@Composable
internal fun FamilyRequestsCard(count: Int, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (focused) CardFocused else CardColor, CardShape)
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.08f), CardShape)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                localizedStringResource(StringsR.string.settings_family_requests),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                localizedStringResource(StringsR.string.settings_family_requests_pending, count),
                style = MaterialTheme.typography.bodyMedium,
                color = SettingsMuted,
            )
        }
        Text("OK ›", color = SettingsMuted, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
internal fun HomeRowLimitCard(viewModel: SettingsViewModel = hiltViewModel()) {
    val limit by viewModel.homeRowItemLimit.collectAsStateWithLifecycle()
    FocusCard(
        onClick = { viewModel.changeHomeRowItemLimit(SettingsRepository.HOME_ROW_ITEM_LIMIT_STEP) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> {
                    if (limit > SettingsRepository.MIN_HOME_ROW_ITEM_LIMIT) {
                        viewModel.changeHomeRowItemLimit(-SettingsRepository.HOME_ROW_ITEM_LIMIT_STEP)
                    }
                    true
                }
                Key.DirectionRight -> {
                    if (limit < SettingsRepository.MAX_HOME_ROW_ITEM_LIMIT) {
                        viewModel.changeHomeRowItemLimit(SettingsRepository.HOME_ROW_ITEM_LIMIT_STEP)
                    }
                    true
                }
                else -> false
            }
        },
    ) { focused ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_items_per_home_row),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    localizedStringResource(StringsR.string.settings_items_per_home_row_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettingsMuted,
                )
            }
            Spacer(Modifier.width(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Arrow("◀", enabled = focused && limit > SettingsRepository.MIN_HOME_ROW_ITEM_LIMIT)
                Text(
                    "$limit",
                    fontFamily = DisplayFont,
                    fontSize = 34.sp,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
                Arrow("▶", enabled = focused && limit < SettingsRepository.MAX_HOME_ROW_ITEM_LIMIT)
            }
        }
    }
}

/** Reduce image decoding, background changes and focus animations on low-power TV boxes. */
@Composable
internal fun LightweightModeCard(viewModel: SettingsViewModel = hiltViewModel()) {
    val enabled by viewModel.lightweightUi.collectAsStateWithLifecycle()
    FocusCard(
        onClick = { viewModel.setLightweightUi(!enabled) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { viewModel.setLightweightUi(true); true }
                Key.DirectionRight -> { viewModel.setLightweightUi(false); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_lightweight_ui),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    localizedStringResource(StringsR.string.settings_smaller_backdrops_no_automatic_rotation_or_focus),
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

/** Blur the backdrop behind Home rows so the cards stand out. */
@Composable
internal fun SoftBackdropsCard(viewModel: SettingsViewModel = hiltViewModel()) {
    val enabled by viewModel.softBackdrops.collectAsStateWithLifecycle()
    OnOffCard(
        title = localizedStringResource(StringsR.string.settings_soft_backdrops),
        summary = localizedStringResource(StringsR.string.settings_soft_backdrops_summary),
        enabled = enabled,
        onChange = viewModel::setSoftBackdrops,
    )
}

/** Intro de Bambi: siempre al abrir, o solo cuando hace falta (arranque o Inicio por recargar). */
@Composable
internal fun IntroAlwaysCard(viewModel: SettingsViewModel = hiltViewModel()) {
    val enabled by viewModel.introAlways.collectAsStateWithLifecycle()
    OnOffCard(
        title = localizedStringResource(StringsR.string.settings_intro_always),
        summary = localizedStringResource(StringsR.string.settings_intro_always_summary),
        enabled = enabled,
        onChange = viewModel::setIntroAlways,
    )
}

/** Load the featured carousel and sharp Home backdrops at TMDB's original size. */
@Composable
internal fun HighResBackdropsCard(viewModel: SettingsViewModel = hiltViewModel()) {
    val enabled by viewModel.highResBackdrops.collectAsStateWithLifecycle()
    OnOffCard(
        title = localizedStringResource(StringsR.string.settings_high_res_backdrops),
        summary = localizedStringResource(StringsR.string.settings_high_res_backdrops_summary),
        enabled = enabled,
        onChange = viewModel::setHighResBackdrops,
    )
}

/** Tráiler mudo sobre el destacado, como en Netflix. */
@Composable
internal fun BillboardTrailersCard(viewModel: SettingsViewModel = hiltViewModel()) {
    val enabled by viewModel.billboardTrailers.collectAsStateWithLifecycle()
    OnOffCard(
        title = localizedStringResource(StringsR.string.settings_billboard_trailers),
        summary = localizedStringResource(StringsR.string.settings_billboard_trailers_summary),
        enabled = enabled,
        onChange = viewModel::setBillboardTrailers,
    )
}

@Composable
internal fun ShowCastCard(viewModel: SettingsViewModel = hiltViewModel()) {
    val enabled by viewModel.showCast.collectAsStateWithLifecycle()
    FocusCard(
        onClick = { viewModel.setShowCast(!enabled) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { viewModel.setShowCast(true); true }
                Key.DirectionRight -> { viewModel.setShowCast(false); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_show_cast),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    localizedStringResource(StringsR.string.settings_show_cast_hint),
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
internal fun EpisodeNotificationsCard(viewModel: SettingsViewModel = hiltViewModel()) {
    val enabled by viewModel.episodeNotifications.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.setEpisodeNotifications(true)
    }
    fun enable() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setEpisodeNotifications(true)
        }
    }
    FocusCard(
        onClick = { if (enabled) viewModel.setEpisodeNotifications(false) else enable() },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { enable(); true }
                Key.DirectionRight -> { viewModel.setEpisodeNotifications(false); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_new_episode_notifications),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    localizedStringResource(StringsR.string.settings_followed_shows_only_checks_for_releases_every),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettingsMuted,
                )
            }
            Spacer(Modifier.width(24.dp))
            Row(Modifier.background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50)).padding(4.dp)) {
                SegmentOption(localizedStringResource(StringsR.string.settings_on), selected = enabled)
                SegmentOption(localizedStringResource(StringsR.string.settings_off_9847b4), selected = !enabled)
            }
        }
    }
}

@Composable
internal fun LanguageCard(current: AppLanguage, onApply: (AppLanguage) -> Unit) {
    var pending by remember(current) { mutableStateOf(current) }
    FocusCard(
        onClick = { if (pending != current) onApply(pending) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { pending = AppLanguage.ES; true }
                Key.DirectionRight -> { pending = AppLanguage.EN; true }
                else -> false
            }
        },
    ) { focused ->
        LaunchedEffect(focused) { if (!focused) pending = current }
        val changing = pending != current
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_app_language),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    if (changing) {
                        if (pending == AppLanguage.EN) "OK to switch to English: the app will restart." else "OK para cambiar a español: la app se reiniciará."
                    } else {
                        localizedStringResource(StringsR.string.settings_menus_titles_and_synopses_and_preferred_audio)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (changing) SettingsAmber else SettingsMuted,
                )
            }
            Spacer(Modifier.width(24.dp))
            Row(
                Modifier
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                    .padding(4.dp),
            ) {
                AppLanguage.entries.forEach { SegmentOption(it.label, selected = it == pending) }
            }
        }
    }
}

@Composable
internal fun AboutCard() {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }
    val testing = context.packageName.endsWith(".debug")
    FocusCard { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (testing) localizedStringResource(StringsR.string.settings_bambi_testing) else "Bambi",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    localizedStringResource(StringsR.string.settings_catalog_by_tmdb_sources_from_torrentio_playback),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettingsMuted,
                )
            }
            Text(localizedStringResource(StringsR.string.settings_version, version), style = MaterialTheme.typography.titleMedium, color = SettingsMuted)
        }
    }
}
