package com.shieldstream.tv.data.di

import com.shieldstream.tv.data.repository.TraktClient
import com.shieldstream.tv.domain.TraktCredentials
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Singleton

/** Lo que vive en módulos de Kotlin puro (sin Hilt) y aquí se crea para la tele: Trakt e IntroDB. */
@Module
@InstallIn(SingletonComponent::class)
object TraktModule {
    @Provides
    @Singleton
    fun provideTraktClient(base: OkHttpClient, json: Json, credentials: TraktCredentials): TraktClient =
        TraktClient(base, json, credentials)

    /** IntroDB también vive en un módulo de Kotlin puro (:core:streams), compartido con escritorio. */
    @Provides
    @Singleton
    fun provideSkipSegmentsRepository(base: OkHttpClient, json: Json): com.shieldstream.tv.domain.SkipSegmentsRepository =
        com.shieldstream.tv.data.skip.IntroDbRepository(base, json)
}
