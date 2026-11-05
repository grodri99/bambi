package com.shieldstream.tv.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextOverflow
import com.shieldstream.tv.model.tr
import com.shieldstream.tv.model.PlaybackVideoInfo
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.shieldstream.tv.ui.component.AppIcons
import com.shieldstream.tv.ui.component.PlaybackMediaBadges
import com.shieldstream.tv.ui.component.onBackKey
import kotlinx.coroutines.delay

private val NetflixRed = Color(0xFFE50914)

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val player by viewModel.player.collectAsStateWithLifecycle()
    val videoInfo by viewModel.videoInfo.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val externalSubs by viewModel.externalSubs.collectAsStateWithLifecycle()
    val activeExternal by viewModel.activeExternal.collectAsStateWithLifecycle()
    val subtitleTiming by viewModel.subtitleTiming.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val thumbnailsEnabled by viewModel.seekThumbnails.collectAsStateWithLifecycle()
    val nextUp by viewModel.nextUp.collectAsStateWithLifecycle()
    val skipPrompt by viewModel.skipPrompt.collectAsStateWithLifecycle()
    // "Siguiente episodio": se sale a la ficha, que busca las fuentes del siguiente y lo pone.
    LaunchedEffect(Unit) { viewModel.goNext.collect { onBack() } }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(3_500)
            viewModel.clearNotice()
        }
    }

    LaunchedEffect(Unit) { viewModel.start() }
    BackHandler(onBack = onBack)

    // Panel Audio/Subtítulos. Registrado después: Atrás lo cierra antes de salir del reproductor.
    var tracksOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = tracksOpen) { tracksOpen = false }
    // Barra "Sincronizar subtítulos" (solo con subtítulo externo).
    var syncOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = syncOpen) { syncOpen = false }
    LaunchedEffect(activeExternal) { if (activeExternal == null) syncOpen = false }

    // Subtítulo externo cargado en el vídeo → se cierra el panel y se vuelve a la película.
    LaunchedEffect(externalSubs) {
        if (externalSubs is ExternalSubsState.Attached) {
            tracksOpen = false
            viewModel.resetExternalSubtitles()
        }
    }

    // Ciclo de vida del player: liberar en ON_STOP (Home / salvapantallas) y recrear en ON_START.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onStart()
                Lifecycle.Event.ON_STOP -> viewModel.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val playback = rememberPlaybackState(player)
    var controlsVisible by remember { mutableStateOf(true) }
    var interactions by remember { mutableIntStateOf(0) }
    // Modo "saltos de 10 s" (ajuste sin miniaturas): aviso "« 10 s" en el centro.
    var seekFeedback by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(900)
            seekFeedback = null
        }
    }

    // Modo búsqueda (◀▶): el vídeo se pausa y se elige el punto con miniaturas; OK salta ahí, Atrás vuelve.
    // null = no se está buscando.
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    var resumeAfterScrub by remember { mutableStateOf(false) }
    val scrubbing = scrubMs != null
    // Dolby Vision perfil 5 (sin capa HDR10/SDR): la capa base sale con colores falsos → sin miniaturas, solo tiempos.
    val mediaUri = remember(player, scrubbing) {
        player?.currentMediaItem?.localConfiguration?.uri
            ?.takeUnless { player?.videoFormat?.codecs?.let { c -> c.startsWith("dvhe.05") || c.startsWith("dvh1.05") } == true }
    }
    // HDR10 / Dolby Vision (PQ): las miniaturas se pasan a SDR con una LUT (ver SeekThumbnails).
    val pqHdr = remember(player, scrubbing) {
        player?.videoFormat?.let {
            it.colorInfo?.colorTransfer == C.COLOR_TRANSFER_ST2084 || it.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION
        } ?: false
    }
    val thumbs = rememberSeekThumbnails(mediaUri, pqHdr, active = scrubbing)
    // Si el reproductor se recrea (vuelta de Home / salvapantallas) se sale del modo búsqueda.
    LaunchedEffect(player) { scrubMs = null }

    fun showControls() {
        controlsVisible = true
        interactions++
    }

    // Auto-ocultar a los 4 s si se está reproduciendo (en pausa se quedan visibles).
    LaunchedEffect(controlsVisible, interactions, playback.isPlaying) {
        if (controlsVisible && playback.isPlaying) {
            delay(4_000)
            controlsVisible = false
        }
    }

    // El foco del D-pad vive en Compose (no en el PlayerView): aquí se traducen las teclas del mando.
    val focusRequester = remember { FocusRequester() }
    // Al cerrar el panel de pistas el foco vuelve al reproductor (si no, el D-pad no haría nada).
    LaunchedEffect(tracksOpen) { if (!tracksOpen) runCatching { focusRequester.requestFocus() } }

    // "Omitir intro/resumen": 10 s a la vista (como en Nuvio) y luego solo con los controles abiertos. Mientras no se
    // ve, OK vuelve a ser pausa.
    val skipTarget = (skipPrompt as? SkipPromptUiState.Showing)?.targetMs
    var skipExpired by remember { mutableStateOf(false) }
    LaunchedEffect(skipTarget) {
        skipExpired = false
        if (skipTarget != null) {
            delay(SKIP_VISIBLE_MS)
            skipExpired = true
        }
    }
    val skipVisible = skipPrompt is SkipPromptUiState.Showing && (!skipExpired || controlsVisible) &&
        nextUp !is NextUpUiState.Showing

    fun handleKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        // Con el panel abierto, el D-pad mueve el foco entre pistas: no se intercepta.
        if (tracksOpen) return false
        val p = player ?: return false
        // Tarjeta "Siguiente episodio": OK = ya (Atrás la quita: ver onBackKey abajo).
        val okKey = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
        if (okKey && nextUp is NextUpUiState.Showing && scrubMs == null && !syncOpen) {
            viewModel.playNextNow()
            return true
        }
        // "Saltar intro / resumen" a la vista: OK salta (como en Netflix); para pausar, Play/Pausa del mando.
        if (okKey && skipVisible && scrubMs == null && !syncOpen) {
            viewModel.skipSegment()
            return true
        }
        if (syncOpen) {
            return when (event.key) {
                Key.DirectionLeft -> { viewModel.adjustSubtitleOffset(-100); true }
                Key.DirectionRight -> { viewModel.adjustSubtitleOffset(100); true }
                Key.DirectionDown -> { viewModel.adjustSubtitleOffset(-1_000); true }
                Key.DirectionUp -> { viewModel.adjustSubtitleOffset(1_000); true }
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { viewModel.autoSyncNow(); true }
                Key.MediaPlayPause, Key.Spacebar -> { if (p.isPlaying) p.pause() else p.play(); true }
                else -> false
            }
        }
        // ◀▶ mueven el punto elegido (no el vídeo): al primer toque se pausa y se entra en modo búsqueda.
        fun scrub(deltaMs: Long) {
            val duration = p.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
            val from = scrubMs ?: run {
                resumeAfterScrub = p.isPlaying
                p.pause()
                p.currentPosition
            }
            // Redondeado a la rejilla de miniaturas (10 s): la hora del centro coincide con su fotograma.
            val snapped = Math.round((from + deltaMs).toDouble() / THUMB_SLOT_MS) * THUMB_SLOT_MS
            scrubMs = snapped.coerceIn(0L, duration)
            showControls()
        }
        val target = scrubMs
        if (target != null) {
            when (event.key) {
                // OK / Play: saltar al punto elegido y seguir viendo.
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause, Key.Spacebar, Key.MediaPlay -> {
                    p.seekTo(target)
                    p.play()
                    scrubMs = null
                    showControls()
                    return true
                }
                Key.DirectionUp, Key.DirectionDown, Key.MediaPause -> return true
                else -> Unit
            }
        }
        return when (event.key) {
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause, Key.Spacebar -> {
                if (p.isPlaying) p.pause() else p.play()
                showControls()
                true
            }
            Key.MediaPlay -> { p.play(); showControls(); true }
            Key.MediaPause -> { p.pause(); showControls(); true }
            Key.DirectionLeft, Key.MediaRewind -> {
                val step = seekStepMs(event.nativeKeyEvent.repeatCount)
                if (thumbnailsEnabled) {
                    scrub(-step)
                } else {
                    p.seekTo((p.currentPosition - step).coerceAtLeast(0L))
                    seekFeedback = "« ${step / 1000} s"
                    showControls()
                }
                true
            }
            Key.DirectionRight, Key.MediaFastForward -> {
                val step = seekStepMs(event.nativeKeyEvent.repeatCount)
                if (thumbnailsEnabled) {
                    scrub(step)
                } else {
                    val duration = p.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                    p.seekTo((p.currentPosition + step).coerceAtMost(duration))
                    seekFeedback = "${step / 1000} s »"
                    showControls()
                }
                true
            }
            // ▼ con los controles visibles (o Menú / tecla de subtítulos) abre Audio y subtítulos.
            Key.DirectionDown, Key.Menu, Key.Captions -> {
                if (controlsVisible || event.key != Key.DirectionDown) {
                    controlsVisible = false
                    tracksOpen = true
                } else {
                    showControls()
                }
                true
            }
            Key.DirectionUp, Key.Info -> { showControls(); true }
            // Volumen, Atrás, Home… se dejan pasar al sistema / BackHandler.
            else -> false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onBackKey(enabled = scrubMs != null) {
                scrubMs = null
                if (resumeAfterScrub) player?.play()
                showControls()
            }
            .onBackKey(enabled = tracksOpen) { tracksOpen = false }
            .onBackKey(enabled = syncOpen) { syncOpen = false }
            .onBackKey(enabled = nextUp is NextUpUiState.Showing && !tracksOpen && !syncOpen && scrubMs == null) {
                viewModel.dismissNextUp()
            }
            .focusRequester(focusRequester)
            .onKeyEvent(::handleKey)
            .focusable(),
    ) {
        player?.let { exoPlayer ->
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        // SurfaceView por defecto: obligatorio para Dolby Vision (TextureView no lo soporta).
                        useController = false // controles propios en Compose (abajo)
                        keepScreenOn = true // evita que salte el salvapantallas de Android TV
                        isFocusable = false
                    }
                },
                update = { view ->
                    view.player = exoPlayer
                    // La referencia queda oculta tanto con un externo como durante la selección automática.
                    val evaluating = (externalSubs as? ExternalSubsState.Results)?.evaluating == true
                    view.subtitleView?.visibility = if (activeExternal != null || evaluating) View.GONE else View.VISIBLE
                },
                modifier = Modifier.fillMaxSize(),
            )
            activeExternal?.let { ExternalSubtitleOverlay(exoPlayer, it, subtitleTiming) }
        }

        if (state == PlayerUiState.Playing) {
            if (playback.buffering) {
                CircularProgressIndicator(color = NetflixRed, modifier = Modifier.align(Alignment.Center).size(48.dp))
            }
            AnimatedVisibility(visible = controlsVisible && !syncOpen, enter = fadeIn(), exit = fadeOut()) {
                ControlsOverlay(
                    title = title,
                    videoInfo = videoInfo,
                    playback = playback,
                    scrubMs = scrubMs,
                    thumbs = thumbs,
                    thumbnailsEnabled = thumbnailsEnabled,
                )
            }
            seekFeedback?.let {
                Text(
                    text = it,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }

        if (syncOpen && state == PlayerUiState.Playing) {
            SubtitleSyncBar(timing = subtitleTiming, status = syncStatus, modifier = Modifier.align(Alignment.TopCenter))
        }

        if (state == PlayerUiState.Playing && !syncOpen) {
            notice?.let {
                Text(
                    text = it,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }

        if (tracksOpen && state == PlayerUiState.Playing) {
            player?.let {
                TracksPanel(
                    player = it,
                    external = externalSubs,
                    onSearchExternal = { lang -> viewModel.searchExternalSubtitles(lang) },
                    onPickExternal = { sub -> viewModel.downloadExternalSubtitle(sub) },
                    onCloseExternal = { viewModel.resetExternalSubtitles() },
                    externalLabel = activeExternal?.label,
                    syncDetail = syncStatus.describe().ifBlank { subtitleTiming.describe() },
                    onExternalOff = { viewModel.disableExternalSubtitle() },
                    onManualSubtitleSelection = viewModel::onManualSubtitleSelection,
                    onOpenSync = {
                        tracksOpen = false
                        syncOpen = true
                    },
                )
            }
        }

        // Abajo a la derecha, compactos (estilo Netflix): suben un poco si están los controles para no tapar la barra.
        val pillBottom by animateDpAsState(if (controlsVisible) 116.dp else 48.dp, label = "pillBottom")
        val pillModifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = pillBottom)
        if (state == PlayerUiState.Playing && !tracksOpen) {
            (nextUp as? NextUpUiState.Showing)?.let { next ->
                // Relleno = cuenta atrás de los últimos 10 s (al llenarse, pasa solo).
                val target = if (next.secondsLeft <= NEXT_FILL_SECONDS) 1f - next.secondsLeft / NEXT_FILL_SECONDS.toFloat() else 0f
                val fill by animateFloatAsState(target, animationSpec = tween(500, easing = LinearEasing), label = "nextFill")
                PlayerPill(tr("Siguiente episodio", "Next episode"), AppIcons.SkipNext, fill, pillModifier)
            }
            if (skipVisible) {
                (skipPrompt as? SkipPromptUiState.Showing)?.let { skip ->
                    PlayerPill(skip.label, AppIcons.FastForward, null, pillModifier)
                }
            }
        }

        when (val s = state) {
            is PlayerUiState.Resolving -> StatusOverlay(title = title, message = s.message, loading = true)
            is PlayerUiState.Error -> StatusOverlay(title = title, message = s.message, loading = false)
            PlayerUiState.Playing -> Unit
        }
    }
}

/** Cuántos segundos del final se ven como relleno en "Siguiente episodio". */
private const val NEXT_FILL_SECONDS = 10
/** Tiempo que "Omitir intro" se ve sin abrir los controles. */
private const val SKIP_VISIBLE_MS = 10_000L

/**
 * Botón compacto del reproductor ("Omitir intro", "Siguiente episodio"): píldora oscura con borde blanco.
 * [fill] (0..1) pinta por detrás el avance de la cuenta atrás. Se activa con OK (ver handleKey).
 */
@Composable
private fun PlayerPill(text: String, icon: ImageVector, fill: Float?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.62f))
            .drawBehind {
                if (fill != null && fill > 0f) drawRect(Color.White.copy(alpha = 0.28f), size = size.copy(width = size.width * fill))
            }
            .border(1.5.dp, Color.White.copy(alpha = 0.9f), shape)
            .padding(start = 14.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
    }
}

/** Mantener pulsado acelera el salto: 10 s → 30 s → 60 s. */
private fun seekStepMs(repeatCount: Int): Long = when {
    repeatCount > 20 -> 60_000L
    repeatCount > 5 -> 30_000L
    else -> 10_000L
}

@Stable
private class PlaybackState {
    var isPlaying by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var bufferedMs by mutableLongStateOf(0L)
}

@Composable
private fun rememberPlaybackState(player: ExoPlayer?): PlaybackState {
    val state = remember { PlaybackState() }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                state.isPlaying = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                state.buffering = playbackState == Player.STATE_BUFFERING
            }
        }
        player?.addListener(listener)
        state.isPlaying = player?.isPlaying == true
        onDispose { player?.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (true) {
            player?.let {
                state.positionMs = it.currentPosition
                state.durationMs = it.duration.coerceAtLeast(0L)
                state.bufferedMs = it.bufferedPosition
            }
            delay(500)
        }
    }
    return state
}

@Composable
private fun ControlsOverlay(
    title: String,
    videoInfo: PlaybackVideoInfo?,
    playback: PlaybackState,
    scrubMs: Long?,
    thumbs: SeekThumbnails?,
    thumbnailsEnabled: Boolean,
) {
    val shownMs = scrubMs ?: playback.positionMs
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent))),
        )
        Column(
            Modifier.align(Alignment.TopStart).padding(start = 48.dp, top = 28.dp, end = 48.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            videoInfo?.let {
                Spacer(Modifier.height(8.dp))
                PlaybackMediaBadges(it)
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                .padding(start = 48.dp, end = 48.dp, top = 60.dp, bottom = 32.dp),
        ) {
            if (scrubMs != null) {
                SeekThumbnailStrip(thumbs = thumbs, targetMs = scrubMs, durationMs = playback.durationMs)
                Spacer(Modifier.height(18.dp))
            }
            SeekBar(playback, shownMs)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (playback.isPlaying) AppIcons.Pause else AppIcons.Play,
                    contentDescription = if (playback.isPlaying) tr("Pausa", "Pause") else tr("Reproducir", "Play"),
                    tint = Color.White,
                    modifier = Modifier.size(34.dp),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = formatTime(shownMs),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = when {
                        scrubMs != null -> tr("OK ir aquí · Atrás volver", "OK jump here · Back return")
                        thumbnailsEnabled -> tr("OK pausa · ◀ ▶ buscar · ▼ Audio y subtítulos", "OK pause · ◀ ▶ seek · ▼ Audio & subtitles")
                        else -> tr("OK pausa · ◀ ▶ 10 s · ▼ Audio y subtítulos", "OK pause · ◀ ▶ 10 s · ▼ Audio & subtitles")
                    },
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.width(24.dp))
                Text(
                    text = if (playback.durationMs > 0) "-" + formatTime(playback.durationMs - shownMs) else "",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun SeekBar(playback: PlaybackState, positionMs: Long) {
    val duration = playback.durationMs.coerceAtLeast(1L)
    val played = (positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val buffered = (playback.bufferedMs.toFloat() / duration).coerceIn(0f, 1f)
    BoxWithConstraints(Modifier.fillMaxWidth().height(14.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.25f)))
        Box(Modifier.fillMaxWidth(buffered).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.45f)))
        Box(Modifier.fillMaxWidth(played).height(4.dp).clip(RoundedCornerShape(2.dp)).background(NetflixRed))
        Box(
            Modifier
                .offset(x = maxWidth * played - 7.dp)
                .size(14.dp)
                .clip(CircleShape)
                .background(NetflixRed),
        )
    }
}


@Composable
private fun StatusOverlay(title: String, message: String, loading: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (title.isNotBlank()) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
        }
        if (loading) {
            CircularProgressIndicator(color = NetflixRed, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(16.dp))
        }
        Text(
            text = message,
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 640.dp),
        )
        if (!loading) {
            Spacer(Modifier.height(16.dp))
            Text(tr("Pulsa Atrás para elegir otra fuente", "Press Back to pick another source"), color = Color(0xFFB3B3B3), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

