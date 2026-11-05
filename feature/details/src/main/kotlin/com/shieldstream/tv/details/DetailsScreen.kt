package com.shieldstream.tv.details

import com.shieldstream.tv.domain.SourceProvider
import com.shieldstream.tv.model.tr
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import com.shieldstream.tv.model.CatalogDetails
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.HdrType
import com.shieldstream.tv.model.StreamSource
import com.shieldstream.tv.model.WatchProgress
import com.shieldstream.tv.ui.component.AppIcons
import com.shieldstream.tv.ui.component.ProvideMinimalScroll
import com.shieldstream.tv.ui.component.ProvidePivotScroll
import com.shieldstream.tv.ui.component.TvButton
import com.shieldstream.tv.ui.component.onBackKey
import com.shieldstream.tv.ui.component.tvClickable
import java.util.Locale

private val Green = Color(0xFF46D369)
private val Grey = Color(0xFFB3B3B3)

@Composable
fun DetailsScreen(
    onBack: () -> Unit,
    onPlay: () -> Unit,
    viewModel: DetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Solo con la ficha visible (STARTED; tapada por el reproductor baja a CREATED): lo que llegue tarde se descarta.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    DetailsEvent.OpenPlayer -> onPlay()
                }
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onScreenHidden() }
    // De vuelta del reproductor: si pidió el siguiente episodio, arranca aquí (ver onScreenResumed).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onScreenResumed() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val s = state) {
            DetailsUiState.Loading -> CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
            is DetailsUiState.Error -> Text(
                text = s.message,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
            is DetailsUiState.Content -> DetailsContent(s, viewModel, onBack)
        }
    }
}

@Composable
private fun DetailsContent(
    state: DetailsUiState.Content,
    viewModel: DetailsViewModel,
    onBack: () -> Unit,
) {
    var panelOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = panelOpen) { panelOpen = false }
    BackHandler(enabled = !panelOpen) { onBack() }

    val playRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { playRequester.requestFocus() }
    }
    // Al cerrar el panel el foco vuelve al botón principal.
    LaunchedEffect(panelOpen) {
        if (!panelOpen) {
            withFrameNanos { }
            runCatching { playRequester.requestFocus() }
        }
    }

    val item = state.details.item

    Box(Modifier.fillMaxSize().onBackKey(enabled = panelOpen) { panelOpen = false }) {
        // Fondo: backdrop a pantalla completa con degradados a negro (izquierda y abajo).
        AsyncImage(
            model = item.backdropUrl ?: item.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color.Black,
                    0.40f to Color.Black.copy(alpha = 0.85f),
                    0.75f to Color.Transparent,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.40f to Color.Transparent, 1f to Color.Black),
            ),
        )

        ProvideMinimalScroll(margin = 72.dp) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 44.dp, bottom = 32.dp),
        ) {
            InfoBlock(
                details = state.details,
                state = state,
                interactive = !panelOpen,
                playRequester = playRequester,
                onPlay = { viewModel.play() },
                onPlayFromStart = { viewModel.play(fromStart = true) },
                onOpenSources = {
                    viewModel.ensureSources()
                    panelOpen = true
                },
                onToggleWatched = { if (state.isSeries) viewModel.toggleSeasonWatched() else viewModel.toggleMovieWatched() },
                onToggleMyList = viewModel::toggleMyList,
                modifier = Modifier.padding(start = 56.dp, end = 56.dp),
            )

            if (state.isSeries) {
                Spacer(Modifier.height(28.dp))
                SeasonsAndEpisodes(
                    state = state,
                    interactive = !panelOpen,
                    onSeason = { viewModel.selectSeason(it) },
                    onEpisode = { viewModel.playEpisode(it) },
                    onEpisodeLongPress = { viewModel.toggleEpisodeWatched(it) },
                )
            }
        }
        }

        AnimatedVisibility(
            visible = panelOpen,
            enter = fadeIn() + slideInHorizontally { it / 3 },
            exit = fadeOut() + slideOutHorizontally { it / 3 },
            modifier = Modifier.zIndex(2f),
        ) {
            SourcesPanel(
                state = state,
                onPick = { source ->
                    panelOpen = false
                    viewModel.playSource(source)
                },
                isBlocked = viewModel::isBlocked,
                onToggleSpanishOnly = viewModel::toggleSpanishOnly,
            )
        }
    }

    state.message?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3500)
            viewModel.consumeMessage()
        }
    }
}

@Composable
private fun InfoBlock(
    details: CatalogDetails,
    state: DetailsUiState.Content,
    interactive: Boolean,
    playRequester: FocusRequester,
    onPlay: () -> Unit,
    onPlayFromStart: () -> Unit,
    onOpenSources: () -> Unit,
    onToggleWatched: () -> Unit,
    onToggleMyList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = details.item
    Column(modifier.widthIn(max = 600.dp)) {
        // Logo del título si TMDB lo tiene (como Netflix); si no, texto grande.
        if (details.logoUrl != null) {
            AsyncImage(
                model = details.logoUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                modifier = Modifier.heightIn(max = 110.dp).widthIn(max = 420.dp),
            )
        } else {
            Text(
                text = item.title,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(14.dp))

        MetaRow(details, state)
        if (details.genres.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(details.genres.take(4).joinToString("  ·  "), color = Grey, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = item.overview.ifBlank { details.tagline.orEmpty() },
            color = Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(20.dp))

        // Sin el tope de 600 dp del texto: con "Desde el principio" y "Marcar vista" son 4 botones y no caben.
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true),
        ) {
            val progress = state.mainProgress
            val resumable = progress?.isResumable == true
            val episodeCode = state.selectedEpisode?.takeIf { state.isSeries }?.let { " ${it.code}" }.orEmpty()
            val playLabel = when {
                state.pendingPlay -> tr("Preparando…", "Preparing…")
                resumable -> tr("Reanudar$episodeCode", "Resume$episodeCode")
                else -> tr("Reproducir$episodeCode", "Play$episodeCode")
            }
            TvButton(
                text = playLabel,
                icon = AppIcons.Play,
                onClick = onPlay,
                enabled = interactive,
                modifier = Modifier.focusRequester(playRequester),
            )
            if (resumable && !state.pendingPlay) {
                TvButton(
                    text = tr("Desde el principio", "From the beginning"),
                    onClick = onPlayFromStart,
                    enabled = interactive,
                )
            }
            TvButton(
                text = tr("Fuentes", "Sources"),
                icon = AppIcons.List,
                onClick = onOpenSources,
                enabled = interactive,
            )
            // Marcar a mano: la película, o la temporada que se ve (los episodios sueltos: mantener OK en su tarjeta).
            val watchedLabel = if (state.isSeries) {
                val season = state.selectedSeason
                val eps = (state.episodes as? EpisodesState.Ready)?.episodes.orEmpty()
                if (season != null && eps.isNotEmpty()) {
                    if (eps.all { state.progressOf(it)?.isWatched == true }) tr("T$season vista", "S$season watched")
                    else tr("Marcar T$season vista", "Mark S$season watched")
                } else null
            } else {
                if (state.mainProgress?.isWatched == true) tr("Vista", "Watched") else tr("Marcar vista", "Mark watched")
            }
            if (watchedLabel != null && !state.pendingPlay) {
                val done = if (state.isSeries) {
                    (state.episodes as? EpisodesState.Ready)?.episodes.orEmpty().let { eps ->
                        eps.isNotEmpty() && eps.all { state.progressOf(it)?.isWatched == true }
                    }
                } else {
                    state.mainProgress?.isWatched == true
                }
                TvButton(
                    text = watchedLabel,
                    icon = if (done) AppIcons.Check else null,
                    onClick = onToggleWatched,
                    enabled = interactive,
                )
            }
            // Mi lista (como Netflix): + para añadir, ✓ si ya está (OK la quita).
            if (!state.pendingPlay) {
                TvButton(
                    text = if (state.inMyList) tr("En mi lista", "In my list") else tr("Mi lista", "My list"),
                    icon = if (state.inMyList) AppIcons.Check else AppIcons.Add,
                    onClick = onToggleMyList,
                    enabled = interactive,
                )
            }
        }
        // Barra de visionado bajo los botones (película o episodio seleccionado), como Netflix.
        state.mainProgress?.takeIf { it.isResumable }?.let { p ->
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                WatchBar(fraction = p.fraction, modifier = Modifier.width(240.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    text = buildString {
                        state.selectedEpisode?.takeIf { state.isSeries }?.let { append(it.code).append(" · ") }
                        val left = formatRuntime(((p.remainingMs + 59_999) / 60_000).toInt())
                        append(tr("Quedan $left", "$left left"))
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        SourcesStatus(state)
    }
}

@Composable
private fun MetaRow(details: CatalogDetails, state: DetailsUiState.Content) {
    val item = details.item
    Row(verticalAlignment = Alignment.CenterVertically) {
        item.rating?.takeIf { it > 0f }?.let {
            Text(
                text = tr("${(it * 10).toInt()}% valoración", "${(it * 10).toInt()}% rating"),
                color = Green,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.width(14.dp))
        }
        item.year?.let {
            Text(it.toString(), color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(14.dp))
        }
        val duration = when {
            state.isSeries && details.seasons.isNotEmpty() ->
                if (details.seasons.size == 1) tr("1 temporada", "1 season") else tr("${details.seasons.size} temporadas", "${details.seasons.size} seasons")
            else -> details.runtimeMinutes?.let { formatRuntime(it) }
        }
        duration?.let {
            Text(it, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(14.dp))
        }
        // Mejor calidad disponible según las fuentes ya cargadas.
        (state.sources as? SourcesState.Ready)?.sources?.firstOrNull()?.let { best ->
            Badge(best.quality.label)
            if (best.hdrType != HdrType.SDR) {
                Spacer(Modifier.width(8.dp))
                Badge(best.hdrType.label)
            }
        }
    }
}

@Composable
private fun Badge(text: String) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier
            .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
private fun SourcesStatus(state: DetailsUiState.Content) {
    val text = state.message ?: when (val s = state.sources) {
        SourcesState.Idle -> if (state.isSeries) tr("Elige un episodio o pulsa Reproducir · mantén OK en un episodio para marcarlo visto", "Pick an episode or press Play · hold OK on an episode to mark it watched") else ""
        SourcesState.Loading -> tr("Buscando fuentes…", "Finding sources…")
        is SourcesState.Error -> s.message
        is SourcesState.Ready -> {
            val sources = s.sources.filter { !state.spanishOnly || it.advertisesSpanishAudio }
            val cached = sources.count { it.isCached }
            val scope = state.selectedEpisode?.takeIf { state.isSeries }?.let { tr(" para ${it.code}", " for ${it.code}") }.orEmpty()
            when {
                sources.isEmpty() && s.pending.isNotEmpty() -> tr("Buscando fuentes…", "Finding sources…")
                sources.isEmpty() && state.spanishOnly -> tr("No hay fuentes en español para este perfil", "No Spanish sources available for this profile")
                sources.isEmpty() -> tr("Sin fuentes$scope", "No sources$scope")
                cached > 0 -> {
                    val service = sources.firstNotNullOfOrNull { it.debrid }
                    val where = if (service == null) tr("ya en tu Real-Debrid", "already in your Real-Debrid") else tr("en la caché de $service", "cached on $service")
                    tr("${sources.distinctBy { it.infoHash.lowercase() }.size} fuentes$scope · $cached $where ⚡", "${sources.distinctBy { it.infoHash.lowercase() }.size} sources$scope · $cached $where ⚡")
                }
                else -> tr("${sources.distinctBy { it.infoHash.lowercase() }.size} fuentes$scope", "${sources.distinctBy { it.infoHash.lowercase() }.size} sources$scope")
            }
        }
    }
    if (text.isNotEmpty()) {
        Text(text, color = Grey, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SeasonsAndEpisodes(
    state: DetailsUiState.Content,
    interactive: Boolean,
    onSeason: (Int) -> Unit,
    onEpisode: (Episode) -> Unit,
    onEpisodeLongPress: (Episode) -> Unit,
) {
    val seasons = state.details.seasons
    Column {
        if (seasons.size > 1) {
            ProvidePivotScroll(leading = 56.dp) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 56.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(seasons, key = { it.number }) { season ->
                        SeasonChip(
                            label = tr("Temporada ${season.number}", "Season ${season.number}"),
                            selected = season.number == state.selectedSeason,
                            enabled = interactive,
                            onClick = { onSeason(season.number) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        } else {
            Text(
                text = tr("Episodios", "Episodes"),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(start = 56.dp, bottom = 10.dp),
            )
        }

        when (val eps = state.episodes) {
            EpisodesState.Loading -> Text(tr("Cargando episodios…", "Loading episodes…"), color = Grey, modifier = Modifier.padding(start = 56.dp))
            is EpisodesState.Error -> Text(eps.message, color = Grey, modifier = Modifier.padding(start = 56.dp))
            is EpisodesState.Ready -> ProvidePivotScroll(leading = 56.dp) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 56.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(eps.episodes, key = { "${it.season}_${it.number}" }) { ep ->
                        EpisodeCard(
                            episode = ep,
                            progress = state.progressOf(ep),
                            selected = ep == state.selectedEpisode,
                            loading = ep == state.selectedEpisode && state.pendingPlay,
                            enabled = interactive,
                            onClick = { onEpisode(ep) },
                            onLongClick = { onEpisodeLongPress(ep) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> Color.White
        selected -> Color.White.copy(alpha = 0.25f)
        else -> Color.Transparent
    }
    Text(
        text = label,
        color = if (focused) Color.Black else Color.White,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .then(if (!focused && !selected) Modifier.border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(20.dp)) else Modifier)
            .tvClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    )
}

@Composable
private fun EpisodeCard(
    episode: Episode,
    progress: WatchProgress?,
    selected: Boolean,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "episodeScale")
    val shape = RoundedCornerShape(6.dp)
    Column(
        Modifier
            .width(240.dp)
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape)
                .background(Color(0xFF232327))
                .then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                // Mantener OK: visto ⇄ no visto. Con el panel de fuentes abierto (enabled = false) ni se enfoca.
                .then(if (enabled) Modifier.tvClickable(onLongClick = onLongClick, onClick = onClick) else Modifier.tvClickable(enabled = false) {}),
        ) {
            if (episode.stillUrl != null) {
                AsyncImage(
                    model = episode.stillUrl,
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Visto → tick; a medias → barra roja de por dónde vas (abajo de la miniatura).
            if (progress?.isWatched == true) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color.Black.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✓", color = Green, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                }
            } else if (progress != null && progress.fraction > 0.01f) {
                WatchBar(
                    fraction = progress.fraction,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
            if (focused || loading) {
                Box(
                    Modifier.align(Alignment.Center).size(44.dp).clip(RoundedCornerShape(22.dp))
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (loading) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(AppIcons.Play, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${episode.number}. ${episode.title}",
            color = if (selected || focused) Color.White else Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        episode.runtimeMinutes?.let {
            Text("$it min", color = Grey, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Panel lateral derecho con todas las fuentes, ordenadas por caché RD → HDR/DV → calidad → seeders. */
@Composable
private fun SourcesPanel(
    state: DetailsUiState.Content,
    onPick: (StreamSource) -> Unit,
    isBlocked: (StreamSource) -> Boolean,
    onToggleSpanishOnly: () -> Unit,
) {
    val firstRequester = remember { FocusRequester() }
    val sources = (state.sources as? SourcesState.Ready)?.sources.orEmpty()
    val order = SourceProvider.entries.map { it.section }.distinct()
    val pending = (state.sources as? SourcesState.Ready)?.pending.orEmpty()
    val unavailable = (state.sources as? SourcesState.Ready)?.unavailable.orEmpty() +
        pending.associateWith { tr("Buscando…", "Searching…") }
    // Buscadores con fuentes + los que no han dado nada (lista vacía → cabecera con el motivo), en orden fijo.
    val spanishFirst = state.spanishFirst
    val spanishOnly = state.spanishOnly
    val groups = remember(sources, unavailable, spanishFirst, spanishOnly) {
        val byProvider = sources.groupBy { it.provider }
        // Dentro de cada sección: (con "Castellano primero") las de audio en español; luego la mejor calidad y, a
        // igual calidad, la más grande.
        (byProvider.keys + unavailable.keys).distinct().sortedBy { order.indexOf(it) }.map { section ->
            section to byProvider[section].orEmpty()
                // "Solo en español": castellano o "Spanish"/"Dual" (puntuación ≥ 2); fuera latino dudoso y VOSE.
                .filter { !spanishOnly || it.advertisesSpanishAudio }
                .sortedWith(
                compareByDescending<StreamSource> { if (spanishFirst) it.spanishAudioScore else 0 }
                    .thenByDescending { it.quality.rank }
                    .thenByDescending { it.sizeBytes },
            )
        }
    }
    // Lo que el usuario abre/cierra se mantiene aunque sigan llegando buscadores; solo se abre solo el primero con fuentes.
    var open by remember { mutableStateOf(emptySet<String>()) }
    var autoOpened by remember { mutableStateOf(false) }
    LaunchedEffect(groups) {
        if (!autoOpened) groups.firstOrNull { it.second.isNotEmpty() }?.let { open = setOf(it.first); autoOpened = true }
    }
    LaunchedEffect(sources.isNotEmpty()) {
        if (sources.isNotEmpty()) {
            withFrameNanos { }
            runCatching { firstRequester.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(520.dp)
                .background(Color(0xF2141414))
                .padding(top = 32.dp, start = 24.dp, end = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tr("Elegir fuente", "Choose a source"),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                )
                // ▲ desde la primera sección. Se recuerda para las siguientes fichas.
                SpanishOnlyChip(enabled = spanishOnly, onClick = onToggleSpanishOnly)
            }
            state.selectedEpisode?.takeIf { state.isSeries }?.let {
                Text("${it.code} · ${it.title}", color = Grey, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(16.dp))
            when (val s = state.sources) {
                SourcesState.Idle -> Text(tr("Elige un episodio primero", "Pick an episode first"), color = Grey)
                SourcesState.Loading -> Text(tr("Buscando fuentes…", "Finding sources…"), color = Grey)
                is SourcesState.Error -> Text(s.message, color = Grey)
                is SourcesState.Ready -> if (groups.all { it.second.isEmpty() } && s.pending.isEmpty()) {
                    Text(
                        if (spanishOnly) tr("No hay fuentes en español para este perfil", "No Spanish sources available for this profile")
                        else tr("No hay fuentes", "No sources"),
                        color = Grey,
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 32.dp),
                    ) {
                        // Una sección por buscador (Torrentio, KnightCrawler…); OK en la cabecera la abre o la cierra.
                        groups.forEachIndexed { index, (name, list) ->
                            item(key = "h:$name") {
                                ProviderHeader(
                                    name = name,
                                    count = list.size,
                                    cached = list.count { it.isCached },
                                    expanded = name in open,
                                    note = unavailable[name].takeIf { list.isEmpty() }
                                        ?: tr("Ninguna en español", "None in Spanish").takeIf { list.isEmpty() && spanishOnly },
                                    onClick = { if (list.isNotEmpty()) open = if (name in open) open - name else open + name },
                                    modifier = if (index == 0) Modifier.focusRequester(firstRequester) else Modifier,
                                )
                            }
                            if (name in open) {
                                items(list, key = { it.id }) { source -> SourceRow(source = source, blocked = isBlocked(source), onClick = { onPick(source) }) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Píldora "Solo en español" del panel de Fuentes (marcada = filtro puesto). */
@Composable
private fun SpanishOnlyChip(enabled: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text = if (enabled) "✓ " + tr("Solo en español", "Spanish only") else tr("Solo en español", "Spanish only"),
        color = if (focused) Color.Black else Color.White,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier
            .background(
                when {
                    focused -> Color.White
                    enabled -> Color.White.copy(alpha = 0.22f)
                    else -> Color.White.copy(alpha = 0.08f)
                },
                RoundedCornerShape(50),
            )
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun ProviderHeader(name: String, count: Int, cached: Int, expanded: Boolean, note: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val color = if (focused) Color.Black else Color.White
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(name, color = color, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
        Text(
            note ?: buildString {
                append(tr("$count fuentes", "$count sources"))
                if (cached > 0) append(" · $cached ⚡")
            },
            color = if (focused) Color.Black.copy(alpha = 0.7f) else Grey,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (note == null) Text(if (expanded) "   ▾" else "   ›", color = color, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SourceRow(source: StreamSource, blocked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.06f))
            .tvClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Bloqueada por Real-Debrid (ya se intentó): apagada y con el motivo, para no volver a elegirla.
        val fg = when {
            focused -> Color.Black
            blocked -> Color.White.copy(alpha = 0.4f)
            else -> Color.White
        }
        val sub = if (focused) Color.Black.copy(alpha = 0.7f) else Grey.copy(alpha = if (blocked) 0.5f else 1f)
        Box(Modifier.width(26.dp)) {
            if (source.isCached) Icon(AppIcons.Bolt, contentDescription = tr("En tu Real-Debrid", "In your Real-Debrid"), tint = if (focused) Color(0xFF0A8F3C) else Green)
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = buildString {
                    append(source.quality.label)
                    if (source.hdrType != HdrType.SDR) append(" · ").append(source.hdrType.label)
                    if (source.sizeBytes > 0) append(" · ").append(formatSize(source.sizeBytes))
                    source.seeders?.let { append(" · ").append(it).append(" seeds") }
                    if (source.languages.isNotEmpty()) append(" · ").append(source.languages.joinToString("/"))
                    if (source.softwareDecode) append(" · AV1 (CPU)")
                    if (blocked) append(tr(" · Bloqueada por Real-Debrid", " · Blocked by Real-Debrid"))
                },
                color = fg,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = source.title,
                color = sub,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Barra de visionado estilo Netflix: gris con el tramo visto en rojo. */
@Composable
private fun WatchBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.3f)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(Color(0xFFE50914)),
        )
    }
}

private fun formatRuntime(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h h"
        else -> "$h h $m min"
    }
}

private fun formatSize(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 1) String.format(Locale.US, "%.1f GB", gb) else String.format(Locale.US, "%.0f MB", gb * 1024)
}
