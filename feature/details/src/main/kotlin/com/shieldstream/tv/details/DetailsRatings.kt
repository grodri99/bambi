package com.shieldstream.tv.details

import com.shieldstream.tv.ui.theme.AppColors
import com.shieldstream.tv.ui.theme.AppShapes
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.domain.TraktRatingTarget
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.strings.localizedCode
import com.shieldstream.tv.ui.component.AppIcons
import com.shieldstream.tv.ui.component.BottomActionDialog
import com.shieldstream.tv.ui.component.TvButton
import com.shieldstream.tv.ui.component.tvClickable

// Ficha → puntuar en Trakt y ver las puntuaciones.

@Composable
internal fun RatingPickerDialog(
    state: DetailsUiState.Content,
    target: TraktRatingTarget,
    ratings: TraktRatingsState,
    onSave: (Int?) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ready = ratings as? TraktRatingsState.Ready
    val currentRating = when (target) {
        is TraktRatingTarget.Episode -> ready?.episodeRating
        else -> ready?.overallRating
    }
    var selected by remember(target) { mutableIntStateOf(currentRating ?: 8) }
    LaunchedEffect(target, currentRating) { selected = currentRating ?: 8 }
    val requester = remember { FocusRequester() }
    LaunchedEffect(ratings) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
    }
    RatingsDialog(title = ratingTargetLabel(state, target), onDismiss = onDismiss) {
        when (ratings) {
            TraktRatingsState.Idle, TraktRatingsState.Loading -> {
                CircularProgressIndicator(
                    color = AppColors.Accent,
                    modifier = Modifier.align(Alignment.CenterHorizontally).size(36.dp),
                )
            }
            TraktRatingsState.NotConnected -> {
                Text(
                    text = localizedStringResource(StringsR.string.details_trakt_connect_to_rate),
                    color = Grey,
                    style = MaterialTheme.typography.bodyLarge,
                )
                TvButton(
                    text = localizedStringResource(StringsR.string.details_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.focusRequester(requester).align(Alignment.CenterHorizontally),
                )
            }
            is TraktRatingsState.Error -> {
                Text(ratings.message, color = AppColors.Error, style = MaterialTheme.typography.bodyLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    TvButton(
                        text = localizedStringResource(StringsR.string.details_retry),
                        onClick = onRetry,
                        modifier = Modifier.focusRequester(requester),
                    )
                    TvButton(text = localizedStringResource(StringsR.string.details_cancel), onClick = onDismiss)
                }
            }
            is TraktRatingsState.Ready -> {
                Text(
                    text = localizedStringResource(StringsR.string.details_rating_picker_help),
                    color = Grey,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                RatingStarsControl(
                    value = selected,
                    enabled = !ratings.saving,
                    onChange = { selected = it },
                    onSave = { onSave(selected) },
                    modifier = Modifier.align(Alignment.CenterHorizontally).focusRequester(requester),
                )
                ratings.error?.let { Text(it, color = AppColors.Error, style = MaterialTheme.typography.bodyMedium) }
                if (ratings.saving) {
                    Text(
                        text = localizedStringResource(StringsR.string.details_rating_saving),
                        color = Grey,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    if (currentRating != null) {
                        TvButton(
                            text = localizedStringResource(StringsR.string.details_remove_rating),
                            onClick = { onSave(null) },
                            enabled = !ratings.saving,
                        )
                    }
                    TvButton(
                        text = localizedStringResource(StringsR.string.details_cancel),
                        onClick = onDismiss,
                        enabled = !ratings.saving,
                    )
                }
            }
        }
    }
}

@Composable
private fun RatingsDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) = BottomActionDialog(
    title = title,
    onDismiss = onDismiss,
    scrimAlpha = 0.94f,
    content = content,
)

@Composable
private fun RatingStarsControl(
    value: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { onChange((value - 1).coerceAtLeast(1)); true }
                    Key.DirectionRight -> { onChange((value + 1).coerceAtMost(10)); true }
                    else -> false
                }
            }
            .clip(AppShapes.Control)
            .background(if (focused) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .border(1.dp, if (focused) Color.White.copy(alpha = 0.65f) else Color.Transparent, AppShapes.Control)
            .tvClickable(enabled = enabled, onClick = onSave)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (star in 1..10) {
                Icon(
                    imageVector = AppIcons.Star,
                    contentDescription = null,
                    tint = if (star <= value) AppColors.Accent else Color.White.copy(alpha = 0.20f),
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = localizedStringResource(StringsR.string.details_rating_value, value),
            color = if (focused) Color.White else Grey,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
internal fun ratingTargetLabel(state: DetailsUiState.Content, target: TraktRatingTarget): String = when (target) {
    is TraktRatingTarget.Movie -> localizedStringResource(StringsR.string.details_rate_movie)
    is TraktRatingTarget.Show -> localizedStringResource(StringsR.string.details_rate_show)
    is TraktRatingTarget.Episode -> {
        val code = state.selectedEpisode
            ?.takeIf { it.season == target.season && it.number == target.episode }
            ?.localizedCode() ?: "S${target.season}E${target.episode}"
        localizedStringResource(StringsR.string.details_rate_episode, code)
    }
}

internal fun ratingSuffix(rating: Int?): String = rating?.let { " · $it/10" }.orEmpty()
