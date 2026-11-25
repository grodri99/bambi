package com.shieldstream.tv.home

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Build
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView

/** Relación de aspecto del reproductor de YouTube. */
private const val YouTubeAspectRatio = 16f / 9f

/** Fundido de entrada del vídeo sobre el arte estático. */
private const val TrailerFadeMs = 700

/** Sondeo del <video> del reproductor: silencio forzado y aviso cuando ya corre. */
internal val PlaybackProbe =
    "(function(){var v=document.querySelector('video');if(!v)return 'no';" +
        "v.muted=true;v.volume=0;if(v.paused){v.play();}" +
        "return (v.paused||v.ended)?'no':'yes';})()"

/** 20 intentos × 400 ms ≈ 8 s para que YouTube cree el <video> y arranque. */
internal const val ProbeAttempts = 20
internal const val ProbeIntervalMs = 400L

/**
 * Tráiler mudo de YouTube encima del arte de la portada, recortado como `ContentScale.Crop`.
 *
 * El WebView se crea solo cuando toca y se destruye al perder el foco: montarlo cuesta caro en la
 * Nokia y no debe quedar vivo mientras se navegan las filas. El fundido de entrada espera a que el
 * vídeo esté corriendo de verdad, para no enseñar un rectángulo negro mientras carga.
 */
@Composable
internal fun TrailerPreview(
    videoKey: String,
    modifier: Modifier = Modifier,
) {
    if (!remember { isWebViewAvailable() }) return
    val context = LocalContext.current
    var playing by remember { mutableStateOf(false) }
    // Si el proceso de render de WebView muere (cajas con poca memoria), se retira y queda el arte.
    var crashed by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (playing) 1f else 0f, tween(TrailerFadeMs), label = "trailerAlpha")

    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val (width, height) = coverSize(maxWidth, maxHeight)
        if (!crashed) {
            AndroidView(
                modifier = Modifier
                    .size(width, height)
                    .align(Alignment.Center)
                    // El WebView no recorta solo: sin esto se sale de la portada en la Shield.
                    .graphicsLayer {
                        this.alpha = alpha
                        clip = true
                    },
                factory = { ctx ->
                    createTrailerView(
                        context = ctx,
                        videoKey = videoKey,
                        onPlaying = { playing = true },
                        onGone = { crashed = true },
                    )
                },
                onRelease = { view -> releaseTrailer(view) },
            )
        }
    }
}

/** Caja 16:9 que cubre el hueco disponible, recortando lo que sobra (como `ContentScale.Crop`). */
internal fun coverSize(width: Dp, height: Dp): Pair<Dp, Dp> =
    if (width / height >= YouTubeAspectRatio) {
        width to width / YouTubeAspectRatio
    } else {
        height * YouTubeAspectRatio to height
    }

/**
 * Contenedor con el WebView dentro: el `FrameLayout` mantiene el recorte de los hijos y permite
 * destruir el reproductor en un solo sitio.
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
internal fun createTrailerView(
    context: Context,
    videoKey: String,
    onPlaying: () -> Unit,
    onGone: () -> Unit,
): View {
    val client = TrailerClient(onPlaying, onGone)
    val container = TrailerContainer(context, client)
    val web = runCatching {
        WebView(context).apply {
            setBackgroundColor(AndroidColor.BLACK)
            isFocusable = false
            isFocusableInTouchMode = false
            isClickable = false
            isLongClickable = false
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                loadWithOverviewMode = true
                useWideViewPort = true
            }
            webViewClient = client
            // En TV no hay dedos: cualquier toque sobra y podría abrir la web de YouTube.
            setOnTouchListener { _, _ -> true }
            loadUrl(embedUrl(videoKey))
        }
    }.getOrNull()
    if (web != null) {
        container.addView(
            web,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }
    return container
}

internal fun releaseTrailer(view: View) {
    // El cliente se guarda en el contenedor: `WebView.webViewClient` solo existe desde API 26.
    val container = view as? TrailerContainer ?: return
    container.client.released = true
    val web = container.getChildAt(0) as? WebView ?: return
    container.removeView(web)
    runCatching {
        web.stopLoading()
        web.loadUrl("about:blank")
        web.onPause()
        web.destroy()
    }
}

/** Contenedor que recorta a sus hijos y guarda el cliente para poder parar el sondeo al destruirlo. */
internal class TrailerContainer(context: Context, val client: TrailerClient) : FrameLayout(context) {
    init {
        clipChildren = true
    }
}

/**
 * Cuando la página termina de cargar, fuerza silencio (`mute=1` ya viene en la URL, pero un `volume=0`
 * por JS no cuesta nada) y avisa a Compose solo cuando el vídeo está corriendo de verdad.
 */
internal class TrailerClient(
    private val onPlaying: () -> Unit,
    private val onGone: () -> Unit,
) : WebViewClient() {

    @Volatile
    var released = false

    override fun onPageFinished(view: WebView?, url: String?) {
        if (view != null) probe(view, 0)
    }

    /** El render de WebView murió: se cierra sin tumbar la app y la portada vuelve al arte estático. */
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        released = true
        onGone()
        return true
    }

    private fun probe(web: WebView, attempt: Int) {
        if (released || attempt >= ProbeAttempts) return
        runCatching {
            web.evaluateJavascript(PlaybackProbe) { result ->
                when {
                    released -> Unit
                    result?.contains("yes") == true -> onPlaying()
                    else -> web.postDelayed({ probe(web, attempt + 1) }, ProbeIntervalMs)
                }
            }
        }
    }
}

/**
 * `autoplay` + `mute` para que Chrome deje arrancar sin gesto; sin controles ni tarjetas.
 * La clave se limpia igual que en el reproductor de tráileres del creador: solo letras, números, `-` y `_`.
 */
internal fun embedUrl(videoKey: String) = buildString {
    val safeKey = videoKey.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
    append("https://www.youtube-nocookie.com/embed/")
    append(safeKey)
    append("?autoplay=1&mute=1&controls=0&modestbranding=1&rel=0&iv_load_policy=3")
    append("&playsinline=1&loop=1&playlist=")
    append(safeKey)
    append("&disablekb=1&fs=0&cc_load_policy=0")
}

/** Algunos TV boxes vienen sin WebView: sin él la portada se queda con el arte estático. */
internal fun isWebViewAvailable(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
    return runCatching { WebView.getCurrentWebViewPackage() }.getOrNull() != null
}
