package com.shieldstream.tv.data.di

import com.shieldstream.tv.domain.PrioritizeSourcesUseCase
import com.shieldstream.tv.domain.RealDebridRepository
import com.shieldstream.tv.domain.ResolveStreamUseCase
import com.shieldstream.tv.strings.ResourceResolveStreamTexts
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.domain.AllDebridAuthRepository
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
        allDebrid: AllDebridAuthRepository,
        texts: ResourceResolveStreamTexts,
    ): ResolveStreamUseCase = ResolveStreamUseCase(repo, torrentio, allDebrid, texts)

    @Provides
    @Singleton
    fun provideValidateTokenUseCase(
        realDebrid: RealDebridRepository,
        settings: SettingsRepository,
    ): ValidateTokenUseCase = ValidateTokenUseCase(realDebrid, settings)
}
