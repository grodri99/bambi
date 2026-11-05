package com.shieldstream.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.shieldstream.tv.domain.SelectedContentHolder
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.domain.TraktRepository
import com.shieldstream.tv.domain.UpdateRepository
import com.shieldstream.tv.ui.theme.LocalLightweightUi
import com.shieldstream.tv.ui.theme.FranklixTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var holder: SelectedContentHolder

    @Inject
    lateinit var trakt: TraktRepository

    @Inject
    lateinit var updates: UpdateRepository

    @Inject
    lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val lightweightUi by settings.lightweightUi.collectAsState(initial = settings.lightweightUiEnabled)
            CompositionLocalProvider(LocalLightweightUi provides lightweightUi) {
                FranklixTheme {
                    AppNavHost(holder = holder, onExit = { finish() })
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        trakt.syncActive()
        updates.check(manual = false)
    }
}
