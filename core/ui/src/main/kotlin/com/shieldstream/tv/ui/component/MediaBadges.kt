package com.shieldstream.tv.ui.component

import com.shieldstream.tv.ui.theme.AppColors
import com.shieldstream.tv.ui.theme.AppShapes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shieldstream.tv.model.HdrType
import com.shieldstream.tv.model.PlaybackVideoInfo
import com.shieldstream.tv.model.Quality
import com.shieldstream.tv.model.StreamSource

@Composable
fun SourceMediaBadges(source: StreamSource, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (source.quality != Quality.UNKNOWN) {
            MediaBadge(source.quality.label, AppIcons.Movie, BadgeColors.Neutral)
        }
        if (source.hdrType != HdrType.SDR) {
            MediaBadge(hdrLabel(source.hdrType), hdrIcon(source.hdrType), hdrColor(source.hdrType))
        }
        source.videoCodec?.let { MediaBadge(it.label, AppIcons.Memory, BadgeColors.Codec) }
        if (source.softwareDecode) MediaBadge("CPU", AppIcons.Memory, BadgeColors.Software)
    }
}

@Composable
fun PlaybackMediaBadges(info: PlaybackVideoInfo, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        info.resolutionLabel?.let { MediaBadge(it, AppIcons.Movie, BadgeColors.Neutral) }
        if (info.hdrType != HdrType.SDR) {
            MediaBadge(hdrLabel(info.hdrType), hdrIcon(info.hdrType), hdrColor(info.hdrType))
        }
        info.codec?.let { MediaBadge(it.label, AppIcons.Memory, BadgeColors.Codec) }
        info.hardwareDecode?.let { hardware ->
            if (hardware) MediaBadge("HW", AppIcons.Memory, BadgeColors.Hardware)
            else MediaBadge("CPU", AppIcons.Memory, BadgeColors.Software)
        }
    }
}

@Composable
private fun MediaBadge(text: String, icon: ImageVector, accent: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(accent.copy(alpha = 0.20f), AppShapes.Badge)
            .border(1.dp, accent.copy(alpha = 0.58f), AppShapes.Badge)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        )
    }
}

private fun hdrLabel(type: HdrType): String = if (type == HdrType.DOLBY_VISION) "DV" else type.label

private fun hdrIcon(type: HdrType): ImageVector = if (type == HdrType.DOLBY_VISION) AppIcons.Star else AppIcons.Hdr

private fun hdrColor(type: HdrType): Color = if (type == HdrType.DOLBY_VISION) BadgeColors.DolbyVision else BadgeColors.Hdr

// Monochrome: every badge shares one neutral treatment; only software decoding keeps a warning hue.
private object BadgeColors {
    val Neutral = Color(0xFFC8C8CC)
    val Hdr = Neutral
    val DolbyVision = Neutral
    val Codec = Neutral
    val Hardware = Neutral
    val Software = AppColors.Warning
}
