package com.shieldstream.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
    val (top, bottom) = ProfileAvatars.colors[ProfileAvatars.safe(avatar)]
    Box(
        modifier
            .size(size)
            .background(
                Brush.linearGradient(listOf(Color(top), Color(bottom))),
                shape,
            ),
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
