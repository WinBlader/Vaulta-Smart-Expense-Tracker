package com.waleed.vaulta.android.data.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.waleed.vaulta.android.data.repository.ExpenseRepository
import com.waleed.vaulta.android.data.session.UserSessionManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * Background WorkManager worker responsible for reliable, network-constrained
 * synchronization of local RoomDB transactions and profile settings with Supabase.
 * Triggers automatically after offline saves or background auto-detection failures.
 */
class CloudSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncWorkerEntryPoint {
        fun expenseRepository(): ExpenseRepository
        fun userSessionManager(): UserSessionManager
    }

    companion object {
        private const val TAG = "VaultaCloudSync"
        const val WORK_NAME = "vaulta_cloud_sync_worker"

        /**
         * Enqueues a network-constrained, one-time sync request with exponential backoff.
         */
        fun enqueue(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val syncRequest = OneTimeWorkRequestBuilder<CloudSyncWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        15,
                        TimeUnit.SECONDS
                    )
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    syncRequest
                )
                Log.d(TAG, "Enqueued unique CloudSyncWorker request with CONNECTED network constraint")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enqueue CloudSyncWorker: ${e.message}", e)
            }
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Executing CloudSyncWorker doWork (attempt=$runAttemptCount)")
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                SyncWorkerEntryPoint::class.java
            )
            val sessionManager = entryPoint.userSessionManager()
            val userKey = sessionManager.getCurrentUserKey()

            if (userKey.isBlank() || userKey == "guest") {
                Log.d(TAG, "CloudSyncWorker skipped: user is not authenticated (guest session)")
                return Result.success()
            }

            val repository = entryPoint.expenseRepository()
            repository.syncAllCloudData(userKey)
            Log.d(TAG, "CloudSyncWorker finished successfully for user: $userKey")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "CloudSyncWorker execution failed: ${e.message}", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
