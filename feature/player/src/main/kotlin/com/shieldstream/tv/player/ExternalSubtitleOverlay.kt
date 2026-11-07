package com.shieldstream.tv.player

import com.shieldstream.tv.ui.theme.AppColors
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.strings.rememberTexts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.SubtitleView
import com.shieldstream.tv.model.SubtitleAppearance
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(UnstableApi::class)
@Composable
internal fun ExternalSubtitleOverlay(
    player: Player,
    subtitle: ActiveExternalSubtitle,
    timing: SubtitleTiming,
    appearance: SubtitleAppearance,
    modifier: Modifier = Modifier,
) {
    var view by remember { mutableStateOf<SubtitleView?>(null) }
    val currentTiming by rememberUpdatedState(timing)
    AndroidView(
        factory = { ctx ->
            SubtitleView(ctx).apply {
                setUserDefaultStyle()
                setUserDefaultTextSize()
            }.also { view = it }
        },
        modifier = modifier.fillMaxSize(),
        update = { it.applySubtitleAppearance(appearance) },
    )
    LaunchedEffect(player, subtitle, view) {
        val v = view ?: return@LaunchedEffect
        var shown = -2
        while (isActive) {
            val t = currentTiming
            val subtitleTimeUs = ((player.currentPosition - t.offsetMs) / t.scale * 1000.0).toLong()
            val index = SubtitleSync.segmentAt(subtitle.segments, subtitleTimeUs)
            if (index != shown) {
                v.setCues(if (index >= 0) subtitle.segments[index].cues else emptyList())
                shown = index
            }
            delay(40)
        }
    }
}

@Composable
internal fun SubtitleSyncBar(timing: SubtitleTiming, status: SyncStatus, modifier: Modifier = Modifier) {
    val texts = rememberTexts()
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.92f), Color.Black.copy(alpha = 0.6f), Color.Transparent)))
            .padding(start = 48.dp, end = 48.dp, top = 28.dp, bottom = 40.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = localizedStringResource(StringsR.string.player_sync_subtitles),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.width(24.dp))
            Text(
                text = timing.describe(texts),
                color = AppColors.Accent,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            )
        }
        val statusText = status.describe(texts)
        if (statusText.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(statusText, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = localizedStringResource(StringsR.string.player_0_1_s_1_s_ok_auto),
            color = Color.White.copy(alpha = 0.55f),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
