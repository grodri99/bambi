package com.shieldstream.tv.home

import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.model.tr
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.CatalogRow
import com.shieldstream.tv.model.Category
import com.shieldstream.tv.model.FeaturedItem
import com.shieldstream.tv.model.HomeFilter
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.RowStyle
import com.shieldstream.tv.ui.component.ContinueCard
import com.shieldstream.tv.ui.component.PosterCard
import com.shieldstream.tv.ui.component.PosterWidth
import com.shieldstream.tv.ui.component.ProvidePivotScroll
import com.shieldstream.tv.ui.component.Top10Card
import com.shieldstream.tv.ui.component.TvButton
import com.shieldstream.tv.ui.component.firstVirtualIndex
import com.shieldstream.tv.ui.component.loopingItems
import com.shieldstream.tv.ui.component.onBackKey
import com.shieldstream.tv.ui.component.rememberLoopingRowState
import com.shieldstream.tv.ui.component.shouldLoop
import com.shieldstream.tv.ui.theme.DisplayFont
import com.shieldstream.tv.ui.theme.LocalLightweightUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import java.util.Locale

private val ContentStart = 48.dp

/**
 * Inicio / Series / Películas. Dos modos:
 *  - Portada (arriba): menú superior, destacados de la semana y botones de categoría.
 *  - Filas (al bajar): fondo e información del título enfocado + filas (en bucle).
 * ▼ desde las categorías → filas. ▲ desde la primera fila o Atrás → portada.
 */
@Composable
fun HomeScreen(
    filter: HomeFilter,
    onItemClick: (CatalogItem) -> Unit,
    onOpenCategory: (Category) -> Unit,
    topBar: @Composable (Modifier) -> Unit,
    topBarRequester: FocusRequester,
    focusTopBar: () -> Unit,
    preferTopBarFocus: Boolean,
    onTopBarFocusConsumed: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(key = "home_${filter.name}"),
) {
    LaunchedEffect(filter) { viewModel.load(filter) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hidden by viewModel.hiddenCategories.collectAsStateWithLifecycle()

    when (val s = state) {
        // Mismo fondo que la portada para que al terminar de cargar no haya "salto" de color.
        HomeUiState.Loading -> Column(Modifier.fillMaxSize().background(BillboardBackground)) {
            // Con el menú visible mientras carga (al cambiar de pestaña el foco se queda en él).
            topBar(Modifier)
            LaunchedEffect(Unit) {
                if (preferTopBarFocus) {
                    withFrameNanos { }
                    focusTopBar()
                }
            }
            BillboardSkeleton()
        }
        // Con el menú arriba: si falta una clave (instalación nueva), la rueda de Ajustes queda a mano.
        is HomeUiState.Error -> Column(Modifier.fillMaxSize().background(BillboardBackground)) {
            topBar(Modifier)
            HomeError(
                s.message,
                onRetry = { viewModel.load(filter, force = true) },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
        is HomeUiState.Content -> HomeContent(
            rows = s.rows,
            featured = s.featured,
            categories = remember(filter, hidden) {
                when {
                    // Perfil infantil: solo Infantiles y Para toda la familia (no se pueden ocultar).
                    viewModel.kidsProfile -> Category.forFilter(filter).filter { it in Category.KIDS_SAFE }
                    // Si se ocultaran todas las de esta pestaña, se enseñan todas (la fila de botones no puede quedar vacía).
                    else -> Category.forFilter(filter).filter { it.name !in hidden }.ifEmpty { Category.forFilter(filter) }
                }
            },
            onItemClick = onItemClick,
            onOpenCategory = onOpenCategory,
            onRemoveContinue = viewModel::removeFromContinue,
            topBar = topBar,
            topBarRequester = topBarRequester,
            focusTopBar = focusTopBar,
            preferTopBarFocus = preferTopBarFocus,
            onTopBarFocusConsumed = onTopBarFocusConsumed,
        )
    }
}

private enum class TopFocus { HERO, CHIPS }

@Composable
private fun HomeContent(
    rows: List<CatalogRow>,
    featured: List<FeaturedItem>,
    categories: List<Category>,
    onItemClick: (CatalogItem) -> Unit,
    onOpenCategory: (Category) -> Unit,
    onRemoveContinue: (CatalogItem) -> Unit,
    topBar: @Composable (Modifier) -> Unit,
    topBarRequester: FocusRequester,
    focusTopBar: () -> Unit,
    preferTopBarFocus: Boolean,
    onTopBarFocusConsumed: () -> Unit,
) {
    var inRows by rememberSaveable { mutableStateOf(false) }
    var topFocus by rememberSaveable { mutableStateOf(TopFocus.HERO) }
    // Portada y filas conservan su estado al pasar de una a otra (botón de categoría, tarjeta enfocada…).
    val saveable = rememberSaveableStateHolder()
    if (inRows && rows.isNotEmpty()) saveable.SaveableStateProvider("rows") {
        RowsBrowser(
            rows = rows,
            onItemClick = onItemClick,
            onRemoveContinue = onRemoveContinue,
            onExitTop = {
                topFocus = TopFocus.CHIPS
                inRows = false
            },
            onBack = {
                topFocus = if (featured.isNotEmpty()) TopFocus.HERO else TopFocus.CHIPS
                inRows = false
            },
        )
    } else saveable.SaveableStateProvider("billboard") {
        Billboard(
            featured = featured,
            categories = categories,
            initialFocus = topFocus,
            onFocusChange = { topFocus = it },
            onItemClick = onItemClick,
            onOpenCategory = onOpenCategory,
            onEnterRows = { if (rows.isNotEmpty()) inRows = true },
            topBar = topBar,
            topBarRequester = topBarRequester,
            focusTopBar = focusTopBar,
            preferTopBarFocus = preferTopBarFocus,
            onTopBarFocusConsumed = onTopBarFocusConsumed,
        )
    }
}

/** Portada: menú + destacados + categorías. Atrás (fuera del menú) lleva el foco al menú. */
@Composable
private fun Billboard(
    featured: List<FeaturedItem>,
    categories: List<Category>,
    initialFocus: TopFocus,
    onFocusChange: (TopFocus) -> Unit,
    onItemClick: (CatalogItem) -> Unit,
    onOpenCategory: (Category) -> Unit,
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
            initialFocus == TopFocus.HERO && featured.isNotEmpty() -> runCatching { heroRequester.requestFocus() }
            else -> runCatching { chipsRequester.requestFocus() }
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
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(heroRequester)
                    // ▲ desde la portada → la pestaña activa del menú (no la más cercana).
                    // ▼ → el botón de categoría que se ve a la izquierda (el recordado), no el "más cercano"
                    // geométricamente: con una portada tan ancha Android elegía uno más a la derecha.
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
            requester = chipsRequester,
            upRequester = if (featured.isNotEmpty()) heroRequester else topBarRequester,
            onFocused = { onFocusChange(TopFocus.CHIPS) },
            onDown = onEnterRows,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
}

/**
 * Fondo + información del título enfocado + filas en bucle. Lo usan el Inicio (al bajar) y las categorías.
 * [header]: título grande arriba (categorías). [onExitTop]: ▲ en la primera fila.
 */
@Composable
internal fun RowsBrowser(
    rows: List<CatalogRow>,
    onItemClick: (CatalogItem) -> Unit,
    onRemoveContinue: (CatalogItem) -> Unit,
    onExitTop: (() -> Unit)?,
    onBack: () -> Unit,
    header: String? = null,
) {
    // Última tarjeta enfocada: "fila/posición virtual". rememberSaveable: sobrevive a ir al detalle y volver.
    // IMPORTANTE (fluidez): este estado NO se lee al componer. Si se leyera, cada paso del mando recomponía toda la
    // pantalla y todas las filas visibles (frames de 50-150 ms medidos en la Shield). Lo leen solo los listeners.
    val savedFocus = rememberSaveable { mutableStateOf(firstKey(rows.first())) }
    // Si esa tarjeta ya no existe (p. ej. se terminó la peli de "Seguir viendo"), la primera de las filas.
    fun validKey(key: String) = if (findByKey(rows, key) != null) key else firstKey(rows.first())
    // Clave de "volver aquí" para las filas: cambia al entrar en la pantalla o al quitar un título de Seguir viendo.
    var restoreKey by remember { mutableStateOf(Snapshot.withoutReadObservation { validKey(savedFocus.value) }) }
    var lastVerticalRepeat by remember { mutableStateOf(0L) }
    // Tras quitar una tarjeta de "Seguir viendo", el foco salta a la vecina cuando llegan las filas nuevas.
    var pendingRefocus by remember { mutableStateOf(false) }

    // Fondo e información de arriba: cambian 300 ms después de pararse en un título (al recorrer rápido una fila no
    // se piden 20 fondos seguidos al Tegra). Se escucha el estado sin leerlo al componer.
    var hero by remember { mutableStateOf(Snapshot.withoutReadObservation { findByKey(rows, restoreKey) ?: rows.first().items.first() }) }
    LaunchedEffect(rows) {
        snapshotFlow { savedFocus.value }.collectLatest { key ->
            delay(300)
            hero = findByKey(rows, validKey(key)) ?: rows.first().items.first()
        }
    }

    // Al entrar (o volver del detalle) el foco va a la tarjeta que tenía.
    val restoreRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { restoreRequester.requestFocus() }
    }
    LaunchedEffect(rows) {
        if (pendingRefocus) {
            withFrameNanos { }
            runCatching { restoreRequester.requestFocus() }
            pendingRefocus = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onBackKey(enabled = true) { onBack() }
            // Botón ▲▼ mantenido: el mando repite unas 20 veces por segundo y cada paso monta una fila nueva. Se deja
            // pasar una repetición cada 130 ms: la lista baja a ritmo constante en vez de atascarse y dar saltos.
            .onPreviewKeyEvent { e ->
                val vertical = e.key == Key.DirectionDown || e.key == Key.DirectionUp
                if (e.type != KeyEventType.KeyDown || !vertical || e.nativeKeyEvent.repeatCount == 0) return@onPreviewKeyEvent false
                val now = android.os.SystemClock.uptimeMillis()
                if (now - lastVerticalRepeat < 130) true else { lastVerticalRepeat = now; false }
            },
    ) {
        HeroBackdrop(hero)
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(236.dp)
                    .padding(start = ContentStart, top = 24.dp, end = 320.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                header?.let {
                    Text(
                        text = it.uppercase(Lang.locale),
                        fontFamily = DisplayFont,
                        fontSize = 30.sp,
                        color = Color(0xFFE50914),
                    )
                }
                HeroInfo(item = hero)
            }
            // Deja visible el título de la fila enfocada. 51 dp = los 42 de antes + lo que crece hacia arriba el póster
            // enfocado (×1.1): ahora se alinea la tarjeta sin escalar (ver PosterCard), así la pantalla queda igual.
            ProvidePivotScroll(leading = 51.dp) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 260.dp),
                ) {
                    itemsIndexed(rows, key = { _, row -> row.id }, contentType = { _, row -> row.style }) { rowIndex, row ->
                        CatalogRowView(
                            row = row,
                            focusKey = restoreKey,
                            restoreRequester = restoreRequester,
                            onFocused = { virtual -> savedFocus.value = keyOf(row, virtual) },
                            onItemClick = onItemClick,
                            onRemove = { virtual, item ->
                                savedFocus.value = neighbourKey(rows, row, virtual)
                                restoreKey = savedFocus.value
                                pendingRefocus = true
                                onRemoveContinue(item)
                            },
                            modifier = if (rowIndex == 0 && onExitTop != null) {
                                Modifier.onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                        onExitTop()
                                        true
                                    } else {
                                        false
                                    }
                                }
                            } else {
                                Modifier
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogRowView(
    row: CatalogRow,
    focusKey: String?,
    restoreRequester: FocusRequester,
    onFocused: (Int) -> Unit,
    onItemClick: (CatalogItem) -> Unit,
    onRemove: (Int, CatalogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(start = ContentStart, top = 4.dp),
        ) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
            if (row.style == RowStyle.CONTINUE) {
                Text(
                    text = tr("   Mantén OK para quitar", "   Hold OK to remove"),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.45f),
                )
            }
        }
        // Con menos de 8 títulos no hay bucle: si cambia (p. ej. al quitar uno), la fila empieza de cero.
        key(shouldLoop(row.items.size)) {
            // + 6 dp: lo que crece por cada lado el póster enfocado; así su borde sigue alineado con el título de la fila.
            ProvidePivotScroll(leading = ContentStart + 6.dp) {
                LazyRow(
                    state = rememberLoopingRowState(row.items.size),
                    contentPadding = PaddingValues(horizontal = ContentStart, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (row.style == RowStyle.TOP10) 6.dp else 12.dp),
                ) {
                    loopingItems(row.items) { virtual, index, item ->
                        val itemModifier = if (keyOf(row, virtual) == focusKey) Modifier.focusRequester(restoreRequester) else Modifier
                        when (row.style) {
                            RowStyle.POSTER -> PosterCard(
                                item = item,
                                onClick = { onItemClick(item) },
                                onFocused = { onFocused(virtual) },
                                modifier = itemModifier,
                            )
                            RowStyle.CONTINUE -> {
                                val info = row.progress[item.key]
                                ContinueCard(
                                    item = item,
                                    fraction = info?.fraction ?: 0f,
                                    label = info?.label.orEmpty(),
                                    onClick = { onItemClick(item) },
                                    onLongClick = { onRemove(virtual, item) },
                                    onFocused = { onFocused(virtual) },
                                    modifier = itemModifier,
                                )
                            }
                            RowStyle.TOP10 -> Top10Card(
                                rank = index + 1,
                                item = item,
                                onClick = { onItemClick(item) },
                                onFocused = { onFocused(virtual) },
                                modifier = itemModifier,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Backdrop del título enfocado, arriba a la derecha, fundido a negro por la izquierda y por abajo. */
@Composable
private fun HeroBackdrop(item: CatalogItem) {
    Box(Modifier.fillMaxSize()) {
        val area = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.74f).fillMaxHeight(0.70f)
        val backdropUrl = if (LocalLightweightUi.current) {
            item.backdropUrl?.replace("/w1280/", "/w780/")
        } else {
            item.backdropUrl
        }
        if (backdropUrl != null) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = area,
            )
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
private fun HeroInfo(item: CatalogItem) {
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

private fun metaLine(item: CatalogItem): String = buildList {
    add(if (item.type == MediaType.TV) tr("Serie", "Series") else tr("Película", "Movie"))
    item.year?.let { add(it.toString()) }
    item.rating?.takeIf { it > 0f }?.let { add("★ " + String.format(Lang.locale, "%.1f", it)) }
}.joinToString("  ·  ")

private val BillboardBackground = Brush.verticalGradient(listOf(Color(0xFF151622), Color.Black))
private val SkeletonColor = Color(0xFF1C1E2A)

/**
 * Opacidad del esqueleto de carga: no aparece hasta pasados 250 ms y entra con fundido. Si los datos llegan
 * antes (caché / precarga) solo se ve el fondo oscuro y no hay "parpadeo" de cajas.
 */
@Composable
private fun delayedSkeletonAlpha(): Float {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(250)
        visible = true
    }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(300), label = "skeleton")
    return alpha
}

/** Esqueleto con la forma de la portada (tarjeta grande + botones de categoría) mientras carga. */
@Composable
private fun ColumnScope.BillboardSkeleton() {
    val alpha = delayedSkeletonAlpha()
    Box(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
            .graphicsLayer { this.alpha = alpha }
            .background(Brush.verticalGradient(listOf(Color(0xFF191B27), Color(0xFF111219))), RoundedCornerShape(18.dp)),
    ) {
        Column(Modifier.align(Alignment.BottomStart).padding(start = 40.dp, bottom = 40.dp)) {
            Box(Modifier.width(300.dp).height(46.dp).background(SkeletonColor, RoundedCornerShape(6.dp)))
            Spacer(Modifier.height(14.dp))
            Box(Modifier.width(380.dp).height(18.dp).background(SkeletonColor, RoundedCornerShape(4.dp)))
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, top = 14.dp, bottom = 20.dp)
            .graphicsLayer { this.alpha = alpha },
    ) {
        repeat(8) {
            Box(Modifier.width(150.dp).height(48.dp).background(SkeletonColor, RoundedCornerShape(10.dp)))
        }
    }
}

/** Esqueleto de filas (pantallas de categoría). */
@Composable
internal fun HomeSkeleton() {
    val alpha = delayedSkeletonAlpha()
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .graphicsLayer { this.alpha = alpha }
            .padding(start = ContentStart, top = 204.dp),
    ) {
        repeat(2) {
            Box(
                Modifier
                    .width(180.dp)
                    .height(18.dp)
                    .background(SkeletonColor, RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(7) {
                    Box(
                        Modifier
                            .width(PosterWidth)
                            .aspectRatio(2f / 3f)
                            .background(SkeletonColor, RoundedCornerShape(6.dp)),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun HomeError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier.fillMaxSize()) {
    val retryRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { retryRequester.requestFocus() }
    }
    Column(
        modifier.background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, color = Color.White, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        TvButton(text = tr("Reintentar", "Retry"), onClick = onRetry, modifier = Modifier.focusRequester(retryRequester))
    }
}

// --- Claves de foco: "fila/posición virtual" (en filas en bucle el mismo título aparece en varias posiciones) ---

private fun keyOf(row: CatalogRow, virtual: Int) = "${row.id}/$virtual"

private fun firstKey(row: CatalogRow) = keyOf(row, firstVirtualIndex(row.items.size))

private fun findByKey(rows: List<CatalogRow>, key: String?): CatalogItem? {
    if (key == null) return null
    val row = rows.firstOrNull { it.id == key.substringBeforeLast('/') } ?: return null
    if (row.items.isEmpty()) return null
    val virtual = key.substringAfterLast('/').toIntOrNull() ?: return null
    if (!shouldLoop(row.items.size) && virtual >= row.items.size) return null
    return row.items[virtual % row.items.size]
}

/** Tarjeta que recibe el foco al quitar la de [virtual]: la que pasa a ocupar su sitio, la anterior o la fila de abajo. */
private fun neighbourKey(rows: List<CatalogRow>, row: CatalogRow, virtual: Int): String {
    val newSize = row.items.size - 1
    if (newSize <= 0) {
        val below = rows.getOrNull(rows.indexOf(row) + 1) ?: rows.first()
        return firstKey(below)
    }
    val index = minOf(virtual % row.items.size, newSize - 1)
    return keyOf(row, firstVirtualIndex(newSize) + index)
}

