package com.shieldstream.tv.player

import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.model.Lang
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shieldstream.tv.model.ExternalSubtitle
import com.shieldstream.tv.model.SubtitleLanguages
import com.shieldstream.tv.strings.localizedSubtitleLanguage

// Panel de pistas → búsqueda de subtítulos externos.

@Composable
internal fun ExternalSubtitlesView(
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
                    localizedSubtitleLanguage(state.language).let { if (Lang.isEnglish) it else it.lowercase(TrackUiLocale) },
                ))
                is ExternalSubsState.Downloading -> StatusText(localizedStringResource(StringsR.string.player_downloading_and_loading_into_the_video))
                is ExternalSubsState.Error -> StatusText(state.message)
                is ExternalSubsState.Results -> if (state.items.isEmpty()) {
                    StatusText(localizedStringResource(
                        StringsR.string.player_no_language_subtitles,
                        localizedSubtitleLanguage(state.language).let { if (Lang.isEnglish) it else it.lowercase(TrackUiLocale) },
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
