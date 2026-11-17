package com.shieldstream.tv.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.strings.R as StringsR

/** Abre el tráiler en la app de YouTube que tenga el sistema (SmartTube incluido) con un enlace normal. */
fun openTrailerExternally(context: Context, key: String) {
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$key")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(web)
    } catch (_: ActivityNotFoundException) {
        Log.w(TAG, "No hay ninguna app que abra YouTube")
    }
}

/**
 * Tráiler dentro de Bambi con el reproductor oficial de YouTube incrustado (el de cualquier web), a pantalla
 * completa. Va en una pantalla propia sin Compose: dentro de la interfaz de Compose, en la Shield, la vista web
 * sonaba pero el vídeo salía negro.
 *
 * Mando: OK pausa/reanuda, izquierda/derecha retrocede/avanza 10 s, Atrás vuelve a la ficha. Si un tráiler no deja
 * verse incrustado (decisión de quien lo sube: errores 101/150), se prueba el siguiente de TMDB; solo si no va
 * ninguno se abre fuera.
 */
class TrailerActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var loading: android.view.View
    private val main = Handler(Looper.getMainLooper())
    private var keys: List<String> = emptyList()
    private var index = 0
    private var ready = false
    private val timeout = Runnable { if (!ready) next("timeout") }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keys = intent.getStringArrayExtra(EXTRA_KEYS)?.toList().orEmpty().filter { it.isNotBlank() }
        if (keys.isEmpty()) {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        web = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            isFocusable = false
            isFocusableInTouchMode = false
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                    Log.i(TAG, "web: ${message.message()} (${message.sourceId()?.takeLast(40)}:${message.lineNumber()})")
                    return true
                }
            }
            webViewClient = WebViewClient()
            addJavascriptInterface(Bridge(), "Bambi")
        }
        loading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(
                ProgressBar(this@TrailerActivity),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
            addView(
                TextView(this@TrailerActivity).apply {
                    text = getString(StringsR.string.trailer_loading)
                    setTextColor(Color.argb(180, 255, 255, 255))
                    textSize = 16f
                    gravity = Gravity.CENTER
                    setPadding(0, 24, 0, 0)
                },
                // En un LinearLayout vertical el texto ocupa todo el ancho por defecto: centrado de verdad.
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
        setContentView(
            FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)
                addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(loading, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            },
        )
        load()
    }

    private fun load() {
        ready = false
        loading.visibility = android.view.View.VISIBLE
        main.removeCallbacks(timeout)
        main.postDelayed(timeout, LOAD_TIMEOUT_MS)
        // Con un origen propio YouTube acepta el reproductor incrustado (sin él da "error de configuración").
        web.loadDataWithBaseURL(ORIGIN, html(keys[index]), "text/html", "utf-8", null)
    }

    private fun next(reason: String) {
        Log.w(TAG, "Tráiler ${keys.getOrNull(index)} no se puede ver aquí ($reason)")
        if (isFinishing) return
        index++
        if (index < keys.size) {
            load()
        } else {
            openTrailerExternally(this, keys.first())
            finish()
        }
    }

    private fun js(code: String) = web.evaluateJavascript(code, null)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            return if (event.keyCode == KeyEvent.KEYCODE_BACK) super.dispatchKeyEvent(event) else true
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> js("toggle()")
            KeyEvent.KEYCODE_MEDIA_PLAY -> js("player && player.playVideo()")
            KeyEvent.KEYCODE_MEDIA_PAUSE -> js("player && player.pauseVideo()")
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> js("seek(-10)")
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> js("seek(10)")
            KeyEvent.KEYCODE_BACK -> return super.dispatchKeyEvent(event)
            else -> Unit
        }
        return true
    }

    override fun onPause() {
        super.onPause()
        if (::web.isInitialized) js("player && player.pauseVideo()")
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        if (::web.isInitialized) {
            web.loadUrl("about:blank")
            web.destroy()
        }
        super.onDestroy()
    }

    private inner class Bridge {
        @JavascriptInterface
        fun ready() {
            main.post {
                ready = true
                main.removeCallbacks(timeout)
                loading.visibility = android.view.View.GONE
            }
        }

        @JavascriptInterface
        fun state(value: String) {
            Log.i(TAG, "Tráiler ${keys.getOrNull(index)}: estado $value")
        }

        @JavascriptInterface
        fun ended() {
            main.post { finish() }
        }

        @JavascriptInterface
        fun error(code: String) {
            main.post { next("error $code") }
        }
    }

    private fun html(key: String): String {
        val safeKey = key.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        val lang = if (Lang.isEnglish) "en" else "es"
        return """
<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{position:absolute;top:0;left:0;width:100%;height:100%;border:0}</style>
</head><body><div id="p"></div>
<script src="https://www.youtube.com/iframe_api"></script>
<script>
var player;
function onYouTubeIframeAPIReady(){
  player = new YT.Player('p', {
    videoId: '$safeKey',
    playerVars: {autoplay:1, controls:1, rel:0, modestbranding:1, playsinline:1, fs:0, iv_load_policy:3,
                 hl:'$lang', cc_lang_pref:'$lang', origin:'$ORIGIN'},
    events: {
      onReady: function(e){ Bambi.state('listo'); e.target.playVideo(); },
      onStateChange: function(e){ Bambi.state(String(e.data)); if (e.data === 1) Bambi.ready(); if (e.data === 0) Bambi.ended(); },
      onError: function(e){ Bambi.error(String(e.data)); }
    }
  });
}
function toggle(){ if(!player) return; if (player.getPlayerState() === 1) player.pauseVideo(); else player.playVideo(); }
function seek(d){ if(!player) return; player.seekTo(Math.max(0, player.getCurrentTime() + d), true); }
</script></body></html>
""".trimIndent()
    }

    companion object {
        private const val EXTRA_KEYS = "keys"

        fun start(context: Context, keys: List<String>) {
            context.startActivity(Intent(context, TrailerActivity::class.java).putExtra(EXTRA_KEYS, keys.toTypedArray()))
        }
    }
}

private const val TAG = "Trailer"
private const val ORIGIN = "https://bambi.tv"
private const val LOAD_TIMEOUT_MS = 20_000L
