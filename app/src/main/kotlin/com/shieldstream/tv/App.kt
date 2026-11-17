package com.shieldstream.tv

import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.model.AppLanguage
import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.shieldstream.tv.data.security.TokenStore
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.domain.EpisodeTrackingRepository
import com.shieldstream.tv.watchnext.WatchNextManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), ImageLoaderFactory, Configuration.Provider {

    // Perezoso: Coil no hace falta hasta la primera imagen y así no retrasa el arranque.
    @Inject
    lateinit var imageLoader: dagger.Lazy<ImageLoader>

    @Inject
    lateinit var tokenStore: TokenStore

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var episodeTracking: EpisodeTrackingRepository

    @Inject
    lateinit var watchNext: WatchNextManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        StartupTrace.mark("App.onCreate")
        Lang.current = AppLanguage.fromName(
            getSharedPreferences(AppLanguage.PREFS, MODE_PRIVATE).getString(AppLanguage.PREF_KEY, null),
        ) ?: AppLanguage.default()
        super.onCreate()
        StartupTrace.mark("App inyectada")
        tokenStore.seedOnce(TokenStore.RD_TOKEN, BuildConfig.RD_TOKEN)
        tokenStore.seedOnce(TokenStore.TMDB_KEY, BuildConfig.TMDB_API_KEY)
        tokenStore.seedOnce(TokenStore.SUBDL_KEY, BuildConfig.SUBDL_API_KEY)
        // Solo en Bambi Pruebas: si no hay ningún addon, se deja preconfigurado Torrentio (código abierto) limitado
        // a las webs españolas, para tener una sección solo con fuentes en castellano. Se quita desde Ajustes → Addons.
        if (BuildConfig.DEBUG) {
            tokenStore.seedOnce(TokenStore.keyNameFor(ApiService.AIOSTREAMS), DEBUG_SPANISH_ADDON)
        }
        StartupTrace.mark("App claves")
        watchNext.start()
        StartupTrace.mark("App watchNext")
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            episodeTracking.notificationsEnabled.collect { enabled ->
                EpisodeNotificationScheduler.sync(this@App, enabled)
            }
        }
    }

    override fun newImageLoader(): ImageLoader = imageLoader.get()

    private companion object {
        const val DEBUG_SPANISH_ADDON =
            "https://torrentio.strem.fun/providers=mejortorrent,wolfmax4k,cinecalidad|language=spanish/manifest.json"
    }
}
