package com.shieldstream.tv.home

import com.shieldstream.tv.ui.theme.AppColors
import com.shieldstream.tv.ui.theme.AppShapes
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.model.Lang
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.Crossfade
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.Category
import com.shieldstream.tv.model.FeaturedItem
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.ui.component.onBackKey
import com.shieldstream.tv.ui.component.SoftBackdropImage
import com.shieldstream.tv.ui.component.requestFocusWithRetry
import com.shieldstream.tv.ui.theme.LocalSoftBackdrops
import com.shieldstream.tv.ui.theme.LocalHighResBackdrops
import com.shieldstream.tv.model.BackdropUrls
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

// Inicio → cabecera con el destacado (fondo, logo, datos) y su esqueleto de carga.

@Composable
internal fun Billboard(
    featured: List<FeaturedItem>,
    categories: List<Category>,
    trailersEnabled: Boolean,
    initialFocus: TopFocus,
    onFocusChange: (TopFocus) -> Unit,
    onItemClick: (CatalogItem) -> Unit,
    onOpenCategory: (Category) -> Unit,
    onSurprise: () -> Unit,
    onPlaySomething: () -> Unit,
    onEnterRows: () -> Unit,
    topBar: @Composable (Modifier) -> Unit,
    topBarRequester: FocusRequester,
    focusTopBar: () -> Unit,
    preferTopBarFocus: Boolean,
    onTopBarFocusConsumed: () -> Unit,
) {
    val heroRequester = remember { FocusRequester() }
    val chipsRequester = remember { FocusRequester() }
    var topBarFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        when {
            preferTopBarFocus -> {
                focusTopBar()
                onTopBarFocusConsumed()
            }
            initialFocus == TopFocus.HERO && featured.isNotEmpty() -> heroRequester.requestFocusWithRetry()
            else -> chipsRequester.requestFocusWithRetry()
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(BillboardBackground)
            .onBackKey(enabled = !topBarFocused) { focusTopBar() },
    ) {
        topBar(Modifier.onFocusChanged { topBarFocused = it.hasFocus })
        if (featured.isNotEmpty()) {
            FeaturedCarousel(
                items = featured,
                onOpen = onItemClick,
                trailersEnabled = trailersEnabled,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(heroRequester)
                    // ▲ desde la portada → la pestaña activa del menú (no la más cercana).
                    // ▼ → el botón de categoría recordado, no el "más cercano" geométricamente.
                    .focusProperties {
                        up = topBarRequester
                        down = chipsRequester
                    }
                    .onFocusChanged { if (it.isFocused) onFocusChange(TopFocus.HERO) },
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        CategoryChips(
            categories = categories,
            onOpen = onOpenCategory,
            onSurprise = onSurprise,
            onPlaySomething = onPlaySomething,
            requester = chipsRequester,
            upRequester = if (featured.isNotEmpty()) heroRequester else topBarRequester,
            onFocused = { onFocusChange(TopFocus.CHIPS) },
            onDown = onEnterRows,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
}

/** Backdrop del título enfocado, arriba a la derecha, fundido a negro por la izquierda y por abajo. */
@Composable
internal fun HeroBackdrop(item: CatalogItem) {
    Box(Modifier.fillMaxSize()) {
        val area = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.74f).fillMaxHeight(0.70f)
        val url = BackdropUrls.fullScreen(item.backdropUrl, lightweight = false, highRes = LocalHighResBackdrops.current)
        Crossfade(targetState = url, animationSpec = tween(450), label = "heroBackdrop", modifier = area) { u ->
            if (u != null) {
                if (LocalSoftBackdrops.current) {
                    SoftBackdropImage(url = u, modifier = Modifier.fillMaxSize())
                } else {
                    AsyncImage(
                        model = u,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        Box(
            area.background(
                Brush.horizontalGradient(
                    0f to Color.Black,
                    0.30f to Color.Black.copy(alpha = 0.55f),
                    0.65f to Color.Transparent,
                ),
            ),
        )
        Box(
            area.background(
                Brush.verticalGradient(
                    0.45f to Color.Transparent,
                    1f to Color.Black,
                ),
            ),
        )
    }
}

@Composable
internal fun HeroInfo(item: CatalogItem) {
    Column {
        Text(
            text = item.title,
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = metaLine(item),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color(0xFF46D369),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.overview,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 520.dp),
        )
    }
}

@Composable
private fun metaLine(item: CatalogItem): String = buildList {
    add(if (item.type == MediaType.TV) localizedStringResource(StringsR.string.home_series) else localizedStringResource(StringsR.string.home_movie))
    item.year?.let { add(it.toString()) }
    item.rating?.takeIf { it > 0f }?.let { add("★ " + String.format(Lang.locale, "%.1f", it)) }
}.joinToString("  ·  ")

internal val BillboardBackground = Brush.verticalGradient(listOf(Color(0xFF151622), Color.Black))

@Composable
internal fun ColumnScope.BillboardSkeleton() {
    val alpha = delayedSkeletonAlpha() * skeletonPulse()
    Box(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .background(Brush.verticalGradient(listOf(AppColors.BackgroundRaised, AppColors.Background))),
    ) {
        Column(Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 40.dp)) {
            Box(Modifier.width(320.dp).height(42.dp).background(SkeletonColor, AppShapes.Badge))
            Spacer(Modifier.height(14.dp))
            Box(Modifier.width(340.dp).height(16.dp).background(SkeletonColor, AppShapes.Badge))
            Spacer(Modifier.height(18.dp))
            Box(Modifier.width(126.dp).height(36.dp).background(SkeletonColor, AppShapes.Pill))
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 48.dp, top = 10.dp, bottom = 16.dp)
            .graphicsLayer { this.alpha = alpha },
    ) {
        repeat(8) { index ->
            Box(
                Modifier
                    .width((104 + (index % 3) * 18).dp)
                    .height(40.dp)
                    .background(SkeletonColor, AppShapes.Pill),
            )
        }
    }
}
