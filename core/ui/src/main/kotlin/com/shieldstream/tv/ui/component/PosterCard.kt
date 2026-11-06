package com.shieldstream.tv.ui.component

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.drawscope.Stroke
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
private val CardShape = RoundedCornerShape(12.dp)
private val AccentColor = Color(0xFFE50914)
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
                .background(Color(0xFF232327))
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
                Text(
                    text = localizedPluralResource(R.plurals.poster_new_episodes, newEpisodeCount, newEpisodeCount),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(Color(0xFFE50914), RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
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
            .background(Color(0xFF232327))
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
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        if (newEpisodeCount > 0) {
            Text(
                text = localizedPluralResource(R.plurals.poster_new_episodes, newEpisodeCount, newEpisodeCount),
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(AccentColor, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
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
                    .background(AccentColor),
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
            Text(
                text = if (count == 0) {
                    localizedStringResource(R.string.coming_soon)
                } else {
                    localizedPluralResource(R.plurals.new_episode_count, count, count)
                },
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(Color(0xFFE50914), RoundedCornerShape(3.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
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
    val numberWidth = if (rank >= 10) 104.dp else 70.dp
    val posterHeight = PosterWidth * 1.5f
    Box(modifier = Modifier.width(numberWidth + PosterWidth).height(posterHeight)) {
        val numberStyle = TextStyle(
            fontSize = 150.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-12).sp,
        )
        Text(
            text = rank.toString(),
            style = numberStyle.copy(color = Color(0xFF0E0E10)),
            modifier = Modifier.align(Alignment.BottomStart).offset(x = (-4).dp, y = 36.dp),
        )
        Text(
            text = rank.toString(),
            style = numberStyle.copy(color = Color(0xFF8C8C90), drawStyle = Stroke(width = 5f)),
            modifier = Modifier.align(Alignment.BottomStart).offset(x = (-4).dp, y = 36.dp),
        )
        PosterCard(
            item = item,
            onClick = onClick,
            onFocused = onFocused,
            newEpisodeCount = newEpisodeCount,
            modifier = modifier.align(Alignment.BottomEnd),
        )
    }
}
