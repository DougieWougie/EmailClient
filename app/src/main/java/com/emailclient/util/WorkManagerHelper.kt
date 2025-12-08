package com.emailclient.util

import android.content.Context
import androidx.work.*
import com.emailclient.data.local.AppPreferences
import com.emailclient.workers.EmailSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class for managing WorkManager tasks
 */
@Singleton
class WorkManagerHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences
) {

    /**
     * Schedule periodic email sync with configurable interval
     */
    fun schedulePeriodicSync() {
        val intervalMinutes = appPreferences.getSyncInterval()

        // If interval is 0, cancel all syncs (manual only mode)
        if (intervalMinutes == 0) {
            android.util.Log.d("WorkManagerHelper", "Manual sync only - canceling periodic sync")
            cancelSync()
            return
        }

        // WorkManager minimum interval is 15 minutes
        val actualInterval = maxOf(intervalMinutes, 15)

        android.util.Log.d("WorkManagerHelper", "Scheduling periodic sync every $actualInterval minutes")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<EmailSyncWorker>(
            actualInterval.toLong(), TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            EmailSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE, // Update to change interval while preserving running work
            syncRequest
        )
    }

    /**
     * Trigger immediate email sync
     */
    fun syncNow() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<EmailSyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(syncRequest)
    }

    /**
     * Cancel all scheduled syncs
     */
    fun cancelSync() {
        WorkManager.getInstance(context).cancelUniqueWork(EmailSyncWorker.WORK_NAME)
    }
}
