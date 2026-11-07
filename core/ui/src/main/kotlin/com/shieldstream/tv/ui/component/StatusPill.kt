package com.shieldstream.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shieldstream.tv.ui.theme.AppColors
import com.shieldstream.tv.ui.theme.AppShapes

/**
 * Small translucent label drawn over artwork, e.g. "New episode". The accent dot marks news; pass
 * `dot = false` for neutral labels such as remaining time. The shape is drawn directly instead of
 * clipped because some TV render paths drop clips on scaled layers.
 */
@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    dot: Boolean = true,
) {
    Row(
        modifier = modifier
            .background(AppColors.PillScrim, AppShapes.Pill)
            .padding(start = if (dot) 7.dp else 8.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Box(Modifier.size(6.dp).background(AppColors.Highlight, CircleShape))
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            softWrap = false,
        )
    }
}
