package com.shieldstream.tv.data.di

import com.shieldstream.tv.domain.AllDebridRepository
import com.shieldstream.tv.domain.PrioritizeSourcesUseCase
import com.shieldstream.tv.domain.RealDebridRepository
import com.shieldstream.tv.domain.ResolveStreamUseCase
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.domain.TorrentioLinkResolver
import com.shieldstream.tv.domain.ValidateTokenUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    @Singleton
    fun providePrioritizeSourcesUseCase(): PrioritizeSourcesUseCase = PrioritizeSourcesUseCase()

    @Provides
    @Singleton
    fun provideResolveStreamUseCase(
        repo: RealDebridRepository,
        torrentio: TorrentioLinkResolver,
        allDebrid: AllDebridRepository,
        settings: SettingsRepository,
    ): ResolveStreamUseCase = ResolveStreamUseCase(repo, torrentio, allDebrid, settings)

    @Provides
    @Singleton
    fun provideValidateTokenUseCase(
        realDebrid: RealDebridRepository,
        settings: SettingsRepository,
    ): ValidateTokenUseCase = ValidateTokenUseCase(realDebrid, settings)
}
