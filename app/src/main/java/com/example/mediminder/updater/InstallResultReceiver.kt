package com.example.mediminder.updater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.os.Build

/**
 * Android's answers about an update the app is installing (see [Updater.install]).
 *
 *  • "Needs the person's OK": opens Android's own "Update this app?" confirmation.
 *  • Success: nothing to do; Android restarts the app on the new version.
 *  • Cancelled: back to "ready to install".
 *  • Anything else: the update dialog says it failed, and the reason goes to the problem log.
 *
 * Not exported: only Android, through the app's own PendingIntent, can reach it.
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = confirmIntent(intent) ?: return Updater.installFailed("no confirmation screen")
                // only Android's own installer may be opened from here
                val target = confirm.resolveActivity(context.packageManager)?.packageName
                val system = target != null && runCatching {
                    context.packageManager.getApplicationInfo(target, 0).flags and ApplicationInfo.FLAG_SYSTEM != 0
                }.getOrDefault(false)
                if (!system) return Updater.installFailed("confirmation screen from $target")
                runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { Updater.installFailed("could not open the confirmation", it) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> Updater.installCancelled()
            else -> Updater.installFailed("status $status: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
        }
    }

    @Suppress("DEPRECATION")
    private fun confirmIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        else intent.getParcelableExtra(Intent.EXTRA_INTENT)
}
