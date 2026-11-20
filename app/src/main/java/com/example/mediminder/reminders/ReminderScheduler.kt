package com.example.mediminder.reminders

import com.example.mediminder.util.ErrorLog
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.mediminder.MainActivity
import com.example.mediminder.data.local.entity.AlertType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper over [AlarmManager]. Every alarm is a one-shot; [ReminderEngine] chains the
 * next one each time an alarm fires and re-arms everything after reboot / time changes.
 *
 * Alarm identity comes from the intent's data URI, so one alarm per dose time, per
 * snooze/repeat slot, per appointment reminder:
 *   mediminder://dose/{scheduleId}                 the regular daily alarm
 *   mediminder://dose-repeat/{scheduleId}/{day}    a snooze or "not answered yet" repeat
 *   mediminder://appointment/{id}/{main|evening}   appointment reminders
 *   mediminder://refill                            the daily low-stock check
 *   mediminder://test                              "test my alarm"
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** False on Android 12+ when the user has not allowed exact alarms (reminders may then be late). */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun scheduleDose(scheduleId: Long, day: Long, minuteOfDay: Int, triggerAt: Long, alertType: AlertType) {
        val intent = receiverIntent(ACTION_DOSE, "dose/$scheduleId")
            .putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            .putExtra(EXTRA_DAY, day)
            .putExtra(EXTRA_MINUTE, minuteOfDay)
            .putExtra(EXTRA_KIND, KIND_REGULAR)
        set(triggerAt, broadcast(intent), alarmClock = alertType == AlertType.ALARM)
    }

    fun cancelDose(scheduleId: Long) = cancel(receiverIntent(ACTION_DOSE, "dose/$scheduleId"))

    /** A snooze or an automatic repeat for one dose; replaces any earlier one for that dose. */
    fun scheduleDoseRepeat(scheduleId: Long, day: Long, triggerAt: Long, alertType: AlertType, kind: String) {
        val intent = receiverIntent(ACTION_DOSE, "dose-repeat/$scheduleId/$day")
            .putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            .putExtra(EXTRA_DAY, day)
            .putExtra(EXTRA_KIND, kind)
        set(triggerAt, broadcast(intent), alarmClock = alertType == AlertType.ALARM)
    }

    fun cancelDoseRepeat(scheduleId: Long, day: Long) = cancel(receiverIntent(ACTION_DOSE, "dose-repeat/$scheduleId/$day"))

    fun scheduleAppointment(appointmentId: Long, which: String, triggerAt: Long, alertType: AlertType) {
        val intent = receiverIntent(ACTION_APPOINTMENT, "appointment/$appointmentId/$which")
            .putExtra(EXTRA_APPOINTMENT_ID, appointmentId)
            .putExtra(EXTRA_WHICH, which)
        set(triggerAt, broadcast(intent), alarmClock = alertType == AlertType.ALARM)
    }

    fun cancelAppointment(appointmentId: Long) {
        cancel(receiverIntent(ACTION_APPOINTMENT, "appointment/$appointmentId/$WHICH_MAIN"))
        cancel(receiverIntent(ACTION_APPOINTMENT, "appointment/$appointmentId/$WHICH_EVENING"))
    }

    fun scheduleRefillCheck(triggerAt: Long) =
        set(triggerAt, broadcast(receiverIntent(ACTION_REFILL_CHECK, "refill")), alarmClock = false)

    fun scheduleTest(triggerAt: Long, alertType: AlertType) {
        val intent = receiverIntent(ACTION_TEST, "test").putExtra(EXTRA_ALERT_TYPE, alertType.name)
        set(triggerAt, broadcast(intent), alarmClock = alertType == AlertType.ALARM)
    }

    private fun receiverIntent(action: String, path: String) =
        Intent(context, ReminderReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("mediminder://$path"))

    private fun broadcast(intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun cancel(intent: Intent) {
        val pending = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pending)
        pending.cancel()
    }

    /**
     * Alarm-type doses use setAlarmClock — the most reliable API (exempt from Doze and from
     * most OEM battery savers, and it shows the alarm icon). Reminders use an exact,
     * Doze-exempt alarm. Without exact-alarm permission both fall back to an inexact alarm.
     */
    private fun set(triggerAt: Long, operation: PendingIntent, alarmClock: Boolean) {
        try {
            when {
                alarmClock && canScheduleExact() -> {
                    val show = PendingIntent.getActivity(
                        context, 0, Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), operation)
                }
                canScheduleExact() -> alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
                else -> alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            }
        } catch (e: SecurityException) {
            ErrorLog.record("Reminders", "Exact alarm refused, falling back to inexact", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        }
    }

    companion object {
        private const val TAG = "ReminderScheduler"

        const val ACTION_DOSE = "com.example.mediminder.action.DOSE"
        const val ACTION_APPOINTMENT = "com.example.mediminder.action.APPOINTMENT"
        const val ACTION_REFILL_CHECK = "com.example.mediminder.action.REFILL_CHECK"
        const val ACTION_TEST = "com.example.mediminder.action.TEST"

        const val EXTRA_SCHEDULE_ID = "scheduleId"
        const val EXTRA_DAY = "day"
        const val EXTRA_MINUTE = "minute"
        const val EXTRA_KIND = "kind"
        const val EXTRA_APPOINTMENT_ID = "appointmentId"
        const val EXTRA_WHICH = "which"
        const val EXTRA_ALERT_TYPE = "alertType"

        const val KIND_REGULAR = "regular"
        const val KIND_SNOOZE = "snooze"
        const val KIND_REPEAT = "repeat"

        const val WHICH_MAIN = "main"
        const val WHICH_EVENING = "evening"
    }
}
