package com.example.mediminder.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.mediminder.R

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val medicationId = intent.getLongExtra("MEDICATION_ID", -1L)
        if (medicationId == -1L) return

        showFullScreenNotification(context, medicationId)
    }

    private fun showFullScreenNotification(context: Context, medicationId: Long) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "mediminder_alarms"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Medication Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Full screen alarms for medication reminders"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val fullScreenIntent = Intent(context, FullScreenAlarmActivity::class.java).apply {
            putExtra("MEDICATION_ID", medicationId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            medicationId.hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Time for your medication")
            .setContentText("Tap to view details")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(medicationId.hashCode(), notification)
    }
}
