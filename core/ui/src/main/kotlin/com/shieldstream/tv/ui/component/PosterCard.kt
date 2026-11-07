package com.shieldstream.tv.ui.component

import com.shieldstream.tv.ui.theme.AppColors
import com.shieldstream.tv.ui.theme.AppShapes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.strings.R
import com.shieldstream.tv.strings.localizedPluralResource
import com.shieldstream.tv.strings.localizedStringResource

val PosterWidth: Dp = 136.dp
private val CardShape = AppShapes.Control
private val FocusBorderColor = Color.White.copy(alpha = 0.88f)
private val PosterCaptionHeight = 48.dp
private val ContinueCardWidth = 240.dp

@Composable
fun PosterCard(
    item: CatalogItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    width: Dp = PosterWidth,
    onLongClick: (() -> Unit)? = null,
    newEpisodeCount: Int = 0,
    overlay: @Composable BoxScope.() -> Unit = {},
    caption: (@Composable ColumnScope.() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val scale = if (focused) 1.04f else 1f

    Column(
        modifier = modifier
            .zIndex(if (focused) 1f else 0f)
            .width(width)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .tvClickable(onLongClick = onLongClick, onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CardShape)
                .background(AppColors.Placeholder)
                .then(if (focused) Modifier.border(2.dp, FocusBorderColor, CardShape) else Modifier),
        ) {
            if (item.posterUrl != null) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = item.title,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(8.dp),
                )
            }
            overlay()
            if (newEpisodeCount > 0) {
                StatusPill(
                    text = localizedPluralResource(R.plurals.poster_new_episodes, newEpisodeCount, newEpisodeCount),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                )
            }
        }
        if (caption != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(PosterCaptionHeight),
            ) {
                caption.invoke(this)
            }
        }
    }
}

@Composable
fun CatalogPosterCard(
    item: CatalogItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    width: Dp = PosterWidth,
    onLongClick: (() -> Unit)? = null,
    newEpisodeCount: Int = 0,
) {
    PosterCard(
        item = item,
        onClick = onClick,
        modifier = modifier,
        onFocused = onFocused,
        width = width,
        onLongClick = onLongClick,
        newEpisodeCount = newEpisodeCount,
        caption = {
            Spacer(Modifier.height(7.dp))
            Text(
                text = item.title,
                color = Color.White.copy(alpha = 0.94f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.year?.toString().orEmpty(),
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        },
    )
}

@Composable
fun ContinueCard(
    item: CatalogItem,
    fraction: Float,
    label: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    newEpisodeCount: Int = 0,
) {
    var focused by remember { mutableStateOf(false) }
    val scale = if (focused) 1.04f else 1f

    Box(
        modifier = modifier
            .zIndex(if (focused) 1f else 0f)
            .width(ContinueCardWidth)
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
            .clip(CardShape)
            .background(AppColors.Placeholder)
            .then(if (focused) Modifier.border(2.dp, FocusBorderColor, CardShape) else Modifier),
    ) {
        val imageUrl = item.backdropUrl ?: item.posterUrl
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "${item.title}, $label",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(64.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.88f),
                    ),
                ),
        )
        if (label.isNotEmpty()) {
            StatusPill(
                text = label,
                dot = false,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            )
        }
        if (newEpisodeCount > 0) {
            StatusPill(
                text = localizedPluralResource(R.plurals.poster_new_episodes, newEpisodeCount, newEpisodeCount),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            )
        }
        Text(
            text = item.title,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(3.dp)
                .background(Color.White.copy(alpha = 0.24f)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .background(AppColors.Highlight),
            )
        }
    }
}

@Composable
fun NewEpisodeCard(
    item: CatalogItem,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    count: Int = 1,
    onLongClick: (() -> Unit)? = null,
) {
    PosterCard(
        item = item,
        onClick = onClick,
        modifier = modifier,
        onFocused = onFocused,
        onLongClick = onLongClick,
        overlay = {
            StatusPill(
                text = if (count == 0) {
                    localizedStringResource(R.string.coming_soon)
                } else {
                    localizedPluralResource(R.plurals.new_episode_count, count, count)
                },
                dot = count > 0,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
            )
        },
        caption = {
            Spacer(Modifier.height(7.dp))
            Text(
                text = item.title,
                color = Color.White.copy(alpha = 0.94f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.60f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
fun Top10Card(
    rank: Int,
    item: CatalogItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    newEpisodeCount: Int = 0,
) {
    PosterCard(
        item = item,
        onClick = onClick,
        onFocused = onFocused,
        newEpisodeCount = newEpisodeCount,
        modifier = modifier,
        overlay = {
            Text(
                text = rank.toString(),
                color = Color.White,
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.75f),
                        offset = Offset(2f, 2f),
                        blurRadius = 6f,
                    ),
                ),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 4.dp),
            )
        },
    )
}
