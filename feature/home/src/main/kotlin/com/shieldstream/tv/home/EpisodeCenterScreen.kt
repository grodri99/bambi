package com.shieldstream.tv.home

import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.strings.localizedCode
import com.shieldstream.tv.ui.component.NewEpisodeCard
import com.shieldstream.tv.ui.component.formatEpisodeDate

@Composable
fun EpisodeCenterScreen(
    onOpenEpisode: (CatalogItem, Episode) -> Unit,
    viewModel: EpisodeCenterViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.activate() }
    Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 36.dp, top = 24.dp)) {
        Text(
            text = localizedStringResource(StringsR.string.home_episodes),
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(16.dp))
        when (val s = state) {
            EpisodeCenterUiState.Loading -> Text(localizedStringResource(StringsR.string.home_finding_episodes), color = Color.White.copy(alpha = 0.6f))
            is EpisodeCenterUiState.Content -> {
                if (s.available.isEmpty() && s.upcoming.isEmpty()) {
                    Text(
                        text = localizedStringResource(StringsR.string.home_no_recent_or_upcoming_episodes_in_your),
                        color = Color.White.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 48.dp)) {
                        if (s.available.isNotEmpty()) {
                            item(key = "available_header") {
                                SectionTitle(localizedStringResource(StringsR.string.home_available_now) + " · ${s.newEpisodeCount}")
                            }
                            item(key = "available_row") {
                                LazyRow(
                                    contentPadding = PaddingValues(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(s.available, key = { it.item.key }) { status ->
                                        val episode = status.availableEpisodes.first()
                                        val episodeCode = episode.localizedCode()
                                        val label = remember(episode, episodeCode) { episodeLabel(episode, episodeCode) }
                                        NewEpisodeCard(
                                            item = status.item,
                                            label = label,
                                            count = status.availableEpisodes.size,
                                            onClick = { onOpenEpisode(status.item, episode) },
                                            onLongClick = { viewModel.dismiss(status) },
                                        )
                                    }
                                }
                            }
                            item(key = "dismiss_hint") {
                                Text(
                                    text = localizedStringResource(StringsR.string.home_hold_ok_to_dismiss),
                                    color = Color.White.copy(alpha = 0.45f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        if (s.upcoming.isNotEmpty()) {
                            item(key = "upcoming_header") {
                                Spacer(Modifier.height(20.dp))
                                SectionTitle(localizedStringResource(StringsR.string.home_next_30_days))
                            }
                            item(key = "upcoming_row") {
                                LazyRow(
                                    contentPadding = PaddingValues(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(s.upcoming, key = { it.item.key }) { status ->
                                        val episode = status.upcomingEpisode!!
                                        val episodeCode = episode.localizedCode()
                                        val label = remember(episode, episodeCode) { episodeLabel(episode, episodeCode) }
                                        NewEpisodeCard(
                                            item = status.item,
                                            label = label,
                                            count = 0,
                                            onClick = { onOpenEpisode(status.item, episode) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
}

private fun episodeLabel(episode: Episode, episodeCode: String): String =
    listOf(episodeCode, formatEpisodeDate(episode.airDate)).filter { it.isNotBlank() }.joinToString(" · ")
