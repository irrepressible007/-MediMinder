package com.example.mediminder.reminders

import androidx.lifecycle.viewModelScope
import com.example.mediminder.data.AppointmentRepository
import com.example.mediminder.ui.today.TodayViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import com.example.mediminder.data.photos.MedicinePhotos
import android.graphics.Bitmap
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mediminder.data.MedicineRepository
import com.example.mediminder.data.backup.BackupManager
import com.example.mediminder.data.local.MediMinderDatabase
import com.example.mediminder.data.local.entity.AlertType
import com.example.mediminder.data.local.entity.DoseStatus
import com.example.mediminder.data.local.entity.Medication
import com.example.mediminder.data.local.entity.Schedule
import com.example.mediminder.data.settings.SettingsRepository
import com.example.mediminder.domain.minuteOf
import com.example.mediminder.domain.toEpochMillis
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import java.time.LocalDateTime

/**
 * The reminder engine against Android's real AlarmManager / NotificationManager logic
 * (Robolectric shadows): what gets armed, what rings, and what each answer does.
 */
@RunWith(AndroidJUnit4::class)
class ReminderEngineTest {
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

    /** Adds a medicine with one dose time [minutesFromNow] from now (negative = in the past). */
    private suspend fun addMedicine(
        minutesFromNow: Long,
        alert: AlertType,
        stock: Double = 20.0,
        threshold: Int = 5,
    ): Pair<Schedule, Long> {
        val due = LocalDateTime.now().withSecond(0).withNano(0).plusMinutes(minutesFromNow)
        val id = medicines.save(
            Medication(name = "Napa", strength = "500 mg", trackStock = true, stock = stock, refillThresholdDays = threshold),
            listOf(Schedule(medicationId = 0, minuteOfDay = minuteOf(due.toLocalTime()), alertType = alert)),
        )
        val schedule = db.scheduleDao().forMedication(id).single()
        return schedule to due.toLocalDate().toEpochDay()
    }

    private fun alarmFor(path: String) = alarms.scheduledAlarms.firstOrNull { it.operation?.let { op -> shadowOf(op).savedIntent.data?.toString() } == "mediminder://$path" }

    // (the test phone has no full-screen permission, so that notice is left out of the counts)
    private fun shownNotifications(): List<Notification> =
        shadowOf(notifications).allNotifications.filter { it.channelId != Notifier.CHANNEL_ALARM_SETUP }

    @Test fun `saving a medicine arms its next alarm`() = runBlocking {
        val (schedule, _) = addMedicine(minutesFromNow = 90, alert = AlertType.ALARM)
        val alarm = alarmFor("dose/${schedule.id}")
        assertNotNull("dose alarm armed", alarm)
        // Alarm-type doses use setAlarmClock (shows in the status bar, exempt from Doze)
        assertNotNull(context.getSystemService(AlarmManager::class.java).nextAlarmClock)
    }

    @Test fun `alarm rings, repeats, and Taken clears it and counts stock`() = runBlocking {
        val (schedule, day) = addMedicine(minutesFromNow = -2, alert = AlertType.ALARM, stock = 20.0)

        engine.onDoseAlarm(schedule.id, day, ReminderScheduler.KIND_REGULAR)

        // ringing: one active alert, an insistent full-screen alarm notification, a repeat armed
        assertEquals(1, db.activeAlertDao().getAll().size)
        val alarm = shownNotifications().single()
        assertTrue(alarm.flags and Notification.FLAG_INSISTENT != 0)
        assertNotNull(alarm.fullScreenIntent)
        assertNotNull(alarmFor("dose-repeat/${schedule.id}/$day"))
        // and tomorrow's dose is already armed
        assertNotNull(alarmFor("dose/${schedule.id}"))

        actions.take(schedule.id, day)

        assertTrue(db.activeAlertDao().getAll().isEmpty())
        assertTrue("alarm notification removed", shownNotifications().isEmpty())
        assertNull("repeat cancelled", alarmFor("dose-repeat/${schedule.id}/$day"))
        assertEquals(DoseStatus.TAKEN, db.doseLogDao().forOccurrence(schedule.id, day)!!.status)
        assertEquals(19.0, db.medicationDao().getById(schedule.medicationId)!!.stock, 0.0)
    }

    @Test fun `the next doses are kept where they can be read before the first unlock`() = runBlocking {
        val (schedule, _) = addMedicine(minutesFromNow = 60, alert = AlertType.ALARM)
        engine.rescheduleAll()
        val saved = LockedAlarms.load(context).filter { it.scheduleId == schedule.id }
        assertTrue("today's and tomorrow's dose", saved.size >= 2)
        assertTrue(saved.all { it.alarm && it.title.isNotBlank() && it.detail.isNotBlank() })
        assertEquals(alarmFor("dose/${schedule.id}")!!.triggerAtTime, saved.first().at)

        // answered doses drop out
        actions.take(schedule.id, saved.first().day)
        assertTrue(LockedAlarms.load(context).none { it.scheduleId == schedule.id && it.day == saved.first().day })
    }

    /**
     * The alarm page answers in a job tied to the page, and the page closes itself as soon as
     * nothing is ringing, which cancels that job. The answer must still finish: on a Redmi the
     * alarm notification stayed after Skip until its 3-minute timeout.
     */
    @Test fun `an answer finishes even when the screen that started it closes`() = runBlocking {
        for (answer in listOf("skip", "take", "snooze")) {
            val (schedule, day) = addMedicine(minutesFromNow = -2, alert = AlertType.ALARM)
            engine.onDoseAlarm(schedule.id, day, ReminderScheduler.KIND_REGULAR)
            assertEquals(1, shownNotifications().size)

            val page = CoroutineScope(Dispatchers.Default)
            page.launch(start = CoroutineStart.UNDISPATCHED) {
                when (answer) {
                    "skip" -> actions.skip(schedule.id, day)
                    "take" -> actions.take(schedule.id, day)
                    else -> actions.snoozeAllRinging()
                }
            }
            page.cancel()   // the page closed straight away
            withTimeout(10_000) { while (shownNotifications().isNotEmpty() || db.activeAlertDao().getAll().isNotEmpty()) delay(20) }

            assertTrue("$answer: alarm notification removed", shownNotifications().isEmpty())
            val logged = db.doseLogDao().forOccurrence(schedule.id, day)!!.status
            assertEquals(answer, when (answer) { "skip" -> DoseStatus.SKIPPED; "take" -> DoseStatus.TAKEN; else -> DoseStatus.SNOOZED }, logged)
            if (answer == "snooze") assertNotNull("snooze: rings again later", alarmFor("dose-repeat/${schedule.id}/$day"))
        }
    }

    @Test fun `the medicine's photo shows beside the reminder and in full when expanded`() = runBlocking {
        val (schedule, day) = addMedicine(minutesFromNow = -1, alert = AlertType.REMINDER)
        val photo = MedicinePhotos.save(context, Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888))
        val medication = db.medicationDao().getById(schedule.medicationId)!!
        db.medicationDao().update(medication.copy(photoUri = photo))

        engine.onDoseAlarm(schedule.id, day, ReminderScheduler.KIND_REGULAR)

        val n = shownNotifications().single()
        assertNotNull("side picture", n.getLargeIcon())
        assertNotNull("big picture when expanded", n.extras.get(Notification.EXTRA_PICTURE) ?: n.extras.get("android.pictureIcon"))
        assertEquals(3, n.actions.size)
    }

    @Test fun `without a photo the reminder stays text only`() = runBlocking {
        val (schedule, day) = addMedicine(minutesFromNow = -1, alert = AlertType.REMINDER)
        engine.onDoseAlarm(schedule.id, day, ReminderScheduler.KIND_REGULAR)
        assertNull(shownNotifications().single().getLargeIcon())
    }

    @Test fun `deleting a medicine keeps its history unless asked not to`() = runBlocking {
        val (schedule, day) = addMedicine(minutesFromNow = -2, alert = AlertType.REMINDER)
        actions.take(schedule.id, day)

        medicines.delete(schedule.medicationId)   // default: keep the history

        assertTrue("gone from the list", medicines.observeAll().first().isEmpty())
        assertNull("no more reminders", alarmFor("dose/${schedule.id}"))
        val kept = db.doseLogDao().observeHistory(0, null).first().single()
        assertEquals("Napa", kept.name)
        assertNotNull("marked deleted", kept.deletedAt)
        engine.rescheduleAll()
        assertNull("stays silent after a full re-arm", alarmFor("dose/${schedule.id}"))

        // Deleting it again with "also delete its history" removes everything
        medicines.delete(schedule.medicationId, keepHistory = false)
        assertTrue(db.doseLogDao().observeHistory(0, null).first().isEmpty())
        assertNull(db.medicationDao().getById(schedule.medicationId))
    }

    @Test fun `a wrong or too-new backup file is refused with a message the person can read`() {
        val backup = BackupManager(context, db, settings, engine)
        val notOurs = runCatching { backup.decode("{\"hello\": 1") }.exceptionOrNull()
        assertEquals(com.example.mediminder.R.string.backup_not_ours, (notOurs as com.example.mediminder.data.backup.BackupException).reason)
        val tooNew = runCatching { backup.decode("{\"format\": 99}") }.exceptionOrNull()
        assertEquals(com.example.mediminder.R.string.backup_too_new, (tooNew as com.example.mediminder.data.backup.BackupException).reason)
        // and both messages exist in Bangla too
        assertTrue(context.getString(notOurs.reason).isNotBlank())
    }

    @Test fun `another day never shows today's doses as taken`() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        try {
            val (schedule, day) = addMedicine(minutesFromNow = -2, alert = AlertType.REMINDER)
            actions.take(schedule.id, day)
            val today = TodayViewModel(medicines, AppointmentRepository(db, engine), db, settings, actions, com.example.mediminder.data.RamadanManager(settings, medicines, Notifier(context)))
            today.selectedDate.value = java.time.LocalDate.ofEpochDay(day)
            assertEquals(1, today.state.filterNotNull().first { it.date.toEpochDay() == day }.taken)

            // The very first picture of the next day must not reuse this day's records
            today.nextDay()
            val next = today.state.filterNotNull().first { it.date.toEpochDay() == day + 1 }
            assertEquals("the next day starts untouched", 0, next.taken)
            assertTrue(next.rows.none { it.state == com.example.mediminder.domain.DoseState.TAKEN })
            today.viewModelScope.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `a medicine with no history is simply removed`() = runBlocking {
        val (schedule, _) = addMedicine(minutesFromNow = 60, alert = AlertType.REMINDER)
        medicines.delete(schedule.medicationId)
        assertNull(db.medicationDao().getById(schedule.medicationId))
    }

    @Test fun `deleting a medicine while its reminder is showing clears everything`() = runBlocking {
        val (schedule, day) = addMedicine(minutesFromNow = -2, alert = AlertType.REMINDER)
        engine.onDoseAlarm(schedule.id, day, ReminderScheduler.KIND_REGULAR)
        assertEquals(1, shownNotifications().size)
        assertNotNull(alarmFor("dose-repeat/${schedule.id}/$day"))

        medicines.delete(schedule.medicationId)

        assertTrue("reminder removed from the screen", shownNotifications().isEmpty())
        assertNull("repeat cancelled", alarmFor("dose-repeat/${schedule.id}/$day"))
        assertNull("daily alarm cancelled", alarmFor("dose/${schedule.id}"))
    }

    @Test fun `stopping a medicine cancels a pending snooze`() = runBlocking {
        val (schedule, day) = addMedicine(minutesFromNow = -2, alert = AlertType.ALARM)
        engine.onDoseAlarm(schedule.id, day, ReminderScheduler.KIND_REGULAR)
        actions.snooze(schedule.id, day)
        assertNotNull(alarmFor("dose-repeat/${schedule.id}/$day"))

        medicines.setArchived(schedule.medicationId, true)

        assertNull("snooze cancelled", alarmFor("dose-repeat/${schedule.id}/$day"))
        assertNull(alarmFor("dose/${schedule.id}"))
    }

    @Test fun `tapping Taken twice only counts one dose`() = runBlocking {
        val (schedule, day) = addMedicine(minutesFromNow = -2, alert = AlertType.REMINDER, stock = 10.0)
        actions.take(schedule.id, day)
        actions.take(schedule.id, day)
        assertEquals(9.0, db.medicationDao().getById(schedule.medicationId)!!.stock, 0.0)
        actions.undo(schedule.id, day)
        assertEquals(10.0, db.medicationDao().getById(schedule.medicationId)!!.stock, 0.0)
        assertNull(db.doseLogDao().forOccurrence(schedule.id, day))
    }

    @Test fun `reminder type shows a normal notification with three buttons`() = runBlocking {
        val (schedule, day) = addMedicine(minutesFromNow = -1, alert = AlertType.REMINDER)
        engine.onDoseAlarm(schedule.id, day, ReminderScheduler.KIND_REGULAR)
        val n = shownNotifications().single()
        assertEquals(0, n.flags and Notification.FLAG_INSISTENT)
        assertNull(n.fullScreenIntent)
        assertEquals(3, n.actions.size)
    }

    @Test fun `snooze stops the alert and rings again later`() = runBlocking {
        val (schedule, day) = addMedicine(minutesFromNow = -1, alert = AlertType.ALARM)
        engine.onDoseAlarm(schedule.id, day, ReminderScheduler.KIND_REGULAR)

        val before = System.currentTimeMillis()
        actions.snooze(schedule.id, day)

        assertTrue(db.activeAlertDao().getAll().isEmpty())
        assertTrue(shownNotifications().isEmpty())
        val log = db.doseLogDao().forOccurrence(schedule.id, day)!!
        assertEquals(DoseStatus.SNOOZED, log.status)
        val again = alarmFor("dose-repeat/${schedule.id}/$day")!!
        assertTrue(again.triggerAtTime in (before + 9 * 60_000)..(before + 11 * 60_000))

        // the snooze alarm brings the alarm back
        engine.onDoseAlarm(schedule.id, day, ReminderScheduler.KIND_SNOOZE)
        assertEquals(1, db.activeAlertDao().getAll().size)
    }

    @Test fun `taking a dose early moves its alarm to the next day`() = runBlocking {
        val (schedule, day) = addMedicine(minutesFromNow = 120, alert = AlertType.REMINDER)
        val todays = alarmFor("dose/${schedule.id}")!!.triggerAtTime
        actions.take(schedule.id, day)
        val next = alarmFor("dose/${schedule.id}")!!.triggerAtTime
        assertEquals(24 * 60 * 60_000L, next - todays)
    }

    @Test fun `a dose answered already does not ring`() = runBlocking {
        val (schedule, day) = addMedicine(minutesFromNow = -1, alert = AlertType.ALARM)
        actions.skip(schedule.id, day)
        engine.onDoseAlarm(schedule.id, day, ReminderScheduler.KIND_REGULAR)
        assertTrue(shownNotifications().isEmpty())
        assertTrue(db.activeAlertDao().getAll().isEmpty())
    }

    @Test fun `an alarm hours late (phone was off) does not ring`() = runBlocking {
        val (schedule, day) = addMedicine(minutesFromNow = -180, alert = AlertType.ALARM)
        engine.onDoseAlarm(schedule.id, day, ReminderScheduler.KIND_REGULAR)
        assertTrue(shownNotifications().isEmpty())
    }

    @Test fun `after a reboot a dose due minutes ago still rings`() = runBlocking {
        val (schedule, _) = addMedicine(minutesFromNow = -10, alert = AlertType.ALARM)
        // a reboot wipes every alarm
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarms.scheduledAlarms.toList().forEach { alarm -> alarm.operation?.let(alarmManager::cancel) }
        assertTrue(alarms.scheduledAlarms.isEmpty())

        engine.rescheduleAll()

        val alarm = alarmFor("dose/${schedule.id}")!!
        assertTrue("catch-up fires within seconds", alarm.triggerAtTime - System.currentTimeMillis() < 10_000)
    }

    @Test fun `running low posts a refill alert once`() = runBlocking {
        val (schedule, day) = addMedicine(minutesFromNow = -1, alert = AlertType.REMINDER, stock = 6.0, threshold = 5)
        actions.take(schedule.id, day) // 5 left = 5 days → low
        val stockAlerts = shownNotifications().filter { it.channelId == Notifier.CHANNEL_REFILLS }
        assertEquals(1, stockAlerts.size)
        assertEquals(java.time.LocalDate.now().toEpochDay(), db.medicationDao().getById(schedule.medicationId)!!.lowStockAlertDay)

        // restocking re-arms the warning
        medicines.restock(schedule.medicationId, 30.0, 45.0, "Lazz Pharma")
        val med = db.medicationDao().getById(schedule.medicationId)!!
        assertEquals(35.0, med.stock, 0.0)
        assertNull(med.lowStockAlertDay)
        assertEquals(45.0, db.restockDao().getAll().single().pricePaid!!, 0.0)
    }

    @Test fun `as-needed daily limit is enforced unless overridden`() = runBlocking {
        val id = medicines.save(Medication(name = "Ace", isAsNeeded = true, maxDailyDoses = 2, trackStock = true, stock = 10.0), emptyList())
        assertEquals(AsNeededResult.Logged, actions.takeAsNeeded(id, 1.0))
        assertEquals(AsNeededResult.Logged, actions.takeAsNeeded(id, 1.0))
        assertTrue(actions.takeAsNeeded(id, 1.0) is AsNeededResult.LimitReached)
        assertEquals(AsNeededResult.Logged, actions.takeAsNeeded(id, 1.0, override = true))
        assertEquals(7.0, db.medicationDao().getById(id)!!.stock, 0.0)
    }

    @Test fun `stopping a medicine cancels its alarms`() = runBlocking {
        val (schedule, _) = addMedicine(minutesFromNow = 60, alert = AlertType.ALARM)
        assertNotNull(alarmFor("dose/${schedule.id}"))
        medicines.setArchived(schedule.medicationId, true)
        assertNull(alarmFor("dose/${schedule.id}"))
    }

    @Test fun `appointment reminders are armed before the visit and the evening before`() = runBlocking {
        val visit = LocalDateTime.now().plusDays(3).withHour(17).withMinute(0)
        val repo = com.example.mediminder.data.AppointmentRepository(db, engine)
        val id = repo.save(
            com.example.mediminder.data.local.entity.Appointment(doctorName = "Dr. Rahman", dateTime = visit.toEpochMillis(), reminderMinutesBefore = 120, remindDayBefore = true)
        )
        assertEquals(visit.minusHours(2).toEpochMillis(), alarmFor("appointment/$id/main")!!.triggerAtTime)
        assertEquals(visit.toLocalDate().minusDays(1).atTime(20, 0).toEpochMillis(), alarmFor("appointment/$id/evening")!!.triggerAtTime)
        repo.setStatus(id, com.example.mediminder.data.local.entity.AppointmentStatus.CANCELLED)
        assertNull(alarmFor("appointment/$id/main"))
    }

    @Test fun `backup restores everything into an empty phone`() = runBlocking {
        val (schedule, day) = addMedicine(minutesFromNow = -1, alert = AlertType.ALARM, stock = 12.0)
        actions.take(schedule.id, day)
        val backup = BackupManager(context, db, settings, engine)
        val json = backup.encode(backup.create())

        val fresh = Room.inMemoryDatabaseBuilder(context, MediMinderDatabase::class.java).allowMainThreadQueries().build()
        try {
            val freshEngine = ReminderEngine(context, fresh.medicationDao(), fresh.scheduleDao(), fresh.doseLogDao(), fresh.activeAlertDao(), fresh.appointmentDao(), ReminderScheduler(context), Notifier(context), settings)
            val counts = BackupManager(context, fresh, settings, freshEngine).restore(backup.decode(json))
            assertEquals(1, counts.medicines)
            assertEquals(11.0, fresh.medicationDao().getById(schedule.medicationId)!!.stock, 0.0)
            assertEquals(DoseStatus.TAKEN, fresh.doseLogDao().forOccurrence(schedule.id, day)!!.status)
            assertEquals(AlertType.ALARM, fresh.scheduleDao().getById(schedule.id)!!.alertType)
        } finally {
            fresh.close()
        }
    }
}
