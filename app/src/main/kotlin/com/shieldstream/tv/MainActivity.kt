package com.shieldstream.tv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.shieldstream.tv.domain.IthilUpdateRepository
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        watchNextRequest.value = parseWatchNextRequest(intent)
    }

    override fun onStart() {
        super.onStart()
        watchNext.onForeground()
        trakt.syncActive()
        updates.check(manual = false)
        lifecycleScope.launch { ithilUpdates.checkForUpdates() }
    }
}
