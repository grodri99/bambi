package com.shieldstream.tv.domain

import kotlinx.coroutines.flow.StateFlow

data class IthilRemoteLibrary(
    val name: String,
    val sha: String,
    val downloadUrl: String,
)

sealed interface IthilLibraryState {
    data object NotInstalled : IthilLibraryState
    data class Downloading(val progress: Float) : IthilLibraryState
    data object Indexing : IthilLibraryState
    data class Ready(val info: IthilLibraryInfo) : IthilLibraryState
    data class Error(val message: String) : IthilLibraryState
}

interface IthilUpdateRepository {
    val state: StateFlow<IthilLibraryState>

    suspend fun remoteLibrary(): IthilRemoteLibrary

    suspend fun downloadNow(): IthilLibraryInfo

    suspend fun checkForUpdates(): IthilLibraryInfo?

    suspend fun delete()
}

interface IthilDownloadScheduler {
    fun downloadNow()

    fun cancelDownloads()
}
