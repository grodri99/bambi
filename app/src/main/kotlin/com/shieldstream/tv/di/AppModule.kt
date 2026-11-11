package com.shieldstream.tv.di

import com.shieldstream.tv.BuildConfig
import com.shieldstream.tv.domain.SelectedContentHolder
import com.shieldstream.tv.domain.TraktCredentials
import com.shieldstream.tv.domain.AppVersion
import com.shieldstream.tv.domain.IthilConfig
import com.shieldstream.tv.domain.IthilDownloadScheduler
import com.shieldstream.tv.IthilLibraryScheduler
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

    @Provides
    @Singleton
    fun provideAppVersion(): AppVersion =
        AppVersion(
            name = BuildConfig.VERSION_NAME,
            isDebug = BuildConfig.DEBUG || BuildConfig.APPLICATION_ID.endsWith(".debug"),
            updateRepo = "grodri99/bambi",
        )

    @Provides
    @Singleton
    fun provideTraktCredentials(): TraktCredentials =
        TraktCredentials(BuildConfig.TRAKT_CLIENT_ID, BuildConfig.TRAKT_CLIENT_SECRET, BuildConfig.TRAKT_REDIRECT_URI)

    @Provides
    @Singleton
    fun provideIthilConfig(): IthilConfig =
        IthilConfig(
            BuildConfig.ITHIL_METADATA_URL,
            BuildConfig.ITHIL_INDEX_KEY,
            BuildConfig.ITHIL_ARCHIVE_PREFIX,
        )

    @Provides
    @Singleton
    fun provideIthilDownloadScheduler(scheduler: IthilLibraryScheduler): IthilDownloadScheduler = scheduler
}
