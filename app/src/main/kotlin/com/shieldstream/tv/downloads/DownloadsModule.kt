package com.shieldstream.tv.downloads

import com.shieldstream.tv.domain.DownloadsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadsModule {
    @Binds
    @Singleton
    abstract fun bindDownloads(impl: DownloadsRepositoryImpl): DownloadsRepository
}
