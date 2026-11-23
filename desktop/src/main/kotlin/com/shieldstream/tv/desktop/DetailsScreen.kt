package com.shieldstream.tv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.MediaType

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    repository: DesktopRepository,
    item: CatalogItem,
    library: LibraryStore,
    onPlay: (PlayRequest) -> Unit,
    trakt: TraktManager? = null,
    /** Abierta con «Reproducir» desde el Inicio: empieza sola en cuanto haya fuente. */
    autoPlayOnOpen: Boolean = false,
    showCast: Boolean = true,
    onOpen: (CatalogItem) -> Unit = {},
    onNotice: (String) -> Unit = {},
    service: String = "",
    keepChosenSource: Boolean = false,
    broken: BrokenSources = BrokenSources.default,
    /** Temporada con la que abrir (al pasar al siguiente episodio). */
    startSeason: Int? = null,
    autoNext: Boolean = true,
    matchRefresh: Boolean = false,
) {
    val details by produceState<Load<TitleDetails>>(Load.Loading, item) { value = load { repository.details(item) } }
    val info = (details as? Load.Ok)?.value
    val full = info?.item ?: item
    val isShow = item.type == MediaType.TV

    var season by remember(item) { mutableStateOf(startSeason) }
    LaunchedEffect(info) { if (season == null) season = info?.seasons?.firstOrNull()?.number }
    var episode by remember(item) { mutableStateOf<Episode?>(null) }
    val episodes by produceState<Load<List<Episode>>>(Load.Loading, full.id, season) {
        val s = season ?: return@produceState
        value = Load.Loading
        value = load { repository.episodes(full, s) }
    }
    val wantSources = if (isShow) episode != null else info != null || details is Load.Failed
    val sources by produceState<Load<SourcesState>?>(null, full.id, episode, wantSources) {
        if (!wantSources) {
            value = null
            return@produceState
        }
        value = Load.Loading
        runCatching { repository.sources(full, episode).collect { value = Load.Ok(it) } }
            .onFailure { value = Load.Failed(it.message ?: "No se pudieron buscar fuentes") }
    }
    val label = episode?.let { "${full.title} · T${it.season} E${it.number}" } ?: full.title
    // «Desde el principio» ignora por dónde ibas (solo para la próxima reproducción).
    var fromStart by remember(item) { mutableStateOf(false) }
    fun request(source: DesktopSource, rest: List<DesktopSource> = emptyList()): PlayRequest {
        val saved = library.progressFor(full, episode?.season, episode?.number)?.takeIf { it.resumable && !fromStart }
        fromStart = false
        val tracks = library.tracksFor(full)
        return PlayRequest(
            source, full, episode, label, saved?.positionSec ?: 0.0,
            alternatives = rest,
            service = service,
            audioLang = tracks?.audioLang,
            subLang = tracks?.subLang,
            autoNext = isShow && autoNext,
            matchRefresh = matchRefresh,
        )
    }
    // Probar fuentes en orden hasta que una dé vídeo (o solo la elegida, si así está en Ajustes).
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var trying by remember(item) { mutableStateOf<String?>(null) }
    fun play(candidates: List<DesktopSource>) {
        if (trying != null || candidates.isEmpty()) return
        trying = "Preparando…"
        scope.launch {
            when (val pick = pickPlayable(candidates, service, onTrying = { trying = "Probando ${it.quality.label} · ${it.section}…" })) {
                is Pick.Ready -> {
                    val segments = if (isShow) runCatching { repository.skipSegments(full, episode) }.getOrNull() else null
                    onPlay(request(pick.source, pick.rest).copy(segments = segments))
                }
                is Pick.Failed -> onNotice(pick.message)
            }
            trying = null
        }
    }
    // El episodio que toca: el primero sin terminar de la temporada (o el primero).
    fun nextEpisode(): Episode? {
        val list = (episodes as? Load.Ok)?.value.orEmpty()
        return list.firstOrNull { library.progressFor(full, it.season, it.number)?.finished != true } ?: list.firstOrNull()
    }
    // «Reproducir»: la primera fuente (en caché y con mejor calidad) en cuanto estén cargadas.
    var autoPlay by remember(item) { mutableStateOf(autoPlayOnOpen) }
    LaunchedEffect(autoPlay, episodes) {
        if (!autoPlay || !isShow || episode != null) return@LaunchedEffect
        val list = (episodes as? Load.Ok)?.value ?: return@LaunchedEffect
        val pending = list.firstOrNull { library.progressFor(full, it.season, it.number)?.finished != true }
        // Temporada terminada: se sigue con la primera de la siguiente.
        val nextSeason = info?.seasons?.map { it.number }?.filter { it > (season ?: 0) }?.minOrNull()
        if (pending == null && nextSeason != null) season = nextSeason else episode = pending ?: list.firstOrNull()
    }
    LaunchedEffect(sources, autoPlay) {
        val state = (sources as? Load.Ok)?.value ?: return@LaunchedEffect
        // Se espera a que respondan todos los buscadores para elegir la mejor.
        if (state.pending.isNotEmpty() && state.sources.none { it.cached }) return@LaunchedEffect
        val list = state.sources
        if (autoPlay) {
            autoPlay = false
            play(list.filterNot { broken.isBroken(it.title) })
        }
    }
    // Panel de fuentes: se abre con el botón «Fuentes» (como en la tele), no siempre a la vista.
    var showSources by remember(item) { mutableStateOf(DetailsTourHook.openSources) }
    androidx.compose.runtime.DisposableEffect(showSources) {
        if (!showSources) return@DisposableEffect onDispose {}
        // Mientras está abierto, Escape / «Atrás» lo cierran; al cerrarse, Atrás vuelve a cambiar de pantalla.
        val close: () -> Boolean = { showSources = false; true }
        BackPress.handler = close
        onDispose { if (BackPress.handler === close) BackPress.handler = null }
    }
    val playLabel = run {
        val saved = library.progressFor(full, episode?.season, episode?.number)?.takeIf { it.resumable }
        when {
            saved != null -> "Reanudar " + formatClock(saved.positionSec)
            isShow && episode != null -> "Reproducir T${episode!!.season} E${episode!!.number}"
            else -> "Reproducir"
        }
    }

    Box(Modifier.fillMaxSize()) {
        RemoteImage(full.backdropUrl, Modifier.fillMaxWidth().height(560.dp))
        Box(Modifier.fillMaxWidth().height(560.dp).background(Brush.horizontalGradient(0f to BambiColors.Background, 0.7f to Color.Transparent)))
        Box(Modifier.fillMaxWidth().height(560.dp).background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to BambiColors.Background)))
        Box(Modifier.fillMaxSize().padding(start = 48.dp, end = 32.dp)) {
            LazyColumn(Modifier.widthIn(max = 1080.dp).fillMaxHeight(), contentPadding = PaddingValues(top = 150.dp, bottom = 48.dp)) {
                item(key = "info") { Info(full, info) }
                item(key = "actions") {
                    // Varios botones: si no caben en una línea, bajan a la siguiente.
                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 22.dp),
                    ) {
                        PillButton(playLabel, AppIcons.Play, primary = true) {
                            if (isShow && episode == null) episode = nextEpisode()
                            autoPlay = true
                        }
                        val resumable = library.progressFor(full, episode?.season, episode?.number)?.resumable == true
                        if (resumable) {
                            PillButton("Desde el principio", null) {
                                fromStart = true
                                autoPlay = true
                            }
                        }
                        PillButton("Fuentes", AppIcons.List) {
                            if (isShow && episode == null) episode = nextEpisode()
                            showSources = true
                        }
                        val listed = library.inMyList(full)
                        PillButton(if (listed) "En Mi lista" else "Mi lista", if (listed) AppIcons.Check else AppIcons.Add) { library.toggleMyList(full) }
                        info?.trailerKey?.let { key -> PillButton("Tráiler", null) { openInBrowser("https://www.youtube.com/watch?v=$key") } }
                        if (!isShow) {
                            val watched = library.progressFor(full)?.finished == true
                            PillButton(if (watched) "Vista" else "Marcar vista", if (watched) AppIcons.Check else null) {
                                setWatched(library, trakt, full, listOf(null to null), !watched)
                            }
                        }
                    }
                }
                if (trakt?.canRate() == true && full.id.toIntOrNull() != null) {
                    item(key = "rating") { TraktRating(trakt, full) }
                }
                if (isShow) {
                    item(key = "seasons") {
                        BambiLazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 28.dp, bottom = 12.dp),
                            arrows = false,
                        ) {
                            items(info?.seasons.orEmpty(), key = { it.number }) { s ->
                                FilterChip(
                                    selected = season == s.number,
                                    onClick = { season = s.number; episode = null },
                                    label = { Text(s.name.ifBlank { "Temporada ${s.number}" }) },
                                )
                            }
                        }
                        // Marcar la temporada entera, como en la tele.
                        val list = (episodes as? Load.Ok)?.value.orEmpty()
                        if (list.isNotEmpty()) {
                            val all = list.all { library.progressFor(full, it.season, it.number)?.finished == true }
                            androidx.compose.material3.TextButton(onClick = {
                                setWatched(library, trakt, full, list.map { it.season to it.number }, !all)
                            }) { Text(if (all) "Marcar temporada como no vista" else "Marcar temporada como vista", color = BambiColors.Muted) }
                        }
                    }
                    when (val e = episodes) {
                        Load.Loading -> item(key = "eloading") { Text("Cargando episodios…", color = BambiColors.Muted) }
                        is Load.Failed -> item(key = "efail") { Text(e.message, color = BambiColors.Text) }
                        is Load.Ok -> items(e.value, key = { "${it.season}x${it.number}" }) { ep ->
                            val watched = library.progressFor(full, ep.season, ep.number)?.finished == true
                            // Clic derecho: marcar visto o no visto (en la tele, mantener OK).
                            androidx.compose.foundation.ContextMenuArea(items = {
                                listOf(
                                    androidx.compose.foundation.ContextMenuItem("Ver fuentes") { episode = ep; showSources = true },
                                    androidx.compose.foundation.ContextMenuItem(if (watched) "Marcar como no visto" else "Marcar como visto") {
                                        setWatched(library, trakt, full, listOf(ep.season to ep.number), !watched)
                                    },
                                )
                            }) {
                                EpisodeRow(
                                    ep,
                                    selected = episode == ep,
                                    progress = library.progressFor(full, ep.season, ep.number)?.let { if (it.finished) 1f else it.fraction },
                                    watched = watched,
                                    onClick = {
                                        episode = ep
                                        autoPlay = true
                                    },
                                )
                            }
                        }
                    }
                }
                if (showCast && info?.cast.orEmpty().isNotEmpty()) {
                    item(key = "cast") { CastRow(info!!.cast) }
                }
                info?.saga?.let { saga ->
                    item(key = "saga") {
                        Column(Modifier.padding(top = 24.dp)) {
                            Text(saga.title, color = BambiColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            BambiLazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                                items(saga.items, key = { it.key }) { part ->
                                    PosterCard(part, onClick = { if (part.key != full.key) onOpen(part) }, width = 130.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
        // Panel lateral de fuentes, encima de la ficha (clic fuera, la X o Escape lo cierran).
        androidx.compose.animation.AnimatedVisibility(
            showSources,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
        ) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { showSources = false },
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            showSources,
            enter = androidx.compose.animation.slideInHorizontally { it } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutHorizontally { it } + androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            SourcesPanel(
                sources = sources,
                subtitle = episode?.let { "T${it.season} E${it.number} · ${it.title}" },
                hint = if (isShow) "Elige un episodio para ver sus fuentes." else "Buscando fuentes…",
                trying = trying,
                broken = broken,
                onPlay = { chosen ->
                    val list = (sources as? Load.Ok)?.value?.sources.orEmpty().filterNot { broken.isBroken(it.title) }
                    play(if (keepChosenSource) listOf(chosen) else listOf(chosen) + list.filter { it != chosen })
                },
                onClose = { showSources = false },
                modifier = Modifier.width(640.dp).fillMaxHeight().padding(top = 96.dp, bottom = 24.dp, end = 24.dp)
                    // Que el clic dentro del panel no lo cierre.
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
            )
        }
    }
}

@Composable
private fun Info(item: CatalogItem, info: TitleDetails?) {
    Column(Modifier.widthIn(max = 640.dp)) {
        if (info?.logoUrl != null) {
            LogoImage(
                info.logoUrl,
                Modifier.height(150.dp).widthIn(max = 560.dp).fillMaxWidth(),
                placeholder = { Title(item.title) },
            )
        } else {
            Title(item.title)
        }
        Spacer(Modifier.height(12.dp))
        MetaLine(
            age = info?.ageRating,
            parts = listOfNotNull(
                item.year?.toString(),
                info?.runtimeMinutes?.takeIf { it > 0 }?.let { if (it >= 60) "${it / 60} h ${it % 60} min" else "$it min" },
                info?.genres?.take(3)?.joinToString(", ")?.takeIf { it.isNotBlank() },
            ),
            rating = item.rating,
        )
        info?.tagline?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = BambiColors.Text, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        Text(item.overview, color = BambiColors.Text.copy(alpha = 0.9f), fontSize = 16.sp, lineHeight = 25.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Title(text: String) {
    Text(text, color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Black, maxLines = 2, lineHeight = 54.sp)
}

@Composable
private fun EpisodeRow(episode: Episode, selected: Boolean, progress: Float?, watched: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered = interaction.collectIsHoveredAsState().value || interaction.collectIsFocusedAsState().value
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(shape)
            .background(if (selected) BambiColors.SurfaceHigh else if (hovered) BambiColors.Surface else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.width(176.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)).background(BambiColors.SurfaceHigh)) {
            RemoteImage(episode.stillUrl, Modifier.fillMaxSize())
            if (progress != null && progress > 0f) ProgressBar(progress, Modifier.align(Alignment.BottomCenter).padding(6.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${episode.number}. ${episode.title}", color = BambiColors.Text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (watched) Icon(AppIcons.Check, contentDescription = "Visto", tint = BambiColors.Accent, modifier = Modifier.size(16.dp))
            }
            Text(
                listOfNotNull(episode.airDate, episode.runtimeMinutes?.let { "$it min" }).joinToString("  ·  "),
                color = BambiColors.Muted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(episode.overview, color = BambiColors.Muted, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SourcesPanel(
    sources: Load<SourcesState>?,
    subtitle: String?,
    hint: String,
    trying: String?,
    broken: BrokenSources,
    onPlay: (DesktopSource) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var spanishOnly by remember { mutableStateOf(false) }
    // Cada buscador es un desplegable; de primeras, todos cerrados (como en la tele).
    val open = remember(subtitle) { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    // Panel de cristal oscuro sobre el fondo de la ficha, con filo fino.
    Column(
        modifier.shadow(32.dp, BambiShapes.Panel).clip(BambiShapes.Panel).background(BambiColors.BackgroundRaised)
            .border(1.dp, BambiColors.Stroke, BambiShapes.Panel).padding(24.dp),
    ) {
        val count = (sources as? Load.Ok)?.value?.sources?.size
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (count != null) "Fuentes · $count" else "Fuentes",
                color = BambiColors.Text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            // Como en la tele: «Solo en español».
            FilterChip(selected = spanishOnly, onClick = { spanishOnly = !spanishOnly }, label = { Text("Solo en español") })
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(BambiColors.Glass)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Close, contentDescription = "Cerrar", tint = Color.White, modifier = Modifier.size(20.dp)) }
        }
        subtitle?.let { Text(it, color = BambiColors.Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
        trying?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                CircularProgressIndicator(color = BambiColors.Accent, strokeWidth = 2.dp, modifier = Modifier.width(18.dp).height(18.dp))
                Text(it, color = BambiColors.Muted, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        when (sources) {
            null -> Text(hint, color = BambiColors.Muted)
            Load.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(color = BambiColors.Accent, strokeWidth = 2.dp, modifier = Modifier.width(20.dp).height(20.dp))
                Text("Buscando fuentes…", color = BambiColors.Muted)
            }
            is Load.Failed -> Text(sources.message, color = BambiColors.Text)
            is Load.Ok -> {
                val state = sources.value
                // Como en la tele: una sección por buscador; las que aún buscan o no han dado nada, con su motivo.
                val visible = state.sources.filter { !broken.isBroken(it.title) && (!spanishOnly || it.spanish) }
                val sections = visible.groupBy { it.section }
                    .toList()
                    .sortedByDescending { (name, _) -> name == "En castellano" }
                // Si solo hay un buscador, se abre solo.
                val single = sections.size == 1
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    sections.forEach { (name, list) ->
                        val expanded = open[name] ?: single
                        item(key = "h-$name") {
                            SourceGroupHeader(name.ifBlank { "Fuentes" }, list, expanded) { open[name] = !expanded }
                        }
                        if (expanded) items(list, key = { it.section + it.url }) { source ->
                            // Clic derecho: descartar una fuente rota (se restaura en Ajustes).
                            androidx.compose.foundation.ContextMenuArea(items = {
                                listOf(androidx.compose.foundation.ContextMenuItem("Descartar fuente (no funciona)") { broken.discard(source.title) })
                            }) { SourceRow(source, onClick = { onPlay(source) }) }
                        }
                    }
                    state.pending.forEach { name -> item(key = "p-$name") { SectionHeader(name, "buscando…") } }
                    state.unavailable.forEach { (name, why) -> item(key = "u-$name") { SectionHeader(name, why) } }
                    if (visible.isEmpty() && state.pending.isEmpty() && state.unavailable.isEmpty()) {
                        item(key = "empty") { Text(if (spanishOnly) "No hay fuentes en español." else "No hay fuentes para esto ahora mismo.", color = BambiColors.Muted) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(source: DesktopSource, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered = interaction.collectIsHoveredAsState().value || interaction.collectIsFocusedAsState().value
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (hovered) BambiColors.SurfaceHigh else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Calidad: 4K en dorado, como una etiqueta premium; lo desconocido, discreto.
        val uhd = source.quality == com.shieldstream.tv.model.Quality.UHD_2160
        Box(
            Modifier.width(56.dp).clip(BambiShapes.Badge)
                .background(if (uhd) BambiColors.Gold.copy(alpha = 0.12f) else BambiColors.SurfaceHigh)
                .border(1.dp, if (uhd) BambiColors.Gold.copy(alpha = 0.6f) else Color.Transparent, BambiShapes.Badge)
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                source.quality.label.takeIf { it != "?" } ?: "—",
                color = if (uhd) BambiColors.Gold else BambiColors.Text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(source.title, color = BambiColors.Text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (source.detail.isNotBlank()) {
                Text(source.detail, color = BambiColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (source.cached) Text("⚡", color = BambiColors.Cached, fontSize = 16.sp)
    }
}

/** 4512 s → "1:15:12"; 734 s → "12:14". */
fun formatClock(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

@Composable
private fun SectionHeader(title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = BambiColors.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        Text(detail, color = BambiColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}


/** Un buscador (Torrentio, Comet…) como desplegable: nombre, cuántas, cuántas en caché y la mejor calidad. */
@Composable
private fun SourceGroupHeader(title: String, list: List<DesktopSource>, expanded: Boolean, onToggle: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered = interaction.collectIsHoveredAsState().value || interaction.collectIsFocusedAsState().value
    val rotation by androidx.compose.animation.core.animateFloatAsState(if (expanded) 180f else 0f)
    val cached = list.count { it.cached }
    val best = list.map { it.quality }.filter { it.rank > 0 }.maxByOrNull { it.rank }
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp).clip(BambiShapes.Control)
            .background(if (hovered || expanded) BambiColors.SurfaceHigh else BambiColors.Surface)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, color = BambiColors.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("${list.size}", color = BambiColors.Muted, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        if (cached > 0) Text("⚡ $cached", color = BambiColors.Cached, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        best?.let {
            val uhd = it == com.shieldstream.tv.model.Quality.UHD_2160
            Text(
                "hasta ${it.label}",
                color = if (uhd) BambiColors.Gold else BambiColors.Muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Icon(AppIcons.ExpandMore, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = rotation })
    }
}

/** Tu nota en Trakt (1–10), como «Puntuar» en la tele. Pulsar la nota que ya tienes la quita. */
@Composable
private fun TraktRating(trakt: TraktManager, item: CatalogItem) {
    val target = if (item.type == MediaType.TV) com.shieldstream.tv.domain.TraktRatingTarget.Show(item.id)
    else com.shieldstream.tv.domain.TraktRatingTarget.Movie(item.id)
    var rating by remember(item.key) { mutableStateOf<Int?>(null) }
    var failed by remember(item.key) { mutableStateOf(false) }
    LaunchedEffect(item.key) { rating = runCatching { trakt.rating(target) }.getOrNull() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Column(Modifier.padding(top = 18.dp)) {
        Text(if (failed) "No se pudo guardar la nota en Trakt" else "Tu nota en Trakt", color = BambiColors.Muted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            for (n in 1..10) {
                val selected = rating != null && n <= rating!!
                Box(
                    Modifier.size(30.dp)
                        .background(if (selected) BambiColors.Accent else BambiColors.SurfaceHigh, BambiShapes.Badge)
                        .clickable {
                            val new = if (rating == n) null else n
                            val old = rating
                            rating = new
                            scope.launch {
                                failed = runCatching { trakt.rate(target, new) }.isFailure
                                if (failed) rating = old
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) { Text("$n", color = if (selected) BambiColors.OnAccent else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

/** Reparto: foto, nombre y personaje (se oculta en Ajustes → «Reparto en las fichas»). */
@Composable
private fun CastRow(cast: List<CastMember>) {
    Column(Modifier.padding(top = 24.dp)) {
        Text("Reparto", color = BambiColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        BambiLazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
            items(cast, key = { it.name + it.role }) { c ->
                Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(84.dp).clip(androidx.compose.foundation.shape.CircleShape).background(BambiColors.SurfaceHigh), contentAlignment = Alignment.Center) {
                        Text(c.name.take(1), color = BambiColors.Muted, fontWeight = FontWeight.Bold)
                        RemoteImage(c.photoUrl, Modifier.fillMaxSize())
                    }
                    Text(c.name, color = BambiColors.Text, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                    c.role?.let { Text(it, color = BambiColors.Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                }
            }
        }
    }
}

/**
 * Marcar visto o no visto: película (null to null), episodios o una temporada entera. Se guarda en el perfil y, si
 * está conectado, en Trakt.
 */
internal fun setWatched(library: LibraryStore, trakt: TraktManager?, item: CatalogItem, which: List<Pair<Int?, Int?>>, watched: Boolean) {
    val now = System.currentTimeMillis()
    val keys = which.map { (s, e) -> progressKey(item.type.name, item.id, s, e) }
    if (watched) {
        library.importProgress(
            which.map { (s, e) ->
                val old = library.progressFor(item, s, e)
                SavedProgress(SavedItem.of(item), s, e, old?.durationSec ?: 1.0, old?.durationSec ?: 1.0, now, finished = true)
            },
        )
    } else {
        library.removeProgress(keys)
    }
    trakt?.setWatched(keys, watched)
}

/** Solo para la prueba con datos reales: abrir la ficha con el panel de fuentes ya abierto. */
object DetailsTourHook {
    @Volatile
    var openSources = false
}
