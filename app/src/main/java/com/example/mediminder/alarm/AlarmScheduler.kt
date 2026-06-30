package com.example.mediminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(medicationId: Long, timeInMillis: Long) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("MEDICATION_ID", medicationId)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medicationId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            timeInMillis,
            pendingIntent
        )
    }

    fun cancel(medicationId: Long) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medicationId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
