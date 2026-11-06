package com.shieldstream.tv.data.repository

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.shieldstream.tv.domain.IthilConfig
import com.shieldstream.tv.domain.IthilLibraryInfo
import com.shieldstream.tv.domain.IthilLibraryRepository
import com.shieldstream.tv.domain.IthilLibraryState
import com.shieldstream.tv.domain.IthilRemoteLibrary
import com.shieldstream.tv.domain.IthilUpdateRepository
import com.shieldstream.tv.network.api.IthilApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IthilUpdateRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val config: IthilConfig,
    private val api: IthilApi,
    private val library: IthilLibraryRepository,
) : IthilUpdateRepository {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val libraryName = Regex(Regex.escape(config.archivePrefix) + "_(\\d+)_(\\d+)_(\\d+)\\.zm3")
    private val mutableState = MutableStateFlow<IthilLibraryState>(
        library.info()?.let(IthilLibraryState::Ready) ?: IthilLibraryState.NotInstalled,
    )
    override val state: StateFlow<IthilLibraryState> = mutableState.asStateFlow()

    @VisibleForTesting
    internal var clock: () -> Long = System::currentTimeMillis

    override suspend fun remoteLibrary(): IthilRemoteLibrary {
        check(config.isConfigured) { "Ithil is not configured" }
        val entries = api.contents(config.metadataUrl.trimEnd('/') + "/contents")
        val best = entries.mapNotNull { entry ->
            val match = libraryName.matchEntire(entry.name) ?: return@mapNotNull null
            val version = match.groupValues.drop(1).map(String::toInt)
            Triple(version, entry, match)
        }.maxWithOrNull(compareBy({ it.first[0] }, { it.first[1] }, { it.first[2] }))?.second
        require(best != null) { "No Ithil library was found" }
        require(best.download_url.startsWith("https://")) { "Invalid Ithil library download URL" }
        return IthilRemoteLibrary(best.name, best.sha, best.download_url)
    }

    override suspend fun downloadNow(): IthilLibraryInfo {
        try {
            val remote = remoteLibrary()
            library.info()?.takeIf { it.sourceSha == remote.sha }?.let { current ->
                mutableState.value = IthilLibraryState.Ready(current)
                clearFailure()
                return current
            }
            mutableState.value = IthilLibraryState.Downloading(0f)
            val info = library.install(remote.downloadUrl, remote.sha) { progress ->
                mutableState.value = if (progress >= 1f) {
                    IthilLibraryState.Indexing
                } else {
                    IthilLibraryState.Downloading(progress)
                }
            }
            mutableState.value = IthilLibraryState.Ready(info)
            clearFailure()
            return info
        } catch (e: Exception) {
            mutableState.value = IthilLibraryState.Error(e.message ?: e.javaClass.simpleName)
            recordFailure()
            throw e
        }
    }

    override suspend fun checkForUpdates(): IthilLibraryInfo? {
        if (!library.isReady() || clock() < prefs.getLong(KEY_NEXT_CHECK, 0L)) return null
        return runCatching { downloadNow() }.getOrNull()
    }

    override suspend fun delete() {
        library.delete()
        prefs.edit().remove(KEY_NEXT_CHECK).remove(KEY_FAILURES).apply()
        mutableState.value = IthilLibraryState.NotInstalled
    }

    private fun recordFailure() {
        val failures = prefs.getInt(KEY_FAILURES, 0) + 1
        val hours = minOf(BASE_BACKOFF_HOURS * (1L shl (failures - 1).coerceAtMost(4)), MAX_BACKOFF_HOURS)
        prefs.edit()
            .putInt(KEY_FAILURES, failures)
            .putLong(KEY_NEXT_CHECK, clock() + TimeUnit.HOURS.toMillis(hours))
            .apply()
    }

    private fun clearFailure() {
        prefs.edit().remove(KEY_NEXT_CHECK).remove(KEY_FAILURES).apply()
    }

    private companion object {
        const val PREFS = "ithil_updates"
        const val KEY_NEXT_CHECK = "next_check"
        const val KEY_FAILURES = "failures"
        const val BASE_BACKOFF_HOURS = 1L
        const val MAX_BACKOFF_HOURS = 24L
    }
}
