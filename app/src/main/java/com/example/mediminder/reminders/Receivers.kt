package com.example.mediminder.reminders

import com.example.mediminder.util.ErrorLog
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.mediminder.data.local.entity.AlertType
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit

/** Gives receivers and workers (which Hilt does not construct) access to the singletons. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun engine(): ReminderEngine
    fun doseActions(): DoseActions
}

internal fun Context.reminders(): ReminderEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, ReminderEntryPoint::class.java)

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Runs suspending work from a receiver while keeping the process alive (goAsync). */
internal fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try {
            // Receivers get ~10 s; stay well inside that
            withTimeout(8_000) { block() }
        } catch (t: Throwable) {
            ErrorLog.record("Reminders", "Background work failed", t)
        } finally {
            pending.finish()
        }
    }
}

/** Every alarm set through [ReminderScheduler] lands here. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val engine = context.reminders().engine()
        runAsync {
            when (intent.action) {
                ReminderScheduler.ACTION_DOSE -> {
                    val scheduleId = intent.getLongExtra(ReminderScheduler.EXTRA_SCHEDULE_ID, -1)
                    val day = intent.getLongExtra(ReminderScheduler.EXTRA_DAY, -1)
                    val kind = intent.getStringExtra(ReminderScheduler.EXTRA_KIND) ?: ReminderScheduler.KIND_REGULAR
                    if (scheduleId > 0 && day >= 0) engine.onDoseAlarm(scheduleId, day, kind)
                }
                ReminderScheduler.ACTION_APPOINTMENT -> {
                    val id = intent.getLongExtra(ReminderScheduler.EXTRA_APPOINTMENT_ID, -1)
                    val which = intent.getStringExtra(ReminderScheduler.EXTRA_WHICH) ?: ReminderScheduler.WHICH_MAIN
                    if (id > 0) engine.onAppointmentAlarm(id, which)
                }
                ReminderScheduler.ACTION_REFILL_CHECK -> engine.onRefillCheck()
                ReminderScheduler.ACTION_TEST -> {
                    val type = runCatching { AlertType.valueOf(intent.getStringExtra(ReminderScheduler.EXTRA_ALERT_TYPE) ?: "") }
                        .getOrDefault(AlertType.ALARM)
                    engine.onTest(type)
                }
            }
        }
    }
}

/** Taken / Snooze / Skip buttons on the dose notifications. */
class DoseActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val actions = context.reminders().doseActions()
        val scheduleId = intent.getLongExtra(ReminderScheduler.EXTRA_SCHEDULE_ID, -1)
        val day = intent.getLongExtra(ReminderScheduler.EXTRA_DAY, -1)
        runAsync {
            when (intent.action) {
                ACTION_TAKE -> if (scheduleId > 0) actions.take(scheduleId, day)
                ACTION_SKIP -> if (scheduleId > 0) actions.skip(scheduleId, day)
                ACTION_SNOOZE -> if (scheduleId > 0) actions.snooze(scheduleId, day)
                ACTION_TAKE_ALL -> actions.takeAllRinging()
                ACTION_SNOOZE_ALL -> actions.snoozeAllRinging()
            }
        }
    }

    companion object {
        const val ACTION_TAKE = "com.example.mediminder.action.TAKE"
        const val ACTION_SKIP = "com.example.mediminder.action.SKIP"
        const val ACTION_SNOOZE = "com.example.mediminder.action.SNOOZE"
        const val ACTION_TAKE_ALL = "com.example.mediminder.action.TAKE_ALL"
        const val ACTION_SNOOZE_ALL = "com.example.mediminder.action.SNOOZE_ALL"
    }
}

/**
 * Android forgets every alarm on reboot, and alarm times are wrong after the clock or time
 * zone changes — re-arm everything when any of that happens.
 */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            "android.intent.action.QUICKBOOT_POWERON", // some Xiaomi / HTC builds
            -> {
                val engine = context.reminders().engine()
                runAsync { engine.rescheduleAll() }
                SafetyNetWorker.ensureScheduled(context)
            }
        }
    }
}

/**
 * Safety net: some phones (Xiaomi, Oppo, Vivo, Realme…) silently drop alarms when they
 * "optimise" background apps. Re-arming every few hours puts them back.
 */
class SafetyNetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        applicationContext.reminders().engine().rescheduleAll()
        return Result.success()
    }

    companion object {
        private const val NAME = "mediminder-safety-net"

        fun ensureScheduled(context: Context) {
            val request = PeriodicWorkRequestBuilder<SafetyNetWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
