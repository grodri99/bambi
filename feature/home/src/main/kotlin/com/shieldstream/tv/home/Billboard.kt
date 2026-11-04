package com.shieldstream.tv.home

import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.model.tr
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.Category
import com.shieldstream.tv.model.FeaturedBadge
import com.shieldstream.tv.model.FeaturedItem
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.ui.component.AppIcons
import com.shieldstream.tv.ui.component.ProvidePivotScroll
import com.shieldstream.tv.ui.component.loopingItems
import com.shieldstream.tv.ui.component.rememberLoopingRowState
import com.shieldstream.tv.ui.component.tvClickable
import com.shieldstream.tv.ui.theme.DisplayFont
import com.shieldstream.tv.ui.theme.LocalLightweightUi
import kotlinx.coroutines.delay
import java.util.Locale

private val UiLocale: Locale get() = Lang.locale
private val NetflixRed = Color(0xFFE50914)
private val CardShape = RoundedCornerShape(18.dp)

/**
 * Portada grande del Inicio: los destacados de la semana. ◀ ▶ cambia de destacado (en bucle) y además
 * avanza solo cada 10 s. OK abre la ficha.
 */
@Composable
internal fun FeaturedCarousel(
    items: List<FeaturedItem>,
    onOpen: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    if (index >= items.size) index = 0
    var focused by remember { mutableStateOf(false) }
    // Cambiar de destacado a mano reinicia la cuenta (el efecto depende de index).
    val lightweightUi = LocalLightweightUi.current
    LaunchedEffect(index, items.size, lightweightUi) {
        if (!lightweightUi && items.size > 1) {
            delay(10_000)
            index = (index + 1) % items.size
        }
    }
    val current = items[index]
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
            .clip(CardShape)
            .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, CardShape)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || items.size < 2) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        index = (index - 1 + items.size) % items.size
                        true
                    }
                    Key.DirectionRight -> {
                        index = (index + 1) % items.size
                        true
                    }
                    else -> false
                }
            }
            .tvClickable { onOpen(current.item) },
    ) {
        if (lightweightUi) {
            FeaturedSlide(current)
        } else {
            Crossfade(targetState = current, animationSpec = tween(500), label = "featured") { featured ->
                FeaturedSlide(featured)
            }
        }
        if (items.size > 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            ) {
                items.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(width = if (i == index) 18.dp else 6.dp, height = 6.dp)
                            // Forma en el propio fondo (sin clip): el punto activo cambia de ancho y en la Shield
                            // el recorte no se actualizaba al cambiar de tamaño → salían cuadrados.
                            .background(if (i == index) Color.White else Color.White.copy(alpha = 0.4f), CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturedSlide(featured: FeaturedItem) {
    val item = featured.item
    val backdropUrl = if (LocalLightweightUi.current) {
        item.backdropUrl?.replace("/w1280/", "/w780/")
    } else {
        item.backdropUrl
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF111114))) {
        AsyncImage(
            model = backdropUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.85f),
                    0.45f to Color.Black.copy(alpha = 0.35f),
                    0.75f to Color.Transparent,
                ),
            ),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))))

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.6f)
                .padding(start = 32.dp, bottom = 28.dp),
        ) {
            if (featured.logoUrl != null) {
                AsyncImage(
                    model = featured.logoUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomStart,
                    modifier = Modifier.widthIn(max = 320.dp).heightIn(max = 120.dp),
                )
            } else {
                Text(
                    text = item.title.uppercase(UiLocale),
                    fontFamily = DisplayFont,
                    fontSize = 54.sp,
                    lineHeight = 52.sp,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(14.dp))
            MetaLine(featured)
        }
        BadgeChip(
            badge = featured.badge,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 28.dp),
        )
    }
}

/** "Película • Drama • 2026 • 1 h 37 min  [16+]  ★ 8,6" */
@Composable
private fun MetaLine(featured: FeaturedItem) {
    val item = featured.item
    val text = listOfNotNull(
        if (item.type == MediaType.TV) tr("Serie", "Series") else tr("Película", "Movie"),
        featured.genre,
        item.year?.toString(),
        featured.durationText,
    ).joinToString("  •  ")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        )
        featured.certification?.let { cert ->
            Spacer(Modifier.width(10.dp))
            Text(
                text = cert,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .background(certificationColor(cert), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        item.rating?.takeIf { it > 0f }?.let { rating ->
            Spacer(Modifier.width(12.dp))
            Text("★", color = Color(0xFFFFC94A), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(4.dp))
            Text(
                text = String.format(UiLocale, "%.1f", rating),
                color = Color.White,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

/** Colores de la calificación por edades española: verde TP, amarillo 7/12/13, naranja 16, rojo 18. */
private fun certificationColor(cert: String): Color = when (cert.removeSuffix("+").toIntOrNull()) {
    null -> Color(0xFF2E9E4F)
    in 0..6 -> Color(0xFF2E9E4F)
    in 7..13 -> Color(0xFFD9A400)
    in 14..16 -> Color(0xFFE66A00)
    else -> NetflixRed
}

@Composable
private fun BadgeChip(badge: FeaturedBadge, modifier: Modifier = Modifier) {
    val (icon, tint) = badgeIcon(badge)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = badge.label,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        )
    }
}

private fun badgeIcon(badge: FeaturedBadge): Pair<ImageVector, Color> = when (badge) {
    FeaturedBadge.NEW -> AppIcons.Bolt to NetflixRed
    FeaturedBadge.POPULAR -> AppIcons.Trending to Color(0xFFFF8A00)
    FeaturedBadge.RISING -> AppIcons.Trending to Color(0xFFFF8A00)
    FeaturedBadge.SERIES_NOW -> AppIcons.Tv to Color(0xFF4FA3FF)
    FeaturedBadge.MOVIE_NOW -> AppIcons.Movie to Color(0xFF4FA3FF)
    FeaturedBadge.ACCLAIMED -> AppIcons.Star to Color(0xFFFFC94A)
}

/**
 * Botones de categoría bajo la portada, en bucle. ▼ baja a las filas ([onDown]).
 * [requester] va en el botón que tenía el foco, para volver a él al subir desde las filas.
 */
@Composable
internal fun CategoryChips(
    categories: List<Category>,
    onOpen: (Category) -> Unit,
    requester: FocusRequester,
    upRequester: FocusRequester,
    onFocused: () -> Unit,
    onDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (categories.isEmpty()) return
    // Si cambian las categorías (Ajustes → Categorías del Inicio), la fila empieza de cero con la lista nueva.
    key(categories) { CategoryChipsRow(categories, onOpen, requester, upRequester, onFocused, onDown, modifier) }
}

@Composable
private fun CategoryChipsRow(
    categories: List<Category>,
    onOpen: (Category) -> Unit,
    requester: FocusRequester,
    upRequester: FocusRequester,
    onFocused: () -> Unit,
    onDown: () -> Unit,
    modifier: Modifier,
) {
    val state = rememberLoopingRowState(categories.size)
    var focusedVirtual by rememberSaveable { mutableIntStateOf(state.firstVisibleItemIndex) }
    ProvidePivotScroll(leading = 28.dp) {
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                        onDown()
                        true
                    } else {
                        false
                    }
                },
        ) {
            loopingItems(categories) { virtual, _, category ->
                CategoryChip(
                    category = category,
                    onClick = { onOpen(category) },
                    onFocused = {
                        focusedVirtual = virtual
                        onFocused()
                    },
                    modifier = (if (virtual == focusedVirtual) Modifier.focusRequester(requester) else Modifier)
                        .focusProperties { up = upRequester },
                )
            }
        }
    }
}

@Composable
private fun CategoryChip(
    category: Category,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale = if (LocalLightweightUi.current) {
        if (focused) 1.07f else 1f
    } else {
        animateFloatAsState(if (focused) 1.07f else 1f, label = "chipScale").value
    }
    val shape = RoundedCornerShape(10.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(48.dp)
            .widthIn(min = 132.dp)
            // Sin clip: el fondo se dibuja ya con la forma (ver TopBarItem).
            .background(
                shape = shape,
                brush = if (focused) {
                    Brush.verticalGradient(listOf(Color.White, Color.White))
                } else {
                    Brush.verticalGradient(listOf(Color(0xFF2A2C3C), Color(0xFF17181F)))
                },
            )
            .border(1.dp, Color.White.copy(alpha = if (focused) 0f else 0.12f), shape)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .tvClickable(onClick = onClick)
            .padding(horizontal = 22.dp),
    ) {
        Text(
            text = category.title.uppercase(UiLocale),
            fontFamily = DisplayFont,
            fontSize = 25.sp,
            letterSpacing = 0.6.sp,
            color = if (focused) Color.Black else Color.White,
            maxLines = 1,
        )
    }
}
