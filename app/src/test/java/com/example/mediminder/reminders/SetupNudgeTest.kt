package com.example.mediminder.reminders

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/** Reminding people to finish the phone setup: gently, never more than every few days. */
@RunWith(AndroidJUnit4::class)
class SetupNudgeTest {
    private val day = 24 * 60 * 60_000L
    private val start = 1_000_000_000_000L

    @Test fun `the first nudge waits three days after the problem is first seen`() {
        assertFalse(SetupNudge.shouldNudge(start, start, 0, problems = 1, notificationsOn = true, hasMedicines = true))
        assertFalse(SetupNudge.shouldNudge(start + 2 * day, start, 0, 1, true, true))
        assertTrue(SetupNudge.shouldNudge(start + 3 * day, start, 0, 1, true, true))
    }

    @Test fun `never more often than every three days`() {
        val lastNudge = start + 3 * day
        assertFalse(SetupNudge.shouldNudge(lastNudge + day, start, lastNudge, 1, true, true))
        assertTrue(SetupNudge.shouldNudge(lastNudge + 3 * day, start, lastNudge, 1, true, true))
    }

    @Test fun `quiet when there is nothing to fix, nothing to remind about, or no way to notify`() {
        val later = start + 10 * day
        assertFalse("nothing to fix", SetupNudge.shouldNudge(later, start, 0, problems = 0, notificationsOn = true, hasMedicines = true))
        assertFalse("no medicines yet", SetupNudge.shouldNudge(later, start, 0, 1, notificationsOn = true, hasMedicines = false))
        assertFalse("notifications off", SetupNudge.shouldNudge(later, start, 0, 1, notificationsOn = false, hasMedicines = true))
        assertFalse("clock not started", SetupNudge.shouldNudge(later, 0, 0, 1, true, true))
    }

    @Config(sdk = [34])
    @Test fun `problems come in the order worth fixing, and disappear once fixed`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val problems = Reliability.criticalProblems(context).map { it.check }
        assertTrue(ReliabilityCheck.EXACT_ALARMS in problems)
        assertEquals("sorted by importance", problems.sortedBy { listOf(ReliabilityCheck.NOTIFICATIONS, ReliabilityCheck.EXACT_ALARMS, ReliabilityCheck.ALARM_CHANNEL, ReliabilityCheck.FULL_SCREEN).indexOf(it) }, problems)
        assertTrue("battery is a tip, not a blocker", ReliabilityCheck.BATTERY !in problems)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        assertTrue(ReliabilityCheck.EXACT_ALARMS !in Reliability.criticalProblems(context).map { it.check })
    }

    @Test fun `remind me tomorrow hides the card for a day`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertFalse(SetupNudge.isSnoozed(context))
        SetupNudge.snooze(context)
        assertTrue(SetupNudge.isSnoozed(context))
    }
}
