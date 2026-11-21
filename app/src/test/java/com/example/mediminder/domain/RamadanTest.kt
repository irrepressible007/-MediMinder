package com.example.mediminder.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mediminder.data.MedicineRepository
import com.example.mediminder.data.RamadanManager
import com.example.mediminder.data.local.MediMinderDatabase
import com.example.mediminder.data.local.entity.DoseSlot
import com.example.mediminder.data.local.entity.MealTiming
import com.example.mediminder.data.local.entity.Medication
import com.example.mediminder.data.local.entity.Schedule
import com.example.mediminder.data.settings.MealTimes
import com.example.mediminder.data.settings.RamadanSettings
import com.example.mediminder.data.settings.SettingsRepository
import com.example.mediminder.reminders.Notifier
import com.example.mediminder.reminders.ReminderEngine
import com.example.mediminder.reminders.ReminderScheduler
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Ramadan mode: when it is, where meal-based doses go, and coming back afterwards. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RamadanTest {
    private lateinit var db: MediMinderDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var medicines: MedicineRepository
    private lateinit var ramadan: RamadanManager

    @Before fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MediMinderDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsRepository(context)
        val engine = ReminderEngine(
            context, db.medicationDao(), db.scheduleDao(), db.doseLogDao(), db.activeAlertDao(), db.appointmentDao(),
            ReminderScheduler(context), Notifier(context), settings,
        )
        medicines = MedicineRepository(db, engine, settings)
        ramadan = RamadanManager(settings, medicines, Notifier(context))
    }

    @After fun tearDown() = db.close()

    @Test fun `the next Ramadan, and when to suggest it`() {
        assertEquals(LocalDate.of(2027, 2, 9), Ramadan.estimatedStart(LocalDate.of(2026, 11, 21)))
        assertFalse(Ramadan.isNear(LocalDate.of(2027, 1, 29)))
        assertTrue("ten days before", Ramadan.isNear(LocalDate.of(2027, 1, 30)))
        assertTrue("during", Ramadan.isNear(LocalDate.of(2027, 3, 1)))
        assertEquals("still this year's on its last day", LocalDate.of(2027, 2, 9), Ramadan.estimatedStart(LocalDate.of(2027, 3, 10)))
        assertEquals("then next year's", LocalDate.of(2028, 1, 29), Ramadan.estimatedStart(LocalDate.of(2027, 3, 11)))
    }

    @Test fun `with meals, doses go to Sehri and Iftar`() {
        val r = RamadanSettings(sehri = 4 * 60 + 30, iftar = 18 * 60, lateNight = 22 * 60)
        val plan = MealSchedule.plan(DosePattern.parse("1+0+1")!!, MealTiming.AFTER_MEAL, Ramadan.meals(r), 30, 15)
        assertEquals(listOf(4 * 60 + 45, 18 * 60 + 15), plan.map { it.minuteOfDay })
        val three = MealSchedule.plan(DosePattern.parse("1+1+1")!!, MealTiming.AFTER_MEAL, Ramadan.meals(r), 30, 15)
        assertEquals("Sehri, Iftar, after Taraweeh", listOf(4 * 60 + 45, 18 * 60 + 15, 22 * 60 + 15), three.map { it.minuteOfDay })
        assertTrue(Ramadan.duringFast(13 * 60, r))
        assertFalse("still Sehri", Ramadan.duringFast(4 * 60 + 45, r))
        assertFalse("after Iftar", Ramadan.duringFast(19 * 60, r))
    }

    @Test fun `turning it on moves meal-based doses, and off puts them back`() = runBlocking {
        settings.update { it.copy(mealTimes = MealTimes(), afterMealMinutes = 15, ramadan = RamadanSettings()) }
        val id = medicines.save(
            Medication(name = "Napa", mealTiming = MealTiming.AFTER_MEAL, dosePattern = "1+0+1"),
            listOf(
                Schedule(medicationId = 0, minuteOfDay = 8 * 60 + 15, slot = DoseSlot.MORNING),
                Schedule(medicationId = 0, minuteOfDay = 21 * 60 + 15, slot = DoseSlot.NIGHT),
                // set by hand: stays where it is
                Schedule(medicationId = 0, minuteOfDay = 14 * 60),
            ),
        )
        fun times() = runBlocking { db.scheduleDao().forMedication(id).map { it.minuteOfDay }.sorted() }

        ramadan.turnOn(LocalDate.now().plusDays(29))
        assertTrue(settings.current.ramadan.on)
        assertEquals(listOf(4 * 60 + 45, 14 * 60, 18 * 60 + 15), times())

        ramadan.setTimes(iftar = 17 * 60 + 50)
        assertEquals(listOf(4 * 60 + 45, 14 * 60, 18 * 60 + 5), times())

        ramadan.turnOff()
        assertFalse(settings.current.ramadan.on)
        assertEquals(MealTimes(), settings.current.mealTimes)
        assertEquals(listOf(8 * 60 + 15, 14 * 60, 21 * 60 + 15), times())
    }

    @Test fun `it ends by itself the day after the last day`() = runBlocking {
        settings.update { it.copy(mealTimes = MealTimes(), ramadan = RamadanSettings()) }
        val last = LocalDate.of(2027, 3, 10)
        ramadan.turnOn(last)
        assertFalse(ramadan.endIfOver(last))
        assertTrue(settings.current.ramadan.on)
        assertTrue(ramadan.endIfOver(last.plusDays(1)))
        assertFalse(settings.current.ramadan.on)
        assertNull(settings.current.ramadan.usualMeals)
        assertEquals(MealTimes(), settings.current.mealTimes)
    }

    @Test fun `put away until next year`() {
        settings.update { it.copy(ramadan = RamadanSettings()) }
        val near = LocalDate.of(2027, 2, 1)
        assertEquals(LocalDate.of(2027, 2, 9), ramadan.suggestion(near))
        ramadan.dismissSuggestion(2027)
        assertNull(ramadan.suggestion(near))
        assertEquals(LocalDate.of(2028, 1, 29), ramadan.suggestion(LocalDate.of(2028, 1, 25)))
    }
}
