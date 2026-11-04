package com.shieldstream.tv.di

import com.shieldstream.tv.BuildConfig
import com.shieldstream.tv.domain.SelectedContentHolder
import com.shieldstream.tv.domain.TraktCredentials
import com.shieldstream.tv.domain.AppVersion
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSelectedContentHolder(): SelectedContentHolder = SelectedContentHolder()

    /** App "Bambi" en Trakt (local.properties → BuildConfig). Vacío = Trakt desactivado en esta compilación. */
    /** Versión instalada y repositorio de GitHub del que salen las actualizaciones. */
    @Provides
    @Singleton
    fun provideAppVersion(): AppVersion =
        AppVersion(
            name = BuildConfig.VERSION_NAME,
            isDebug = BuildConfig.DEBUG || BuildConfig.APPLICATION_ID.endsWith(".debug"),
            updateRepo = "javiermolinar/bambi",
        )

    @Provides
    @Singleton
    fun provideTraktCredentials(): TraktCredentials =
        TraktCredentials(BuildConfig.TRAKT_CLIENT_ID, BuildConfig.TRAKT_CLIENT_SECRET, BuildConfig.TRAKT_REDIRECT_URI)
}
