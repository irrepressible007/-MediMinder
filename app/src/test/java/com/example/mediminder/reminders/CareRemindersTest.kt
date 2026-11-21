package com.example.mediminder.reminders

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mediminder.data.HealthRepository
import com.example.mediminder.data.MedicineRepository
import com.example.mediminder.data.settings.SettingsRepository
import com.example.mediminder.data.local.MediMinderDatabase
import com.example.mediminder.data.local.entity.AlertType
import com.example.mediminder.data.local.entity.DoseStatus
import com.example.mediminder.data.local.entity.Medication
import com.example.mediminder.data.local.entity.Reading
import com.example.mediminder.data.local.entity.Schedule
import com.example.mediminder.domain.CareKind
import com.example.mediminder.domain.CareSites
import com.example.mediminder.domain.MeasureType
import com.example.mediminder.domain.minuteOf
import com.example.mediminder.util.CareText
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.LocalDate
import java.time.LocalDateTime

/** Reminders for creams, drops, injections, health checks and habits. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CareRemindersTest {
    private lateinit var context: Context
    private lateinit var db: MediMinderDatabase
    private lateinit var engine: ReminderEngine
    private lateinit var medicines: MedicineRepository
    private lateinit var health: HealthRepository
    private lateinit var notifications: NotificationManager

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        db = Room.inMemoryDatabaseBuilder(context, MediMinderDatabase::class.java).allowMainThreadQueries().build()
        val settings = SettingsRepository(context)
        settings.update { it.copy(repeatReminders = false) }
        engine = ReminderEngine(
            context, db.medicationDao(), db.scheduleDao(), db.doseLogDao(), db.activeAlertDao(), db.appointmentDao(),
            ReminderScheduler(context), Notifier(context), settings,
        )
        medicines = MedicineRepository(db, engine, settings)
        health = HealthRepository(db, DoseActions(db, engine, settings))
        notifications = context.getSystemService(NotificationManager::class.java)
    }

    @After fun tearDown() = db.close()

    private suspend fun add(medication: Medication, alert: AlertType = AlertType.REMINDER): Pair<Schedule, Long> {
        val due = LocalDateTime.now().withSecond(0).withNano(0).minusMinutes(1)
        val id = medicines.save(medication, listOf(Schedule(medicationId = 0, minuteOfDay = minuteOf(due.toLocalTime()), alertType = alert)))
        return db.scheduleDao().forMedication(id).single() to due.toLocalDate().toEpochDay()
    }

    private fun shown(): Notification = shadowOf(notifications).allNotifications.single()
    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT).toString()
    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE).toString()

    @Test fun `eye drops say which eye, and are answered Done`() = runBlocking {
        val (schedule, day) = add(Medication(name = "Timolol", unit = "drop", kind = CareKind.DROPS, site = "eye_left"))
        engine.onDoseAlarm(schedule.id, day, ReminderScheduler.KIND_REGULAR)
        val n = shown()
        assertTrue(n.text(), n.text().startsWith("1 drop in the left eye"))
        assertEquals("Done", n.actions.first().title.toString())
    }

    @Test fun `a rotating injection names this dose's place`() = runBlocking {
        val (schedule, day) = add(Medication(name = "Insulin", unit = "unit", kind = CareKind.INJECTION, site = CareSites.ROTATE))
        engine.onDoseAlarm(schedule.id, day, ReminderScheduler.KIND_REGULAR)
        val place = CareText.injectionSiteName(context, CareSites.injectionSite(day, 0, 1))!!
        assertTrue(shown().text(), shown().text().contains(place))
    }

    @Test fun `a health check opens the app to write the reading down`() = runBlocking {
        val (schedule, day) = add(Medication(name = "BP", kind = CareKind.MEASURE, subtype = MeasureType.BLOOD_PRESSURE.name))
        engine.onDoseAlarm(schedule.id, day, ReminderScheduler.KIND_REGULAR)
        val n = shown()
        assertEquals("Time to check your blood pressure", n.title())
        val record = n.actions.first()
        assertEquals("Write it down", record.title.toString())
        val intent = shadowOf(record.actionIntent)
        assertTrue("opens the app, not a background answer", intent.isActivityIntent)
        assertEquals(Notifier.DEEP_LINK_RECORD + "${schedule.id}/$day", intent.savedIntent.data.toString())

        // writing it down answers the reminder
        health.record(Reading(medicationId = schedule.medicationId, type = MeasureType.BLOOD_PRESSURE.name, value = 128.0, value2 = 82.0), schedule.id, day)
        assertEquals(DoseStatus.TAKEN, db.doseLogDao().forOccurrence(schedule.id, day)!!.status)
        assertTrue("reminder cleared", shadowOf(notifications).allNotifications.isEmpty())
        assertEquals(1, db.readingDao().getAll().size)
    }

    @Test fun `opened too long ago gives a reminder on the day it should be replaced`() = runBlocking {
        val today = LocalDate.now().toEpochDay()
        add(Medication(name = "Eye drops", unit = "drop", kind = CareKind.DROPS, openedAt = today - 28, useWithinDays = 28))
        engine.onRefillCheck()
        val replace = shadowOf(notifications).allNotifications.filter { it.title().startsWith("Time to replace") }
        assertEquals(1, replace.size)
    }

    @Test fun `still fine means no reminder to replace`() = runBlocking {
        val today = LocalDate.now().toEpochDay()
        add(Medication(name = "Cream", unit = "application", kind = CareKind.TOPICAL, openedAt = today - 3, useWithinDays = 30))
        engine.onRefillCheck()
        assertFalse(shadowOf(notifications).allNotifications.any { it.title().startsWith("Time to replace") })
    }
}
