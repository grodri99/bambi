package com.shieldstream.tv.details

import com.shieldstream.tv.ui.theme.AppColors
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.domain.SourceProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.shieldstream.tv.strings.localizedCode
import com.shieldstream.tv.strings.sectionResource
import com.shieldstream.tv.model.StreamSource
import com.shieldstream.tv.ui.component.ActionMenuDialog
import com.shieldstream.tv.ui.component.ActionMenuItem
import com.shieldstream.tv.ui.component.AppIcons
import com.shieldstream.tv.ui.component.LineIcons
import com.shieldstream.tv.ui.component.SourceMediaBadges
import com.shieldstream.tv.ui.component.tvClickable
import java.util.Locale

// Ficha → lista de fuentes: estado de los buscadores, filtros y cada fuente.

/** Shows at most [max] language codes and summarises the rest, e.g. "EN/RU/IT +4". */
internal fun compactLanguages(languages: List<String>, max: Int = 3): String {
    if (languages.size <= max) return languages.joinToString("/")
    return languages.take(max).joinToString("/") + " +" + (languages.size - max)
}

@Composable
internal fun SourcesStatus(state: DetailsUiState.Content) {
    state.message?.let {
        Text(it, color = Grey, style = MaterialTheme.typography.bodyMedium)
        return
    }
    when (val s = state.sources) {
        SourcesState.Idle -> Unit
        SourcesState.Loading -> Text(localizedStringResource(StringsR.string.details_finding_sources), color = Grey, style = MaterialTheme.typography.bodyMedium)
        is SourcesState.Error -> Text(s.message, color = Grey, style = MaterialTheme.typography.bodyMedium)
        is SourcesState.Ready -> {
            val sources = state.sourceExclusions.filter(s.sources)
                .filter { !state.spanishOnly || it.advertisesSpanishAudio }
                .distinctBy { it.infoHash.lowercase() }
            val scope = state.selectedEpisode?.takeIf { state.isSeries }?.let { localizedStringResource(StringsR.string.details_for_episode, it.localizedCode()) }.orEmpty()
            when {
                sources.isEmpty() && s.pending.isNotEmpty() -> Text(localizedStringResource(StringsR.string.details_finding_sources), color = Grey, style = MaterialTheme.typography.bodyMedium)
                sources.isEmpty() && state.spanishOnly -> Text(localizedStringResource(StringsR.string.details_no_spanish_sources_available_for_this_profile), color = Grey, style = MaterialTheme.typography.bodyMedium)
                sources.isEmpty() && s.sources.isNotEmpty() && !state.sourceExclusions.isEmpty ->
                    Text(localizedStringResource(StringsR.string.details_no_sources_match_exclusions), color = Grey, style = MaterialTheme.typography.bodyMedium)
                sources.isEmpty() -> Text(localizedStringResource(StringsR.string.details_no_sources_for, scope), color = Grey, style = MaterialTheme.typography.bodyMedium)
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        AppIcons.List,
                        contentDescription = localizedStringResource(StringsR.string.details_sources),
                        tint = Grey,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(sources.size.toString(), color = Grey, style = MaterialTheme.typography.bodyMedium)
                    val cached = sources.count { it.isCached }
                    if (cached > 0) {
                        Spacer(Modifier.width(14.dp))
                        Icon(
                            AppIcons.Bolt,
                            contentDescription = localizedStringResource(StringsR.string.details_cached),
                            tint = Grey,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(cached.toString(), color = Grey, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
internal fun SourcesPanel(
    state: DetailsUiState.Content,
    onPick: (StreamSource) -> Unit,
    isBlocked: (StreamSource) -> Boolean,
    onToggleSpanishOnly: () -> Unit,
    onMarkBroken: (StreamSource) -> Unit,
    autoPick: StreamSource? = null,
) {
    val firstRequester = remember { FocusRequester() }
    val allSources = (state.sources as? SourcesState.Ready)?.sources.orEmpty().filter { !it.isMarkedBroken(state.brokenSources) }
    var brokenCandidate by remember { mutableStateOf<StreamSource?>(null) }
    var showAll by remember { mutableStateOf(false) }
    val sources = if (showAll) allSources else state.sourceExclusions.filter(allSources)
    val order = SourceProvider.entries.map { localizedStringResource(it.sectionResource) }.distinct()
    val pending = (state.sources as? SourcesState.Ready)?.pending.orEmpty()
    // El buscador de castellano reparte sus fuentes en las secciones de cada sitio: no tiene cabecera propia.
    val castellanoSection = localizedStringResource(SourceProvider.CASTELLANO.sectionResource)
    val unavailable = ((state.sources as? SourcesState.Ready)?.unavailable.orEmpty() +
        pending.associateWith { localizedStringResource(StringsR.string.details_searching) }) - castellanoSection
    val spanishFirst = state.spanishFirst
    val spanishOnly = state.spanishOnly
    val groups = remember(sources, unavailable, spanishFirst, spanishOnly) {
        val byProvider = sources.groupBy { it.provider }
        (byProvider.keys + unavailable.keys).distinct().sortedBy { order.indexOf(it) }.map { section ->
            // Mismo orden que "Reproducir" (el que ya trae la lista): nada de reordenar aquí.
            section to byProvider[section].orEmpty()
                .filter { !spanishOnly || it.advertisesSpanishAudio }
        }
    }
    var open by remember { mutableStateOf(emptySet<String>()) }
    var autoOpened by remember { mutableStateOf(false) }
    LaunchedEffect(groups) {
        if (!autoOpened) groups.firstOrNull { it.second.isNotEmpty() }?.let { open = setOf(it.first); autoOpened = true }
    }
    LaunchedEffect(sources.isNotEmpty()) {
        if (sources.isNotEmpty()) {
            withFrameNanos { }
            runCatching { firstRequester.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(520.dp)
                .background(AppColors.BackgroundRaised.copy(alpha = 0.95f))
                .padding(top = 32.dp, start = 24.dp, end = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = localizedStringResource(StringsR.string.details_choose_a_source),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                )
                SpanishOnlyChip(enabled = spanishOnly, onClick = onToggleSpanishOnly)
            }
            state.selectedEpisode?.takeIf { state.isSeries }?.let {
                Text("${it.localizedCode()} · ${it.title}", color = Grey, style = MaterialTheme.typography.bodyMedium)
            }
            if (state.sources is SourcesState.Ready) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = localizedStringResource(StringsR.string.details_hold_ok_to_mark_source_broken),
                    color = Grey.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = localizedStringResource(
                            StringsR.string.details_sources_filtered_count,
                            sources.count { !spanishOnly || it.advertisesSpanishAudio },
                            allSources.size,
                        ),
                        color = Grey,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (!state.sourceExclusions.isEmpty) {
                        ShowAllChip(showAll = showAll, onClick = { showAll = !showAll })
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            when (val s = state.sources) {
                SourcesState.Idle -> Text(localizedStringResource(StringsR.string.details_pick_an_episode_first), color = Grey)
                SourcesState.Loading -> Text(localizedStringResource(StringsR.string.details_finding_sources), color = Grey)
                is SourcesState.Error -> Text(s.message, color = Grey)
                is SourcesState.Ready -> if (groups.all { it.second.isEmpty() } && s.pending.isEmpty()) {
                    Text(
                        when {
                            spanishOnly -> localizedStringResource(StringsR.string.details_no_spanish_sources_available_for_this_profile)
                            !showAll && allSources.isNotEmpty() && sources.isEmpty() ->
                                localizedStringResource(StringsR.string.details_no_sources_match_exclusions)
                            else -> localizedStringResource(StringsR.string.details_no_sources)
                        },
                        color = Grey,
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 32.dp),
                    ) {
                        val pick = autoPick?.takeIf { p -> sources.any { it.id == p.id } }
                        if (pick != null) {
                            item(key = "auto") {
                                Column {
                                    Text(
                                        localizedStringResource(StringsR.string.details_plays_on_play),
                                        color = AppColors.Accent,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                                    )
                                    SourceRow(
                                        source = pick,
                                        blocked = isBlocked(pick),
                                        onClick = { onPick(pick) },
                                        onLongClick = { brokenCandidate = pick },
                                        modifier = Modifier.focusRequester(firstRequester),
                                    )
                                    Spacer(Modifier.height(10.dp))
                                }
                            }
                        }
                        groups.forEachIndexed { index, (name, list) ->
                            item(key = "h:$name") {
                                ProviderHeader(
                                    name = name,
                                    count = list.size,
                                    cached = list.count { it.isCached },
                                    expanded = name in open,
                                    note = unavailable[name].takeIf { list.isEmpty() }
                                        ?: localizedStringResource(StringsR.string.details_none_in_spanish).takeIf { list.isEmpty() && spanishOnly },
                                    onClick = { if (list.isNotEmpty()) open = if (name in open) open - name else open + name },
                                    modifier = if (index == 0 && pick == null) Modifier.focusRequester(firstRequester) else Modifier,
                                )
                            }
                            if (name in open) {
                                items(list, key = { it.id }) { source ->
                                    SourceRow(
                                        source = source,
                                        blocked = isBlocked(source),
                                        onClick = { onPick(source) },
                                        onLongClick = { brokenCandidate = source },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    brokenCandidate?.let { source ->
        ActionMenuDialog(
            title = source.title,
            onDismiss = { brokenCandidate = null },
            consumeOpeningKeyRelease = true,
            actions = listOf(
                ActionMenuItem(
                    localizedStringResource(StringsR.string.details_cancel),
                    LineIcons.Close,
                ) { brokenCandidate = null },
                ActionMenuItem(
                    localizedStringResource(StringsR.string.details_mark_source_broken),
                    LineIcons.Delete,
                ) {
                    onMarkBroken(source)
                    brokenCandidate = null
                },
            ),
        )
    }
}

@Composable
private fun SpanishOnlyChip(enabled: Boolean, onClick: () -> Unit) {
    SourcePanelChip(
        text = if (enabled) "✓ " + localizedStringResource(StringsR.string.details_spanish_only) else localizedStringResource(StringsR.string.details_spanish_only),
        highlighted = enabled,
        onClick = onClick,
    )
}

@Composable
private fun ShowAllChip(showAll: Boolean, onClick: () -> Unit) {
    SourcePanelChip(
        text = localizedStringResource(
            if (showAll) StringsR.string.details_hide_excluded else StringsR.string.details_show_all,
        ),
        highlighted = showAll,
        onClick = onClick,
    )
}

@Composable
private fun SourcePanelChip(text: String, highlighted: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text = text,
        color = if (focused) Color.Black else Color.White,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier
            .background(
                when {
                    focused -> Color.White
                    highlighted -> Color.White.copy(alpha = 0.22f)
                    else -> Color.White.copy(alpha = 0.08f)
                },
                RoundedCornerShape(50),
            )
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun ProviderHeader(name: String, count: Int, cached: Int, expanded: Boolean, note: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val color = if (focused) Color.Black else Color.White
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(name, color = color, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
        val secondary = if (focused) Color.Black.copy(alpha = 0.7f) else Grey
        Text(
            note ?: localizedStringResource(StringsR.string.details_source_count, count),
            color = secondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (note == null && cached > 0) {
            // Same bolt icon and colour as the cached source rows, instead of a yellow emoji.
            Text("  ·  ", color = secondary, style = MaterialTheme.typography.bodyMedium)
            Icon(
                AppIcons.Bolt,
                contentDescription = localizedStringResource(StringsR.string.details_cached),
                tint = if (focused) CachedFocused else Green,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(3.dp))
            Text(cached.toString(), color = secondary, style = MaterialTheme.typography.bodyMedium)
        }
        if (note == null) Text(if (expanded) "   ▾" else "   ›", color = color, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SourceRow(
    source: StreamSource,
    blocked: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.06f))
            .tvClickable(onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = when {
            focused -> Color.Black
            blocked -> Color.White.copy(alpha = 0.4f)
            else -> Color.White
        }
        val sub = if (focused) Color.Black.copy(alpha = 0.65f) else Color.White.copy(alpha = if (blocked) 0.3f else 0.55f)
        Box(Modifier.width(26.dp)) {
            if (source.isCached) Icon(AppIcons.Bolt, contentDescription = localizedStringResource(StringsR.string.details_in_your_real_debrid), tint = if (focused) CachedFocused else Green)
        }
        Column(Modifier.weight(1f)) {
            // Primary line: what the viewer chooses by (format, size, languages).
            // Secondary line: torrent health and the raw release name.
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceMediaBadges(source)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = buildString {
                        if (source.sizeBytes > 0) append(formatSize(source.sizeBytes))
                        if (source.languages.isNotEmpty()) {
                            if (isNotEmpty()) append(" · ")
                            append(compactLanguages(source.languages))
                        }
                        if (blocked) {
                            if (isNotEmpty()) append(" · ")
                            append(localizedStringResource(StringsR.string.details_blocked_by_real_debrid))
                        }
                    },
                    color = fg,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(2.dp))
            // Enfocada, el nombre completo de la fuente se desplaza (marquesina) en vez de cortarse con "…".
            Text(
                text = source.seeders?.let { "$it seeds · ${source.title}" } ?: source.title,
                color = sub,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = if (focused) TextOverflow.Clip else TextOverflow.Ellipsis,
                modifier = if (focused) {
                    Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 700, repeatDelayMillis = 1500)
                } else {
                    Modifier
                },
            )
        }
    }
}

private fun formatSize(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 1) String.format(Locale.US, "%.1f GB", gb) else String.format(Locale.US, "%.0f MB", gb * 1024)
}
