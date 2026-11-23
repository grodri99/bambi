package com.shieldstream.tv.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.MediaType

@Composable
fun HomeScreen(
    repository: DesktopRepository,
    catalog: Catalog,
    library: LibraryStore,
    onOpen: (CatalogItem) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    kids: Boolean = false,
    trakt: TraktManager? = null,
    profileId: String? = null,
    hiddenCategories: Set<String> = com.shieldstream.tv.model.Category.DEFAULT_HIDDEN,
    /** «Reproducir» de la portada y «Reproduce algo»: abre la ficha y empieza solo. */
    onPlay: (CatalogItem) -> Unit = onOpen,
    onSettings: () -> Unit,
) {
    // Lo ya cargado se enseña al momento (al volver de una ficha no se recarga ni se pierde la posición).
    val cached = remember(catalog, kids) { HomeCache.get(catalog, kids) }
    val state by produceState<Load<List<HomeRow>>>(cached?.let { Load.Ok(it) } ?: Load.Loading, repository, catalog, kids) {
        if (cached != null) return@produceState
        value = load { repository.homeRows(catalog, kids) }.also { if (it is Load.Ok) HomeCache.put(catalog, kids, it.value) }
    }
    val top10 by produceState(emptyList<CatalogItem>(), catalog, kids) { value = runCatching { repository.top10(catalog, kids) }.getOrDefault(emptyList()) }
    // Las categorías del perfil, una detrás de otra, debajo de las filas fijas (como en la tele).
    val categories = remember(hiddenCategories, catalog, kids) { CategorySpecs.visible(hiddenCategories, catalog, kids) }
    val categoryRows by produceState(emptyList<HomeRow>(), categories, catalog) {
        val loaded = ArrayList<HomeRow>()
        for (c in categories) {
            loaded += runCatching { repository.categoryRows(c, catalog) }.getOrDefault(emptyList())
            value = loaded.toList()
        }
    }
    val scope = rememberCoroutineScope()
    // Recomendaciones de Trakt (si el perfil está conectado): llegan aparte, sin frenar el Inicio.
    val recommended by produceState(emptyList<HomeRow>(), trakt, catalog, profileId) {
        val t = trakt ?: return@produceState
        value = listOfNotNull(
            if (catalog != Catalog.SHOWS) HomeRow("Películas recomendadas para ti", t.recommendations(MediaType.MOVIE)) else null,
            if (catalog != Catalog.MOVIES) HomeRow("Series recomendadas para ti", t.recommendations(MediaType.TV)) else null,
        ).filter { it.items.isNotEmpty() }
    }
    val wanted: (String) -> Boolean = { type ->
        when (catalog) {
            Catalog.ALL -> true
            Catalog.SHOWS -> type == MediaType.TV.name
            Catalog.MOVIES -> type == MediaType.MOVIE.name
        }
    }
    when (val s = state) {
        Load.Loading -> Centered { CircularProgressIndicator(color = BambiColors.Accent) }
        is Load.Failed -> Centered {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.message, color = BambiColors.Text, fontSize = 18.sp)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onSettings) { Text("Ir a Ajustes") }
            }
        }
        is Load.Ok -> LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 56.dp)) {
            val hero = s.value.flatMap { it.items }.firstOrNull { it.backdropUrl != null }
            if (hero != null) item(key = "hero") { Billboard(hero, repository, library, onOpen, onPlay) }
            else item(key = "space") { Spacer(Modifier.height(80.dp)) }
            val continuing = library.continueWatching().filter { wanted(it.item.type) }
            if (continuing.isNotEmpty()) item(key = "continue") { ContinueRow(continuing, onOpen) { library.clear(it.key) } }
            val myList = library.myList.filter { wanted(it.type) }
            if (myList.isNotEmpty() && catalog == Catalog.ALL) {
                item(key = "mylist") { RowSection(HomeRow("Mi lista", myList.map { it.toItem() }), onOpen) }
            }
            item(key = "play-something") {
                PlaySomething(
                    onPlaySomething = {
                        // Como en la tele: lo que estabas viendo, luego Mi lista y, si no hay nada, una tendencia.
                        val candidates = library.continueWatching().filter { wanted(it.item.type) }.map { it.item.toItem() }
                            .ifEmpty { library.myList.filter { wanted(it.type) }.map { it.toItem() } }
                            .ifEmpty { top10 }
                        candidates.randomOrNull()?.let(onPlay)
                    },
                    onSurprise = { scope.launch { runCatching { repository.surprise(kids) }.getOrNull()?.let(onOpen) } },
                )
            }
            if (top10.isNotEmpty()) item(key = "top10") { Top10Row(top10, onOpen) }
            items(recommended, key = { "trakt-" + it.title }) { row -> RowSection(row, onOpen) }
            items(s.value, key = { it.title }) { row -> RowSection(row, onOpen) }
            items(categoryRows, key = { "cat-" + it.title }) { row -> RowSection(row, onOpen) }
        }
    }
}

/** Cabecera del Inicio como en la tele: el fondo a la derecha, y a la izquierda el logo, los datos y los botones. */
@Composable
private fun Billboard(item: CatalogItem, repository: DesktopRepository, library: LibraryStore, onOpen: (CatalogItem) -> Unit, onPlay: (CatalogItem) -> Unit) {
    val details by produceState<TitleDetails?>(null, item.key) { value = runCatching { repository.details(item) }.getOrNull() }
    Box(Modifier.fillMaxWidth().height(660.dp)) {
        Box(Modifier.align(Alignment.TopEnd).fillMaxWidth(0.78f).fillMaxHeight(0.9f)) {
            RemoteImage(item.backdropUrl, Modifier.fillMaxSize(), alignment = Alignment.TopCenter)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to BambiColors.Background, 0.30f to Color.Black.copy(alpha = 0.55f), 0.6f to Color.Transparent)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to BambiColors.Background)))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 40.dp).widthIn(max = 660.dp)) {
            // El logo de la película manda: grande, como en Netflix.
            val logo = details?.logoUrl
            if (logo != null) {
                LogoImage(logo, Modifier.height(170.dp).widthIn(max = 600.dp).fillMaxWidth(), alignment = Alignment.BottomStart)
            } else {
                Text(item.title, color = Color.White, fontSize = 60.sp, fontWeight = FontWeight.Black, maxLines = 2, lineHeight = 62.sp)
            }
            Spacer(Modifier.height(18.dp))
            MetaLine(
                age = details?.ageRating,
                parts = listOfNotNull(
                    if (item.type == MediaType.TV) "Serie" else "Película",
                    item.year?.toString(),
                    details?.genres?.take(2)?.joinToString(" · ")?.takeIf { it.isNotBlank() },
                ),
                rating = item.rating,
            )
            Spacer(Modifier.height(14.dp))
            Text(item.overview, color = Color.White.copy(alpha = 0.88f), fontSize = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 26.sp)
            Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Como en la tele, el foco empieza en «Reproducir» (para manejarlo con el teclado o un mando).
                val first = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
                PillButton("Reproducir", AppIcons.Play, primary = true, focusRequester = first) { onPlay(item) }
                PillButton(if (library.inMyList(item)) "En Mi lista" else "Mi lista", if (library.inMyList(item)) AppIcons.Check else AppIcons.Add) { library.toggleMyList(item) }
                PillButton("Más información", null) { onOpen(item) }
            }
        }
    }
}

/** Botón de la tele: blanco el principal, gris translúcido el resto; al pasar el ratón se vuelve blanco. */
@Composable
fun PillButton(
    text: String,
    icon: ImageVector?,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered = interaction.collectIsHoveredAsState().value || interaction.collectIsFocusedAsState().value
    val scale by animateFloatAsState(if (hovered) 1.04f else 1f)
    val white = primary || hovered
    // Principal: blanco sólido. Secundario: cristal con filo fino; al pasar por encima, blanco.
    Row(
        Modifier.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .scale(scale)
            .then(if (primary && hovered) Modifier.border(2.dp, BambiColors.Accent, BambiShapes.Control) else Modifier)
            .background(if (white) Color.White else BambiColors.Glass, BambiShapes.Control)
            .then(if (!white) Modifier.border(1.dp, Color.White.copy(alpha = 0.16f), BambiShapes.Control) else Modifier)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (white) Color.Black else Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = if (white) Color.Black else Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

/** «Seguir viendo»: fotos horizontales con la barra de lo que llevas visto. */
@Composable
private fun ContinueRow(entries: List<SavedProgress>, onOpen: (CatalogItem) -> Unit, onRemove: (SavedProgress) -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        RowTitle("Seguir viendo")
        BambiLazyRow(contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(entries, key = { it.key }) { entry ->
                val interaction = remember { MutableInteractionSource() }
                val hovered = interaction.collectIsHoveredAsState().value || interaction.collectIsFocusedAsState().value
                val scale by animateFloatAsState(if (hovered) 1.05f else 1f)
                // Como mantener OK en la tele: clic derecho o Supr lo quitan de «Seguir viendo» (y de Trakt).
                androidx.compose.foundation.ContextMenuArea(items = {
                    listOf(androidx.compose.foundation.ContextMenuItem("Quitar de Seguir viendo") { onRemove(entry) })
                }) {
                Column(Modifier.width(300.dp)) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(16f / 9f).scale(scale).clip(BambiShapes.Poster)
                            .onPreviewKeyEvent { e ->
                                if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && e.key == androidx.compose.ui.input.key.Key.Delete) {
                                    onRemove(entry); true
                                } else false
                            }
                            .background(BambiColors.Placeholder)
                            .then(if (hovered) Modifier.border(3.dp, Color.White, BambiShapes.Poster) else Modifier)
                            .hoverable(interaction)
                            .clickable(interactionSource = interaction, indication = null) { onOpen(entry.item.toItem()) },
                    ) {
                        RemoteImage(entry.item.backdropUrl ?: entry.item.posterUrl, Modifier.fillMaxSize(), placeholder = { TitlePlaceholder(entry.item.title) })
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))))
                        // Botón de play en un círculo de cristal que crece al pasar por encima.
                        Box(
                            Modifier.align(Alignment.Center).size(if (hovered) 58.dp else 48.dp)
                                .background(Color.Black.copy(alpha = 0.45f), androidx.compose.foundation.shape.CircleShape)
                                .border(1.5.dp, Color.White.copy(alpha = 0.8f), androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Icon(AppIcons.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp)) }
                        ProgressBar(entry.fraction, Modifier.align(Alignment.BottomCenter).padding(12.dp))
                    }
                    Text(
                        entry.item.title + continueLabel(entry),
                        color = BambiColors.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                }
            }
        }
    }
}

@Composable
private fun RowTitle(text: String) {
    Text(text, color = BambiColors.Text, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp, modifier = Modifier.padding(start = 48.dp, top = 22.dp, bottom = 6.dp))
}

@Composable
fun RowSection(row: HomeRow, onOpen: (CatalogItem) -> Unit) {
    Column {
        RowTitle(row.title)
        BambiLazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(row.items, key = { it.key }) { PosterCard(it, onClick = { onOpen(it) }) }
        }
    }
}

@Composable
fun MyListScreen(library: LibraryStore, onOpen: (CatalogItem) -> Unit, initialFilter: Catalog = Catalog.ALL) {
    var filter by remember { mutableStateOf(initialFilter) }
    Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 88.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Mi lista", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(16.dp))
            // Como en la tele: Todo · Series · Películas.
            for ((value, label) in listOf(Catalog.ALL to "Todo", Catalog.SHOWS to "Series", Catalog.MOVIES to "Películas")) {
                androidx.compose.material3.FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(20.dp))
        val shown = library.myList.filter {
            when (filter) {
                Catalog.ALL -> true
                Catalog.SHOWS -> it.type == MediaType.TV.name
                Catalog.MOVIES -> it.type == MediaType.MOVIE.name
            }
        }
        if (shown.isEmpty()) {
            Text(if (library.myList.isEmpty()) "Aún no has añadido nada. En cualquier ficha, pulsa «Mi lista»." else "Nada en este filtro.", color = BambiColors.Muted)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(158.dp),
                contentPadding = PaddingValues(bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                items(shown, key = { it.type + it.id }) { saved -> PosterCard(saved.toItem(), onClick = { onOpen(saved.toItem()) }) }
            }
        }
    }
}

/** «Reproduce algo» y «Sorpréndeme», como la tarjeta de la tele. */
@Composable
private fun PlaySomething(onPlaySomething: () -> Unit, onSurprise: () -> Unit) {
    Row(Modifier.padding(start = 48.dp, top = 18.dp, bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("¿No sabes qué ver?", color = BambiColors.Muted, fontSize = 14.sp, modifier = Modifier.padding(end = 6.dp))
        QuickChip("Reproduce algo", AppIcons.Play, onPlaySomething)
        QuickChip("Sorpréndeme", AppIcons.Star, onSurprise)
    }
}

@Composable
private fun QuickChip(text: String, icon: ImageVector, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered = interaction.collectIsHoveredAsState().value || interaction.collectIsFocusedAsState().value
    Row(
        Modifier.background(if (hovered) Color.White else BambiColors.Glass, BambiShapes.Pill)
            .border(1.dp, if (hovered) Color.White else Color.White.copy(alpha = 0.12f), BambiShapes.Pill)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (hovered) Color.Black else Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = if (hovered) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** Top 10 de hoy: el número grande detrás del póster, como en Netflix. */
@Composable
private fun Top10Row(items: List<CatalogItem>, onOpen: (CatalogItem) -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        RowTitle("Top 10 hoy")
        BambiLazyRow(contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items.take(10).withIndex().toList(), key = { "top-" + it.value.key }) { (index, item) ->
                // Número hueco con filo, medio escondido detrás del póster, como el Top 10 de Netflix.
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${index + 1}",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 150.sp,
                        fontWeight = FontWeight.Black,
                        style = androidx.compose.ui.text.TextStyle(drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f)),
                        modifier = Modifier.padding(bottom = 18.dp).offset(x = 18.dp),
                    )
                    PosterCard(item, onClick = { onOpen(item) }, width = 136.dp)
                }
            }
        }
    }
}

@Composable
fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/** Como en la tele: «  ·  T1:E6 · 14 min» (lo que queda). */
internal fun continueLabel(entry: SavedProgress): String {
    val left = ((entry.durationSec - entry.positionSec) / 60).toInt()
    val episode = if (entry.season != null) "T${entry.season}:E${entry.episode}" else null
    val rest = if (entry.durationSec > 0 && left > 0) "$left min" else null
    return listOfNotNull(episode, rest).joinToString(" · ").let { if (it.isEmpty()) "" else "  ·  $it" }
}

/** Filas del Inicio ya cargadas (30 minutos), como el «Inicio guardado» de la tele. */
object HomeCache {
    private const val TTL_MS = 30 * 60_000L
    private val rows = HashMap<String, Pair<Long, List<HomeRow>>>()

    @Synchronized
    fun get(catalog: Catalog, kids: Boolean): List<HomeRow>? =
        rows["$catalog-$kids"]?.takeIf { System.currentTimeMillis() - it.first < TTL_MS }?.second

    @Synchronized
    fun put(catalog: Catalog, kids: Boolean, value: List<HomeRow>) {
        rows["$catalog-$kids"] = System.currentTimeMillis() to value
    }

    @Synchronized
    fun clear() = rows.clear()
}

/** Línea de datos de la portada: edad en una etiqueta con filo, nota con estrella dorada y el resto separado por puntos. */
@Composable
fun MetaLine(age: String?, parts: List<String>, rating: Float?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        age?.let {
            Text(
                it,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.6f), BambiShapes.Badge).padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
        rating?.takeIf { it > 0 }?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Star, contentDescription = null, tint = BambiColors.Gold, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("%.1f".format(it), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(parts.joinToString("  ·  "), color = Color.White.copy(alpha = 0.9f), fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}
