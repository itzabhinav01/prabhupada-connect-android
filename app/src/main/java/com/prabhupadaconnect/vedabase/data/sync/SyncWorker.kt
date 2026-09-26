package com.prabhupadaconnect.vedabase.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Background periodic sync tick - runs [ResearchSyncService.syncNow] silently, matching the desktop app's periodic-sync timer. */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncService: ResearchSyncService
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = syncService.syncNow()
        // A configuration/auth error is not transient - don't hammer WorkManager's
        // retry backoff for it. Network-shaped failures get a normal retry.
        return if (result.success) Result.success() else Result.retry()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "vedabase_periodic_sync"

        fun schedule(context: Context, intervalMinutes: Int) {
            val workManager = WorkManager.getInstance(context)
            if (intervalMinutes <= 0) {
                workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(intervalMinutes.toLong(), TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
