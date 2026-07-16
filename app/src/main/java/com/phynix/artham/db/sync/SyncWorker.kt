package com.phynix.artham.db.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * SyncWorker — Periodic background sync using Android WorkManager.
 *
 * Runs every 1 hour (when connected to network) to push local changes
 * to Supabase and pull remote updates.
 *
 * Also provides a one-shot immediate sync trigger for use when
 * network connectivity is restored.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SyncWorker"
        private const val PERIODIC_WORK_NAME = "artham_periodic_sync"
        private const val IMMEDIATE_WORK_NAME = "artham_immediate_sync"

        /**
         * Schedule periodic sync every 1 hour.
         * Call this once during app initialization (e.g., in MyApplication.onCreate).
         */
        @JvmStatic
        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicWork = PeriodicWorkRequestBuilder<SyncWorker>(
                1, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWork
            )

            Log.d(TAG, "Periodic sync scheduled (every 1 hour, network required)")
        }

        /**
         * Trigger an immediate one-shot sync.
         * Call this when network connectivity is restored.
         */
        @JvmStatic
        fun triggerImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val immediateWork = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                immediateWork
            )

            Log.d(TAG, "Immediate sync triggered")
        }
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "SyncWorker starting...")
            SyncEngine.syncAll(applicationContext)
            Log.d(TAG, "SyncWorker completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "SyncWorker failed", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
