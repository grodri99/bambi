package com.shieldstream.tv

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shieldstream.tv.domain.IthilDownloadScheduler
import com.shieldstream.tv.domain.IthilUpdateRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltWorker
class IthilLibraryDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val updates: IthilUpdateRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching { updates.downloadNow() }.fold(
        onSuccess = { Result.success() },
        onFailure = { Result.failure() },
    )
}

@HiltWorker
class IthilLibraryUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val updates: IthilUpdateRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        updates.checkForUpdates()
        return Result.success()
    }
}

class IthilLibraryScheduler @Inject constructor(
    @ApplicationContext context: Context,
) : IthilDownloadScheduler {
    private val workManager = WorkManager.getInstance(context)

    override fun downloadNow() {
        val request = OneTimeWorkRequestBuilder<IthilLibraryDownloadWorker>()
            .setConstraints(networkConstraints())
            .build()
        workManager.enqueueUniqueWork(MANUAL_WORK, ExistingWorkPolicy.KEEP, request)
    }

    override fun cancelDownloads() {
        workManager.cancelUniqueWork(MANUAL_WORK)
    }

    companion object {
        private const val MANUAL_WORK = "ithil_library_download"
        private const val PERIODIC_WORK = "ithil_library_updates"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<IthilLibraryUpdateWorker>(24, TimeUnit.HOURS)
                .setInitialDelay((0..TimeUnit.HOURS.toMillis(6)).random(), TimeUnit.MILLISECONDS)
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        private fun networkConstraints(): Constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    }
}
