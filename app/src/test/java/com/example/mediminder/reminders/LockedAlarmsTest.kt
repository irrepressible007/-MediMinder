package com.example.mediminder.reminders

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * A restart at night (a power cut) wipes every alarm, and until the first unlock the app can't
 * read its database. Doses saved in device-protected storage must still ring.
 */
@RunWith(AndroidJUnit4::class)
class LockedAlarmsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val notifications get() = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications
    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L

    private fun entry(at: Long, id: Long, alarm: Boolean = true) =
        LockedAlarms.Entry(at, scheduleId = id, day = 20_000, title = "Napa 500 mg", detail = "Take 1 tablet · 8:00 AM", alarm = alarm)

    private fun locked(value: Boolean) = shadowOf(context.getSystemService(UserManager::class.java)).setUserUnlocked(!value)

    @Before fun setUp() = locked(true)

    @Test fun `saved doses come back after a restart, before unlock`() {
        LockedAlarms.save(context, listOf(
            entry(now + 2 * hour, 1),                     // later today: an alarm
            entry(now + 3 * hour, 2, alarm = false),      // a gentle reminder
            entry(now - 10 * 60_000L, 3),                 // came due while the phone was off
            entry(now - 2 * hour, 4),                     // long past: not rung now
        ), ringMinutes = 3)

        LockedAlarms.arm(context, now)

        val booked = alarms.scheduledAlarms.associate { shadowOf(it.operation).savedIntent.data.toString() to it.triggerAtTime }
        assertEquals(3, booked.size)
        assertEquals(now + 2 * hour, booked["mediminder://locked/1/20000"])
        assertEquals(now + 3 * hour, booked["mediminder://locked/2/20000"])
        assertTrue("just missed rings in seconds", booked.getValue("mediminder://locked/3/20000") in now..now + 10_000)
        assertNull(booked["mediminder://locked/4/20000"])
        // the alarm is booked as an alarm clock (wakes the phone from deep sleep)
        assertNotNull(context.getSystemService(AlarmManager::class.java).nextAlarmClock)
    }

    @Test fun `before unlock it rings with the medicine's name`() {
        LockedAlarmReceiver().onReceive(context, LockedAlarms.intent(context, entry(now, 7)))
        val n = notifications.single()
        assertEquals(Notifier.CHANNEL_ALARMS, n.channelId)
        assertEquals("Napa 500 mg", n.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(n.flags and Notification.FLAG_INSISTENT != 0)
        assertNotNull("over the lock screen", n.fullScreenIntent)
    }

    @Test fun `a gentle reminder before unlock is an ordinary notification`() {
        LockedAlarmReceiver().onReceive(context, LockedAlarms.intent(context, entry(now, 8, alarm = false)))
        val n = notifications.single()
        assertEquals(Notifier.CHANNEL_DOSES, n.channelId)
        assertNull(n.fullScreenIntent)
    }

    @Test fun `once unlocked the app's own alarms take over`() {
        val e = entry(now + hour, 9)
        LockedAlarms.save(context, listOf(e), ringMinutes = 3)
        LockedAlarms.arm(context, now)
        LockedAlarmReceiver().onReceive(context, LockedAlarms.intent(context, e))
        assertEquals(1, notifications.size)

        locked(false)
        LockedAlarmReceiver().onReceive(context, LockedAlarms.intent(context, e))
        assertEquals("nothing new rings from the saved copy", 1, notifications.size)
        LockedAlarms.disarm(context)
        assertTrue("saved alarm removed", alarms.scheduledAlarms.isEmpty())
        assertTrue("its ringing removed", notifications.isEmpty())
    }
}
