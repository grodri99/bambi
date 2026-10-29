package com.shieldstream.tv.data.di

import com.shieldstream.tv.data.repository.AllDebridRepositoryImpl
import com.shieldstream.tv.data.repository.ApiKeysRepositoryImpl
import com.shieldstream.tv.data.repository.TorrentioLinkResolverImpl
import com.shieldstream.tv.domain.TorrentioLinkResolver
import com.shieldstream.tv.data.repository.CatalogRepositoryImpl
import com.shieldstream.tv.domain.AllDebridRepository
import com.shieldstream.tv.domain.ApiKeysRepository
import com.shieldstream.tv.data.repository.RealDebridRepositoryImpl
import com.shieldstream.tv.data.repository.SettingsRepositoryImpl
import com.shieldstream.tv.data.repository.WatchProgressRepositoryImpl
import com.shieldstream.tv.domain.CatalogRepository
import com.shieldstream.tv.domain.RealDebridRepository
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.domain.SubtitleRepository
import com.shieldstream.tv.data.subtitles.SubtitleRepositoryImpl
import com.shieldstream.tv.domain.WatchProgressRepository
import com.shieldstream.tv.data.repository.ProfileRepositoryImpl
import com.shieldstream.tv.data.repository.TraktRepositoryImpl
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.domain.TraktRepository
import com.shieldstream.tv.data.update.UpdateRepositoryImpl
import com.shieldstream.tv.domain.UpdateRepository
import com.shieldstream.tv.data.skip.IntroDbRepository
import com.shieldstream.tv.domain.SkipSegmentsRepository
import com.shieldstream.tv.data.repository.MyListRepositoryImpl
import com.shieldstream.tv.domain.MyListRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindTorrentioLinkResolver(impl: TorrentioLinkResolverImpl): TorrentioLinkResolver

    @Binds
    @Singleton
    abstract fun bindApiKeysRepository(impl: ApiKeysRepositoryImpl): ApiKeysRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindRealDebridRepository(impl: RealDebridRepositoryImpl): RealDebridRepository

    @Binds
    @Singleton
    abstract fun bindAllDebridRepository(impl: AllDebridRepositoryImpl): AllDebridRepository

    @Binds
    @Singleton
    abstract fun bindCatalogRepository(impl: CatalogRepositoryImpl): CatalogRepository

    @Binds
    @Singleton
    abstract fun bindWatchProgressRepository(impl: WatchProgressRepositoryImpl): WatchProgressRepository

    @Binds
    @Singleton
    abstract fun bindSubtitleRepository(impl: SubtitleRepositoryImpl): SubtitleRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindTraktRepository(impl: TraktRepositoryImpl): TraktRepository

    @Binds
    @Singleton
    abstract fun bindUpdateRepository(impl: UpdateRepositoryImpl): UpdateRepository

    @Binds
    @Singleton
    abstract fun bindSkipSegmentsRepository(impl: IntroDbRepository): SkipSegmentsRepository

    @Binds
    @Singleton
    abstract fun bindMyListRepository(impl: MyListRepositoryImpl): MyListRepository
}
