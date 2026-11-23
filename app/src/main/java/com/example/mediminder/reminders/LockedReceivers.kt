package com.example.mediminder.reminders

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.os.UserManagerCompat
import com.example.mediminder.R

/**
 * The phone restarted and nobody has unlocked it yet: book the doses saved in [LockedAlarms].
 * Runs before unlock (directBootAware), so it touches nothing but that saved copy.
 */
class LockedBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        if (UserManagerCompat.isUserUnlocked(context)) return   // already unlocked: the app's own path runs
        LockedAlarms.arm(context)
    }
}

/**
 * A saved dose came due while the phone is still locked after a restart. It rings (an alarm
 * dose over the lock screen through [LockedAlarmActivity]); answering it waits for the unlock,
 * when the app's own alarm takes over.
 */
class LockedAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // unlocked meanwhile: the app's own alarms are back and will ring this dose
        if (UserManagerCompat.isUserUnlocked(context)) return
        val entry = LockedAlarms.entryFrom(intent) ?: return
        post(context, entry)
    }

    companion object {
        internal fun post(context: Context, entry: LockedAlarms.Entry) {
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return
            val id = LockedAlarms.notificationId(entry)
            val page = PendingIntent.getActivity(
                context, id, LockedAlarmActivity.intent(context, entry, id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val text = entry.detail + "\n" + context.getString(R.string.locked_unlock_hint)
            val builder = NotificationCompat.Builder(context, if (entry.alarm) Notifier.CHANNEL_ALARMS else Notifier.CHANNEL_DOSES)
                .setSmallIcon(R.drawable.ic_stat_pill)
                .setColor(ContextCompat.getColor(context, R.color.brand))
                .setContentTitle(entry.title)
                .setContentText(entry.detail)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(page)
            if (entry.alarm) {
                builder.setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setOngoing(true)
                    .setFullScreenIntent(page, true)
                    .setTimeoutAfter(LockedAlarms.ringMinutes(context) * 60_000L)
            } else {
                builder.setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
            }
            val notification = builder.build().apply { if (entry.alarm) flags = flags or Notification.FLAG_INSISTENT }
            try {
                manager.notify(id, notification)
            } catch (_: SecurityException) {
                // notifications switched off between the check and the call
            }
        }
    }
}
