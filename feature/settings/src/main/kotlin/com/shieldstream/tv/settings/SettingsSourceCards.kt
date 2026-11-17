package com.shieldstream.tv.settings

import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.strings.sectionResource
import com.shieldstream.tv.domain.SourceProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shieldstream.tv.ui.component.tvClickable

// Ajustes → fuentes: buscadores activos, exclusiones y fuentes rotas.

@Composable
internal fun BrokenSourcesCard(count: Int, onRestore: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (focused) CardFocused else CardColor, CardShape)
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.08f), CardShape)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onRestore)
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                localizedStringResource(StringsR.string.settings_broken_sources, count),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                localizedStringResource(StringsR.string.settings_broken_sources_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = SettingsMuted,
            )
        }
        Text(localizedStringResource(StringsR.string.settings_restore), color = SettingsMuted, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
internal fun acceptedSummary(acceptedLabels: List<String>, acceptsAll: Boolean): String = when {
    acceptedLabels.isEmpty() -> localizedStringResource(StringsR.string.settings_accepts_none)
    acceptsAll -> localizedStringResource(StringsR.string.settings_accepts_all)
    else -> localizedStringResource(StringsR.string.settings_accepts_values, acceptedLabels.joinToString(" · "))
}

@Composable
internal fun SourceExclusionGroupCard(title: String, summary: String, expanded: Boolean, onClick: () -> Unit) {
    FocusCard(onClick = onClick) { focused ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettingsMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(24.dp))
            Text(
                text = if (expanded) "▾" else "▸",
                style = MaterialTheme.typography.headlineSmall,
                color = if (focused) Color.White else SettingsMuted,
            )
        }
    }
}

@Composable
internal fun SourceExclusionCard(label: String, excluded: Boolean, onClick: () -> Unit) {
    FocusCard(onClick = onClick) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(24.dp))
            SegmentOption(
                localizedStringResource(
                    if (excluded) StringsR.string.settings_excluded else StringsR.string.settings_shown,
                ),
                selected = excluded,
            )
        }
    }
}

@Composable
internal fun ProviderCard(provider: SourceProvider, enabled: Boolean, onClick: () -> Unit) {
    FocusCard(onClick = onClick) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(localizedStringResource(provider.sectionResource), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
                Text(providerNote(provider), style = MaterialTheme.typography.bodyMedium, color = SettingsMuted)
            }
            Spacer(Modifier.width(24.dp))
            SegmentOption(if (enabled) localizedStringResource(StringsR.string.settings_on_5f22c1) else localizedStringResource(StringsR.string.settings_off_25e189), selected = enabled)
        }
    }
}

@Composable
private fun providerNote(p: SourceProvider): String = when (p) {
    SourceProvider.CASTELLANO -> localizedStringResource(StringsR.string.settings_castellano_finder)
    SourceProvider.TORRENTIO -> localizedStringResource(StringsR.string.settings_the_main_finder_like_in_stremio)
    SourceProvider.PEERFLIX -> localizedStringResource(StringsR.string.settings_spanish_torrents_from_spanish_sites_dontorrent_mejortorrent)
    SourceProvider.KNABEN -> localizedStringResource(StringsR.string.settings_bambi_s_own_finders_together_the_pirate)
    SourceProvider.THEPIRATEBAY -> localizedStringResource(StringsR.string.settings_bambi_s_own_finders_together_the_pirate)
    SourceProvider.NYAA -> localizedStringResource(StringsR.string.settings_own_anime_finder_cocoscrapers_real_debrid_only)
    SourceProvider.COMET -> localizedStringResource(StringsR.string.settings_comet_with_your_real_debrid_token_sent)
    SourceProvider.AIOSTREAMS -> localizedStringResource(StringsR.string.settings_any_stremio_addon_aiostreams_comet_mediafusion_paste)
}
