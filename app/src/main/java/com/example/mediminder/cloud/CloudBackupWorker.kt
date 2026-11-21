package com.example.mediminder.cloud

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.mediminder.reminders.ReminderEntryPoint
import com.example.mediminder.util.ErrorLog
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.EntryPointAccessors
import java.util.concurrent.TimeUnit

/**
 * Keeps the cloud copy up to date: a couple of minutes after the data last changed (so a
 * burst of changes makes one upload), whenever there is internet.
 */
class CloudBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!CloudConfig.available) return Result.success()
        CloudConfig.init(applicationContext)
        if (FirebaseAuth.getInstance().currentUser == null) return Result.success()
        return try {
            EntryPointAccessors.fromApplication(applicationContext, ReminderEntryPoint::class.java).cloudBackup().upload()
            Result.success()
        } catch (e: Exception) {
            ErrorLog.record("Cloud", "Automatic cloud backup failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val NAME = "mediminder-cloud-backup"

        /** Call after any change: only signed-in people are backed up. */
        fun soon(context: Context) {
            if (!CloudConfig.available) return
            if (runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull() == null) return
            val request = OneTimeWorkRequestBuilder<CloudBackupWorker>()
                .setInitialDelay(2, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
