package com.example.mediminder.reminders

import android.content.Context
import androidx.core.app.NotificationManagerCompat

/**
 * Gentle reminders to finish the phone settings that reminders depend on.
 *
 *  • Today's card has "Remind me tomorrow", which hides it for a day.
 *  • While notifications work but another setting is still off, a quiet notification is posted
 *    at most every 3 days — the first one 3 days after the problem is first seen, so people
 *    setting the app up are not nagged — and only while there are medicines to remind about.
 */
object SetupNudge {
    private const val PREFS = "mediminder_setup"
    private const val DAY = 24 * 60 * 60_000L
    const val LATER_FOR = DAY
    const val NUDGE_EVERY = 3 * DAY

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isSnoozed(context: Context): Boolean = System.currentTimeMillis() < prefs(context).getLong("laterUntil", 0)

    fun snooze(context: Context) {
        prefs(context).edit().putLong("laterUntil", System.currentTimeMillis() + LATER_FOR).apply()
    }

    /** Pure decision, so it can be tested: 0 = the clock has not started yet. */
    internal fun shouldNudge(now: Long, clockStartedAt: Long, lastNudge: Long, problems: Int, notificationsOn: Boolean, hasMedicines: Boolean): Boolean =
        notificationsOn && hasMedicines && problems > 0 && clockStartedAt > 0 &&
            now - clockStartedAt >= NUDGE_EVERY && now - lastNudge >= NUDGE_EVERY

    /** Called by the 6-hourly safety net. */
    fun maybeNotify(context: Context, notifier: Notifier, hasMedicines: Boolean) {
        val problems = Reliability.criticalProblems(context).filter { it.check != ReliabilityCheck.NOTIFICATIONS }
        val prefs = prefs(context)
        val now = System.currentTimeMillis()
        if (problems.isEmpty()) {
            prefs.edit().remove("problemSince").apply()   // fixed: the next problem starts a new clock
            return
        }
        val since = prefs.getLong("problemSince", 0).takeIf { it > 0 } ?: now.also { prefs.edit().putLong("problemSince", it).apply() }
        val notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!shouldNudge(now, since, prefs.getLong("lastNudge", 0), problems.size, notificationsOn, hasMedicines)) return
        notifier.showSetupNudge(problems.first().check.whyItMatters(), problems.size)
        prefs.edit().putLong("lastNudge", now).apply()
    }
}
