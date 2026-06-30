package com.example.mediminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedule an exact alarm.
     *
     * @param medicationId  The medication's DB id (used as notification id)
     * @param triggerAtMillis  Absolute epoch time when the alarm should fire
     * @param timeOfDayMillis  Offset millis from midnight (hour * 3600000 + min * 60000).
     *                         Stored in the intent so AlarmReceiver can reschedule for tomorrow.
     */
    fun schedule(medicationId: Long, triggerAtMillis: Long, timeOfDayMillis: Long = -1L) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("MEDICATION_ID", medicationId)
            putExtra("TIME_OF_DAY_MILLIS", timeOfDayMillis)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            // Use a unique request code combining medication and time so multiple
            // schedules for the same medication each get their own alarm slot.
            (medicationId.hashCode() * 31 + timeOfDayMillis.hashCode()),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
    }

    fun cancel(medicationId: Long) {
        // Cancel any pending intent for this medication (best-effort)
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medicationId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    /**
     * Calculate the next occurrence of [hour]:[minute] from now, scheduling for
     * tomorrow if the time has already passed today.
     */
    fun scheduleDaily(medicationId: Long, hour: Int, minute: Int) {
        val timeOfDayMillis = (hour * 3600_000L) + (minute * 60_000L)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DATE, 1)
        }
        schedule(medicationId, cal.timeInMillis, timeOfDayMillis)
    }
}
