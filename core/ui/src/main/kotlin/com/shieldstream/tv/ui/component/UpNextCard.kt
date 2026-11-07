package com.shieldstream.tv.ui.component

import com.shieldstream.tv.ui.theme.AppColors
import com.shieldstream.tv.ui.theme.AppShapes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.Episode

private val UpNextCardShape = AppShapes.Control
private val UpNextCardWidth = 240.dp

fun upNextImageUrl(item: CatalogItem, episode: Episode): String? =
    episode.stillUrl ?: item.backdropUrl ?: item.posterUrl

@Composable
fun UpNextCard(
    item: CatalogItem,
    episode: Episode,
    label: String,
    releaseLabel: String,
    badgeText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    highlightBadge: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val scale = if (focused) 1.04f else 1f

    Box(
        modifier = modifier
            .zIndex(if (focused) 1f else 0f)
            .width(UpNextCardWidth)
            .aspectRatio(16f / 9f)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .tvClickable(onLongClick = onLongClick, onClick = onClick)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(UpNextCardShape)
            .background(AppColors.Placeholder)
            .then(if (focused) Modifier.border(2.dp, Color.White.copy(alpha = 0.88f), UpNextCardShape) else Modifier),
    ) {
        val imageUrl = upNextImageUrl(item, episode)
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "${item.title}, $label",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = item.title,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(12.dp),
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(96.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.30f to Color.Black.copy(alpha = 0.72f),
                        1f to Color.Black.copy(alpha = 0.96f),
                    ),
                ),
        )
        StatusPill(
            text = badgeText,
            dot = highlightBadge,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = item.title,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (releaseLabel.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = releaseLabel,
                    color = Color.White.copy(alpha = 0.58f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
