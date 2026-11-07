package com.shieldstream.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shieldstream.tv.model.ProfileAvatars

@Composable
fun ProfileAvatar(
    initial: String,
    avatar: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(size * 0.14f),
) {
    val (start, middle, end) = ProfileAvatars.colors[ProfileAvatars.safe(avatar)]
    val gradient = remember(start, middle, end) {
        Brush.linearGradient(
            colorStops = arrayOf(
                0f to Color(start),
                0.52f to Color(middle),
                1f to Color(end),
            ),
        )
    }
    val shading = remember {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.White.copy(alpha = 0.16f),
                0.42f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.16f),
            ),
        )
    }
    Box(
        modifier
            .size(size)
            .background(gradient, shape)
            .background(shading, shape)
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.48f).sp,
        )
    }
}
