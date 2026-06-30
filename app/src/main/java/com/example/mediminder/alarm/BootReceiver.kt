package com.example.mediminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.mediminder.data.local.MediMinderDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Re-schedules all active medication alarms after a device reboot or app update,
 * because Android clears all AlarmManager alarms when the device powers off.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val db = MediMinderDatabase.getInstance(context)
        val scheduleDao = db.scheduleDao()
        val alarmScheduler = AlarmScheduler(context)

        // Use a background coroutine since onReceive must not block the main thread
        CoroutineScope(Dispatchers.IO).launch {
            val activeSchedules = scheduleDao.getAllActiveSchedules()
            activeSchedules.forEach { schedule ->
                val hour = ((schedule.timeOfDayMillis / 3_600_000L) % 24).toInt()
                val minute = ((schedule.timeOfDayMillis / 60_000L) % 60).toInt()

                val triggerTime = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    // If the time has already passed today, schedule for tomorrow
                    if (before(Calendar.getInstance())) add(Calendar.DATE, 1)
                }

                alarmScheduler.schedule(
                    medicationId = schedule.medicationId,
                    triggerAtMillis = triggerTime.timeInMillis,
                    timeOfDayMillis = schedule.timeOfDayMillis
                )
            }
        }
    }
}
