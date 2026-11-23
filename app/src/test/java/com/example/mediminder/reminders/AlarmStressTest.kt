package com.example.mediminder.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mediminder.data.AppointmentRepository
import com.example.mediminder.data.MedicineRepository
import com.example.mediminder.data.backup.BackupManager
import com.example.mediminder.data.local.MediMinderDatabase
import com.example.mediminder.data.local.entity.AlertType
import com.example.mediminder.data.local.entity.DoseStatus
import com.example.mediminder.data.local.entity.Medication
import com.example.mediminder.data.local.entity.Schedule
import com.example.mediminder.data.photos.MedicinePhotos
import com.example.mediminder.data.settings.SettingsRepository
import com.example.mediminder.domain.minuteOf
import com.example.mediminder.domain.toEpochMillis
import com.example.mediminder.ui.today.TodayViewModel
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

/**
 * Alarms and reminders under load and in awkward timing: many medicines, many doses in the same
 * minute, duplicate deliveries, repeated snoozes, answers from two places at once.
 */
@RunWith(AndroidJUnit4::class)
class AlarmStressTest {
    private lateinit var context: Context
    private lateinit var db: MediMinderDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var engine: ReminderEngine
    private lateinit var actions: DoseActions
    private lateinit var medicines: MedicineRepository
    private lateinit var alarms: ShadowAlarmManager
    private lateinit var notifications: NotificationManager

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        db = Room.inMemoryDatabaseBuilder(context, MediMinderDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsRepository(context)
        settings.update { it.copy(repeatReminders = true, repeatIntervalMinutes = 10, maxRepeats = 2, snoozeMinutes = 10) }
        engine = ReminderEngine(
            context, db.medicationDao(), db.scheduleDao(), db.doseLogDao(), db.activeAlertDao(), db.appointmentDao(),
            ReminderScheduler(context), Notifier(context), settings,
        )
        actions = DoseActions(db, engine, settings)
        medicines = MedicineRepository(db, engine, settings)
        alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
        notifications = context.getSystemService(NotificationManager::class.java)
    }

    @After fun tearDown() = db.close()

    private fun shown(): List<Notification> =
        shadowOf(notifications).allNotifications.filter { it.channelId != Notifier.CHANNEL_ALARM_SETUP }

    private fun booked(prefix: String) = alarms.scheduledAlarms.mapNotNull { a ->
        a.operation?.let { shadowOf(it).savedIntent.data?.toString() }?.takeIf { it.startsWith("mediminder://$prefix") }
    }

    /** Android drops an alarm from its list when it fires; the test calls the handler directly. */
    private fun fire(prefix: String) {
        val manager = context.getSystemService(AlarmManager::class.java)
        alarms.scheduledAlarms.filter { a -> a.operation?.let { shadowOf(it).savedIntent.data?.toString() }?.startsWith("mediminder://$prefix") == true }
            .forEach { a -> a.operation?.let { op -> manager.cancel(op) } }
    }

    /** A medicine with one dose [minutesFromNow] from now; returns its schedule and that dose's day. */
    private suspend fun add(name: String, minutesFromNow: Long, alert: AlertType, stock: Double = 30.0): Pair<Schedule, Long> {
        val due = LocalDateTime.now().withSecond(0).withNano(0).plusMinutes(minutesFromNow)
        val id = medicines.save(
            Medication(name = name, strength = "10 mg", trackStock = true, stock = stock, refillThresholdDays = 3),
            listOf(Schedule(medicationId = 0, minuteOfDay = minuteOf(due.toLocalTime()), alertType = alert)),
        )
        return db.scheduleDao().forMedication(id).single() to due.toLocalDate().toEpochDay()
    }

    @Test fun `forty medicines with four doses a day are all booked, once each`() = runBlocking {
        repeat(40) { i ->
            medicines.save(
                Medication(name = "Medicine $i", strength = "5 mg"),
                listOf(8 * 60, 13 * 60, 18 * 60, 22 * 60).map { m ->
                    Schedule(medicationId = 0, minuteOfDay = m + i % 30, alertType = if (i % 2 == 0) AlertType.ALARM else AlertType.REMINDER)
                },
            )
        }
        val started = System.currentTimeMillis()
        engine.rescheduleAll()
        engine.rescheduleAll()   // again: nothing may be booked twice
        val took = System.currentTimeMillis() - started
        val doses = booked("dose/")
        assertEquals(160, doses.size)
        assertEquals("no duplicates", 160, doses.toSet().size)
        assertTrue("re-arming everything twice took ${took} ms", took < 15_000)
        // the before-unlock copy stays within its limit
        assertTrue(LockedAlarms.load(context).size <= ReminderEngine.LOCKED_COPY_MAX)
    }

    @Test fun `six doses in the same minute ring as one alarm plus reminders, and clear one by one`() = runBlocking {
        val alarmDoses = (1..4).map { add("Alarm $it", -1, AlertType.ALARM) }
        val reminderDoses = (1..2).map { add("Reminder $it", -1, AlertType.REMINDER) }
        val first = alarmDoses.first()
        engine.onDoseAlarm(first.first.id, first.second, ReminderScheduler.KIND_REGULAR)
        val posted = shown().single()
        (alarmDoses.drop(1) + reminderDoses).forEach { (s, day) -> engine.onDoseAlarm(s.id, day, ReminderScheduler.KIND_REGULAR) }

        val alarm = shown().single { it.channelId == Notifier.CHANNEL_ALARMS }
        // posted once, already listing all four: a re-post while ringing stops the sound on Android 16
        assertSame("not re-posted as the others arrived", posted, alarm)
        assertEquals("one alarm lists all four", 4, (alarm.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES) ?: emptyArray()).size)
        assertEquals(2, shown().count { it.channelId == Notifier.CHANNEL_DOSES })

        // answering alarm doses one at a time keeps the rest ringing
        alarmDoses.dropLast(1).forEach { (s, day) -> actions.take(s.id, day) }
        val still = shown().single { it.channelId == Notifier.CHANNEL_ALARMS }
        assertTrue(still.flags and Notification.FLAG_INSISTENT != 0)
        actions.take(alarmDoses.last().first.id, alarmDoses.last().second)
        assertTrue("alarm gone after the last one", shown().none { it.channelId == Notifier.CHANNEL_ALARMS })
        assertEquals("reminders untouched", 2, shown().size)

        actions.takeAllRinging()
        assertTrue(shown().isEmpty())
        assertTrue(db.activeAlertDao().getAll().isEmpty())
        (alarmDoses + reminderDoses).forEach { (s, day) -> assertEquals(DoseStatus.TAKEN, db.doseLogDao().forOccurrence(s.id, day)!!.status) }
    }

    @Test fun `the same alarm delivered three times rings once`() = runBlocking {
        val (s, day) = add("Napa", -1, AlertType.ALARM)
        repeat(3) { engine.onDoseAlarm(s.id, day, ReminderScheduler.KIND_REGULAR) }
        assertEquals(1, db.activeAlertDao().getAll().size)
        assertEquals(1, shown().size)
        assertEquals(1, booked("dose-repeat/").size)
    }

    @Test fun `snoozing five times keeps exactly one later ring, and Taken ends it`() = runBlocking {
        val (s, day) = add("Napa", -1, AlertType.ALARM)
        engine.onDoseAlarm(s.id, day, ReminderScheduler.KIND_REGULAR)
        repeat(5) {
            actions.snoozeAllRinging()
            assertTrue("silent while snoozed", shown().isEmpty())
            assertEquals(1, booked("dose-repeat/${s.id}/$day").size)
            engine.onDoseAlarm(s.id, day, ReminderScheduler.KIND_SNOOZE)   // the snooze rings
            assertEquals(1, shown().size)
        }
        actions.take(s.id, day)
        assertTrue(shown().isEmpty())
        assertTrue(booked("dose-repeat/").isEmpty())
        assertEquals(DoseStatus.TAKEN, db.doseLogDao().forOccurrence(s.id, day)!!.status)
    }

    @Test fun `Taken from two places at the same moment counts one dose`() = runBlocking {
        val (s, day) = add("Napa", -1, AlertType.ALARM, stock = 10.0)
        engine.onDoseAlarm(s.id, day, ReminderScheduler.KIND_REGULAR)
        coroutineScope {
            repeat(4) { launch(Dispatchers.Default) { actions.take(s.id, day) } }
        }
        assertEquals(9.0, db.medicationDao().getById(s.medicationId)!!.stock, 0.0)
        assertEquals(1, db.doseLogDao().countForMedication(s.medicationId))
        assertTrue(shown().isEmpty())
    }

    @Test fun `an unanswered alarm repeats up to the limit, then stays quiet`() = runBlocking {
        val (s, day) = add("Napa", -1, AlertType.ALARM)
        engine.onDoseAlarm(s.id, day, ReminderScheduler.KIND_REGULAR)
        assertEquals(1, booked("dose-repeat/").size)
        fire("dose-repeat/"); engine.onDoseAlarm(s.id, day, ReminderScheduler.KIND_REPEAT)   // 1st repeat
        assertEquals("a 2nd repeat is booked", 1, booked("dose-repeat/").size)
        fire("dose-repeat/"); engine.onDoseAlarm(s.id, day, ReminderScheduler.KIND_REPEAT)   // 2nd repeat = the limit
        assertEquals(2, db.activeAlertDao().get(s.id, day)!!.repeatCount)
        assertTrue("no third repeat booked", booked("dose-repeat/").isEmpty())
        // the dose is still waiting for an answer, not lost
        assertNull(db.doseLogDao().forOccurrence(s.id, day))
        assertEquals(1, db.activeAlertDao().getAll().size)
    }

    @Test fun `deleting one of several ringing medicines leaves the others ringing`() = runBlocking {
        val a = add("Keep A", -1, AlertType.ALARM)
        val b = add("Delete me", -1, AlertType.ALARM)
        val c = add("Keep C", -1, AlertType.ALARM)
        listOf(a, b, c).forEach { (s, day) -> engine.onDoseAlarm(s.id, day, ReminderScheduler.KIND_REGULAR) }
        medicines.delete(b.first.medicationId, keepHistory = false)
        val alarm = shown().single { it.channelId == Notifier.CHANNEL_ALARMS }
        assertEquals(2, (alarm.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES) ?: emptyArray()).size)
        assertEquals(2, db.activeAlertDao().getAll().size)
    }

    @Test fun `changing a dose time many times leaves one alarm at the last time`() = runBlocking {
        val (s, _) = add("Napa", 60, AlertType.ALARM)
        val medication = db.medicationDao().getById(s.medicationId)!!
        var last = s
        for (offset in listOf(70L, 80L, 90L, 100L, 110L)) {
            val minute = minuteOf(LocalDateTime.now().plusMinutes(offset).toLocalTime())
            medicines.save(medication, listOf(last.copy(minuteOfDay = minute)))
            last = db.scheduleDao().forMedication(medication.id).single()
            assertEquals(minute, last.minuteOfDay)
        }
        val doses = booked("dose/")
        assertEquals(1, doses.size)
    }

    /** A restart or an update: Android drops the app's alarms and its notifications. */
    private fun restart() {
        val manager = context.getSystemService(AlarmManager::class.java)
        alarms.scheduledAlarms.toList().forEach { a -> a.operation?.let { manager.cancel(it) } }
        notifications.cancelAll()
    }

    @Test fun `a snooze survives a restart`() = runBlocking {
        val (s, day) = add("Napa", -1, AlertType.ALARM)
        engine.onDoseAlarm(s.id, day, ReminderScheduler.KIND_REGULAR)
        actions.snoozeAllRinging()
        val until = db.doseLogDao().forOccurrence(s.id, day)!!.snoozedUntil!!
        restart()
        engine.rescheduleAll()
        val repeat = alarms.scheduledAlarms.single { shadowOf(it.operation).savedIntent.data.toString() == "mediminder://dose-repeat/${s.id}/$day" }
        assertEquals("rings at its snooze time", until, repeat.triggerAtTime)
    }

    @Test fun `an alarm that was ringing when the app was stopped rings again`() = runBlocking {
        val (s, day) = add("Napa", -1, AlertType.ALARM)
        engine.onDoseAlarm(s.id, day, ReminderScheduler.KIND_REGULAR)
        restart()
        engine.rescheduleAll()
        val alarm = shown().single { it.channelId == Notifier.CHANNEL_ALARMS }
        assertEquals("a fresh alert, so it sounds", 0, alarm.flags and Notification.FLAG_ONLY_ALERT_ONCE)
        assertEquals(1, booked("dose-repeat/${s.id}/$day").size)
    }

    @Test fun `an old unanswered dose keeps its repeat but doesn't suddenly ring`() = runBlocking {
        val (s, day) = add("Napa", -30, AlertType.ALARM)
        engine.onDoseAlarm(s.id, day, ReminderScheduler.KIND_REGULAR)
        val alert = db.activeAlertDao().get(s.id, day)!!
        db.activeAlertDao().update(alert.copy(firedAt = System.currentTimeMillis() - 20 * 60_000L))
        restart()
        engine.rescheduleAll()
        assertTrue("not ringing now", shown().none { it.channelId == Notifier.CHANNEL_ALARMS })
        assertEquals("its next repeat is booked", 1, booked("dose-repeat/${s.id}/$day").size)
    }

    @Test fun `re-booking while an alarm rings doesn't make it ring twice`() = runBlocking {
        val (s, day) = add("Napa", -1, AlertType.ALARM)
        engine.onDoseAlarm(s.id, day, ReminderScheduler.KIND_REGULAR)
        val posted = shown().single()
        engine.rescheduleAll()   // e.g. the 6-hourly safety check while it rings
        assertSame("left alone while it rings", posted, shown().single { it.channelId == Notifier.CHANNEL_ALARMS })
        assertEquals(1, db.activeAlertDao().getAll().size)
    }
}
