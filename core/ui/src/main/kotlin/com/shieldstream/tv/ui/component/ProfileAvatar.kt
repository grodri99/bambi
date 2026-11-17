package com.shieldstream.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    /** Icono elegido para el perfil; mientras carga (o si falla) se ve la inicial sobre el color. */
    image: String? = null,
) {
    val (start, end) = ProfileAvatars.colors[ProfileAvatars.safe(avatar)]
    val gradient = remember(start, end) {
        Brush.linearGradient(listOf(Color(start), Color(end)))
    }
    Box(
        modifier
            .size(size)
            .background(gradient, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.48f).sp,
        )
        if (image != null) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val model = remember(image) { ProfileIconFiles.model(context, image) }
            coil.compose.AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(size).clip(shape),
            )
        }
    }
}
