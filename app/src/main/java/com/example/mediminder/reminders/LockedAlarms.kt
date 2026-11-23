package com.example.mediminder.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import org.json.JSONObject

/**
 * Doses that must still ring after a restart, before the phone is unlocked.
 *
 * Android forgets every alarm when the phone restarts, and until the first unlock an app can't
 * read its own database or settings. A power cut at night would otherwise silence the morning
 * dose until someone unlocks the phone. So, whenever the engine books doses, it also keeps the
 * next few days' doses (time, name, amount) in Android's "device protected" storage, the one
 * place readable before unlock, the way the Clock app keeps its alarms.
 *
 *  • At LOCKED_BOOT_COMPLETED, [arm] books them again ([LockedBootReceiver]).
 *  • One that fires while the phone is still locked rings through [LockedAlarmReceiver].
 *  • After the first unlock the app's own engine takes over: [disarm] removes these, and the
 *    engine's catch-up rings any dose that just came due, with Taken / Skip / Snooze.
 */
object LockedAlarms {
    private const val PREFS = "locked_alarms"
    private const val KEY_ENTRIES = "entries"
    private const val KEY_RING = "ringMinutes"

    /** One dose time: [at] in epoch millis; [title] and [detail] already in the app's language. */
    data class Entry(
        val at: Long,
        val scheduleId: Long,
        val day: Long,
        val title: String,
        val detail: String,
        val alarm: Boolean,
    )

    private fun prefs(context: Context) =
        context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(context: Context, entries: List<Entry>, ringMinutes: Int) {
        val array = JSONArray()
        entries.forEach { e ->
            array.put(
                JSONObject()
                    .put("at", e.at).put("s", e.scheduleId).put("d", e.day)
                    .put("t", e.title).put("x", e.detail).put("a", e.alarm)
            )
        }
        prefs(context).edit().putString(KEY_ENTRIES, array.toString()).putInt(KEY_RING, ringMinutes).apply()
    }

    fun load(context: Context): List<Entry> {
        val text = prefs(context).getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(text)
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                Entry(o.getLong("at"), o.getLong("s"), o.getLong("d"), o.getString("t"), o.getString("x"), o.getBoolean("a"))
            }
        }.getOrDefault(emptyList())
    }

    fun ringMinutes(context: Context): Int = prefs(context).getInt(KEY_RING, 3)

    /**
     * Books the saved doses (phone restarted, still locked). A dose that came due in the last
     * [ReminderEngine.CATCH_UP_MINUTES] minutes, while the phone was off, rings in a few seconds.
     */
    fun arm(context: Context, now: Long = System.currentTimeMillis()) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val catchUpFrom = now - ReminderEngine.CATCH_UP_MINUTES * 60_000L
        for (e in load(context)) {
            if (e.at < catchUpFrom) continue
            val at = maxOf(e.at, now + 3_000L)
            val operation = PendingIntent.getBroadcast(context, 0, intent(context, e), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            try {
                if (e.alarm) {
                    val show = PendingIntent.getActivity(
                        context, 0, LockedAlarmActivity.intent(context, e, notificationId(e)),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), operation)
                } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
                    alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
                } else {
                    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
                }
            } catch (_: SecurityException) {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            }
        }
    }

    /** The phone was unlocked: the app's own alarms are back, so these go. */
    fun disarm(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val notifications = NotificationManagerCompat.from(context)
        for (e in load(context)) {
            PendingIntent.getBroadcast(context, 0, intent(context, e), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
                alarms?.cancel(it)
                it.cancel()
            }
            notifications.cancel(notificationId(e))
        }
    }

    internal fun notificationId(e: Entry): Int = 40_000 + Math.floorMod(e.scheduleId * 31 + e.day, 10_000L).toInt()

    internal fun intent(context: Context, e: Entry): Intent =
        Intent(context, LockedAlarmReceiver::class.java)
            .setData(Uri.parse("mediminder://locked/${e.scheduleId}/${e.day}"))
            .putExtra(EXTRA_SCHEDULE, e.scheduleId)
            .putExtra(EXTRA_DAY, e.day)
            .putExtra(EXTRA_AT, e.at)
            .putExtra(EXTRA_TITLE, e.title)
            .putExtra(EXTRA_DETAIL, e.detail)
            .putExtra(EXTRA_ALARM, e.alarm)

    internal fun entryFrom(intent: Intent): Entry? {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return null
        return Entry(
            at = intent.getLongExtra(EXTRA_AT, 0),
            scheduleId = intent.getLongExtra(EXTRA_SCHEDULE, 0),
            day = intent.getLongExtra(EXTRA_DAY, 0),
            title = title,
            detail = intent.getStringExtra(EXTRA_DETAIL).orEmpty(),
            alarm = intent.getBooleanExtra(EXTRA_ALARM, true),
        )
    }

    private const val EXTRA_SCHEDULE = "scheduleId"
    private const val EXTRA_DAY = "day"
    private const val EXTRA_AT = "at"
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_DETAIL = "detail"
    private const val EXTRA_ALARM = "alarm"
}
