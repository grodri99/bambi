package com.shieldstream.tv

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.shieldstream.tv.domain.IthilUpdateRepository
import com.shieldstream.tv.intro.IntroKeyGate
import com.shieldstream.tv.intro.ResumeIntro
import com.shieldstream.tv.domain.SelectedContentHolder
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.domain.TraktRepository
import com.shieldstream.tv.domain.UpdateRepository
import com.shieldstream.tv.ui.theme.LocalLightweightUi
import com.shieldstream.tv.ui.theme.LocalSoftBackdrops
import com.shieldstream.tv.ui.theme.LocalHighResBackdrops
import com.shieldstream.tv.ui.theme.ShieldStreamTheme
import com.shieldstream.tv.watchnext.WatchNextManager
import com.shieldstream.tv.watchnext.WatchNextRequest
import com.shieldstream.tv.watchnext.parseWatchNextRequest
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val watchNextRequest = MutableStateFlow<WatchNextRequest?>(null)

    /** Intro al volver a la app desde segundo plano (no en el arranque, que ya empieza con ella). */
    private val resumeIntro = MutableStateFlow<ResumeIntro?>(null)
    private var stoppedAt = 0L

    @Inject
    lateinit var holder: SelectedContentHolder

    @Inject
    lateinit var trakt: TraktRepository

    @Inject
    lateinit var updates: UpdateRepository

    @Inject
    lateinit var ithilUpdates: IthilUpdateRepository

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var watchNext: WatchNextManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            watchNextRequest.value = parseWatchNextRequest(intent)
        }
        setContent {
            val lightweightUi by settings.lightweightUi.collectAsState(initial = settings.lightweightUiEnabled)
            val softBackdrops by settings.softBackdrops.collectAsState(initial = settings.softBackdropsEnabled)
            val highResBackdrops by settings.highResBackdrops.collectAsState(initial = settings.highResBackdropsEnabled)
            CompositionLocalProvider(
                LocalLightweightUi provides lightweightUi,
                LocalSoftBackdrops provides softBackdrops,
                LocalHighResBackdrops provides highResBackdrops,
            ) {
                ShieldStreamTheme {
                    AppNavHost(
                        holder = holder,
                        onExit = { finish() },
                        watchNextRequest = watchNextRequest,
                        resumeIntro = resumeIntro,
                        onResumeIntroDone = { done -> if (resumeIntro.value == done) resumeIntro.value = null },
                        onWatchNextHandled = { handled ->
                            if (watchNextRequest.value?.requestedAtNs == handled.requestedAtNs) {
                                watchNextRequest.value = null
                            }
                        },
                    )
                }
            }
        }
    }

    // Durante la intro las teclas del mando son para ella (OK/Atrás la saltan) y no para la pantalla de debajo.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val gate = IntroKeyGate.onKey ?: return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_UP) gate()
        return true
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    private companion object {
        /** Lo que dura fresco el Inicio guardado: pasado esto, al volver se recarga. */
        const val RELOAD_AFTER_MS = 30 * 60_000L
        const val ALWAYS_MIN_AWAY_MS = 3_000L
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        watchNextRequest.value = parseWatchNextRequest(intent)
    }

    override fun onStart() {
        super.onStart()
        if (stoppedAt > 0L) {
            val away = SystemClock.elapsedRealtime() - stoppedAt
            // Tras mucho rato fuera el Inicio está caducado: la intro sale siempre y lo recarga con sus carátulas.
            // Con "Intro siempre" sale además cada vez que se vuelve (salvo vueltas de un instante).
            val reload = away >= RELOAD_AFTER_MS
            if (reload || (settings.introAlwaysEnabled && away >= ALWAYS_MIN_AWAY_MS)) {
                resumeIntro.value = ResumeIntro(reload = reload, at = SystemClock.elapsedRealtime())
            }
        }
        watchNext.onForeground()
        trakt.syncActive()
        updates.check(manual = false)
        lifecycleScope.launch { ithilUpdates.checkForUpdates() }
    }
}
