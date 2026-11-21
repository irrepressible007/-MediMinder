package com.example.mediminder.reminders

import android.Manifest
import android.app.Application
import android.content.Context
import android.telephony.SmsManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mediminder.data.MedicineRepository
import com.example.mediminder.data.local.MediMinderDatabase
import com.example.mediminder.data.local.entity.AlertType
import com.example.mediminder.data.local.entity.Medication
import com.example.mediminder.data.local.entity.Schedule
import com.example.mediminder.data.settings.FamilyAlerts
import com.example.mediminder.data.settings.SettingsRepository
import com.example.mediminder.domain.CareKind
import com.example.mediminder.domain.minuteOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.LocalDateTime

/** A text to the family when a medicine is still not taken. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class FamilyAlertTest {
    private lateinit var context: Context
    private lateinit var db: MediMinderDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var engine: ReminderEngine
    private lateinit var medicines: MedicineRepository
    private lateinit var actions: DoseActions

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.SEND_SMS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        context.getSharedPreferences("family_alerts", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, MediMinderDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsRepository(context)
        settings.update { it.copy(userName = "Rahima", repeatReminders = false, family = FamilyAlerts(on = true, name = "Karim", phone = "01711000000", afterMinutes = 60)) }
        engine = ReminderEngine(
            context, db.medicationDao(), db.scheduleDao(), db.doseLogDao(), db.activeAlertDao(), db.appointmentDao(),
            ReminderScheduler(context), Notifier(context), settings,
        )
        medicines = MedicineRepository(db, engine, settings)
        actions = DoseActions(db, engine, settings)
    }

    @After fun tearDown() = db.close()

    private fun sms() = shadowOf(context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault())

    /** A medicine whose dose was [minutesAgo] minutes ago. */
    private suspend fun dose(name: String, minutesAgo: Long, kind: CareKind = CareKind.PILL): Pair<Schedule, Long> {
        val due = LocalDateTime.now().withSecond(0).withNano(0).minusMinutes(minutesAgo)
        val id = medicines.save(Medication(name = name, kind = kind), listOf(Schedule(medicationId = 0, minuteOfDay = minuteOf(due.toLocalTime()), alertType = AlertType.REMINDER)))
        return db.scheduleDao().forMedication(id).single() to due.toLocalDate().toEpochDay()
    }

    @Test fun `a dose that rings arms a check for later`() = runBlocking {
        val (schedule, day) = dose("Napa", minutesAgo = 1)
        engine.onDoseAlarm(schedule.id, day, ReminderScheduler.KIND_REGULAR)
        val alarms = shadowOf(context.getSystemService(android.app.AlarmManager::class.java)).scheduledAlarms
        assertTrue(alarms.any { a -> a.operation?.let { shadowOf(it).savedIntent.data.toString() } == "mediminder://missed/${schedule.id}/$day" })
    }

    @Test fun `still not taken, so one text lists every missed medicine`() = runBlocking {
        val (napa, day) = dose("Napa", minutesAgo = 70)
        dose("Seclo", minutesAgo = 65)
        engine.onMissedCheck(napa.id, day)
        val sent = sms().lastSentMultipartTextMessageParams
        assertNotNull(sent)
        assertEquals("01711000000", sent.destinationAddress)
        val text = sent.parts.joinToString("")
        assertTrue(text, text.contains("Rahima") && text.contains("Napa") && text.contains("Seclo"))

        // the same doses are never reported twice
        sms().clearLastSentMultipartTextMessageParams()
        engine.onMissedCheck(napa.id, day)
        assertNull(sms().lastSentMultipartTextMessageParams)
    }

    @Test fun `taken in time means no text`() = runBlocking {
        val (napa, day) = dose("Napa", minutesAgo = 70)
        actions.take(napa.id, day)
        engine.onMissedCheck(napa.id, day)
        assertNull(sms().lastSentMultipartTextMessageParams)
    }

    @Test fun `health checks and habits are not reported, and nothing goes when it is off`() = runBlocking {
        val (walk, day) = dose("Walk", minutesAgo = 70, kind = CareKind.HABIT)
        engine.onMissedCheck(walk.id, day)
        assertNull(sms().lastSentMultipartTextMessageParams)

        settings.update { it.copy(family = it.family.copy(on = false)) }
        val (napa, d2) = dose("Napa", minutesAgo = 70)
        engine.onMissedCheck(napa.id, d2)
        assertNull(sms().lastSentMultipartTextMessageParams)
    }

    @Test fun `at most five texts a day`() = runBlocking {
        val alerter = FamilyAlerter(context, settings)
        repeat(7) { i ->
            alerter.sendMissed(listOf(FamilyAlerter.Missed(FamilyAlerter.key(100L + i, 1), "M$i", 8 * 60)))
        }
        assertEquals(FamilyAlerter.MAX_PER_DAY, alerter.sentToday())
    }

    @Test fun `two checks at the same moment send one text`() = runBlocking {
        val (napa, day) = dose("Napa", minutesAgo = 70)
        val (seclo, _) = dose("Seclo", minutesAgo = 70)
        val sent = mutableListOf<String>()
        coroutineScope {
            listOf(napa.id, seclo.id).map { id -> async(Dispatchers.Default) { engine.onMissedCheck(id, day) } }.forEach { it.await() }
        }
        sms().lastSentMultipartTextMessageParams?.parts?.joinToString("")?.let(sent::add)
        assertEquals(1, FamilyAlerter(context, settings).sentToday())
        assertTrue(sent.single().contains("Napa") && sent.single().contains("Seclo"))
    }

    @Test fun `Bangla digits in the number are sent as plain digits`() {
        assertEquals("01711000000", FamilyAlerter.dialable("০১৭১১ ০০০০০০"))
        assertEquals("+8801711000000", FamilyAlerter.dialable("+880 1711-000000"))
    }
}
