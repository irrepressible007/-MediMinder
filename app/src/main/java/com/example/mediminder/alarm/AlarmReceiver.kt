package com.example.mediminder.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.mediminder.MainActivity
import com.example.mediminder.R
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val medicationId = intent.getLongExtra("MEDICATION_ID", -1L)
        val timeOfDayMillis = intent.getLongExtra("TIME_OF_DAY_MILLIS", -1L)
        if (medicationId == -1L) return

        // ── 1. Show notification ────────────────────────────────────────────
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Medication Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily medication reminder notifications"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = PendingIntent.getActivity(
            context,
            medicationId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("💊 Time for your medication!")
            .setContentText("Tap to open MediMinder and mark it as taken.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)
            .build()

        notificationManager.notify(medicationId.hashCode(), notification)

        // ── 2. Reschedule for tomorrow (so the alarm repeats every day) ─────
        if (timeOfDayMillis >= 0) {
            val hour = ((timeOfDayMillis / (1000 * 60 * 60)) % 24).toInt()
            val minute = ((timeOfDayMillis / (1000 * 60)) % 60).toInt()

            val tomorrow = Calendar.getInstance().apply {
                add(Calendar.DATE, 1)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            AlarmScheduler(context).schedule(medicationId, tomorrow.timeInMillis, timeOfDayMillis)
        }
    }

    companion object {
        const val CHANNEL_ID = "mediminder_alarms"
    }
}
