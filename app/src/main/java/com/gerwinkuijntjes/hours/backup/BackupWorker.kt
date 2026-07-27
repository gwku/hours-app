package com.gerwinkuijntjes.hours.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Uploads a full snapshot in the background.
 *
 * Two triggers, both deliberate:
 *  - a periodic run so a phone that is never touched still checks in daily;
 *  - a debounced run after an edit, so a change is off the device within the hour
 *    instead of waiting for the next period.
 */
class BackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return when (val result = BackupUploader(applicationContext).upload()) {
            is BackupUploader.Result.Success -> Result.success()
            // Nothing configured yet: succeed quietly rather than retry forever.
            is BackupUploader.Result.NotConfigured -> Result.success()
            is BackupUploader.Result.NothingToBackUp -> Result.success()
            is BackupUploader.Result.Failed ->
                if (result.retryable) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val PERIODIC_WORK = "backup-periodic"
        private const val AFTER_EDIT_WORK = "backup-after-edit"

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Call once on app start. Safe to call repeatedly. */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /**
         * Queue an upload after a change. Replacing the previous request means a
         * burst of edits results in one upload, fifteen minutes after the last one.
         */
        fun scheduleAfterEdit(context: Context) {
            val request = OneTimeWorkRequestBuilder<BackupWorker>()
                .setConstraints(constraints)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                AFTER_EDIT_WORK,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
