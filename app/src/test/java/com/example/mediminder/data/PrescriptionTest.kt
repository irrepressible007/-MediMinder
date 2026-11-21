package com.example.mediminder.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mediminder.data.catalogue.CatalogueDatabase
import com.example.mediminder.data.catalogue.CatalogueRepository
import com.example.mediminder.data.local.MediMinderDatabase
import com.example.mediminder.data.local.entity.MealTiming
import com.example.mediminder.data.settings.MealTimes
import com.example.mediminder.data.settings.SettingsRepository
import com.example.mediminder.domain.PrescriptionParser
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

/** Reading a prescription photo's text into medicines. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PrescriptionTest {
    private lateinit var catalogueDb: CatalogueDatabase
    private lateinit var db: MediMinderDatabase
    private lateinit var importer: PrescriptionImporter

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(CatalogueDatabase.FILE_NAME)
        catalogueDb = CatalogueDatabase.build(context)
        db = Room.inMemoryDatabaseBuilder(context, MediMinderDatabase::class.java).allowMainThreadQueries().build()
        val settings = SettingsRepository(context)
        settings.update { it.copy(mealTimes = MealTimes(), afterMealMinutes = 15, beforeMealMinutes = 30) }
        val engine = ReminderEngine(
            context, db.medicationDao(), db.scheduleDao(), db.doseLogDao(), db.activeAlertDao(), db.appointmentDao(),
            ReminderScheduler(context), Notifier(context), settings,
        )
        importer = PrescriptionImporter(CatalogueRepository(catalogueDb.catalogueDao()), MedicineRepository(db, engine, settings), settings)
    }

    @After fun tearDown() { catalogueDb.close(); db.close() }

    @Test fun `dose patterns, shorthand, food and course length`() {
        val a = PrescriptionParser.read("1+0+1 after meal x 7 days")
        assertEquals("1+0+1", a.pattern); assertEquals(MealTiming.AFTER_MEAL, a.meal); assertEquals(7, a.days)
        assertEquals("1+1+1", PrescriptionParser.read("l - l - l  p.c.").pattern)
        assertEquals(MealTiming.AFTER_MEAL, PrescriptionParser.read("l - l - l  p.c.").meal)
        assertEquals("½+0+½", PrescriptionParser.read("½+0+½ before food").pattern)
        assertEquals("1+0+1", PrescriptionParser.read("BD for 2 weeks").pattern)
        assertEquals(14, PrescriptionParser.read("BD for 2 weeks").days)
        assertEquals("0+0+1", PrescriptionParser.read("HS").pattern)
        assertEquals(30, PrescriptionParser.read("TDS 1 month").days)
        assertTrue(PrescriptionParser.read("Tab. Ace 1 tab SOS").asNeeded)
        assertNull("a date is not a dose", PrescriptionParser.read("Date: 21-11-2026").pattern)
        assertNull("nor a phone number", PrescriptionParser.read("01711-123-456").pattern)
        assertEquals("tablet", PrescriptionParser.formOf("1. Tab. Napa 500mg"))
        assertEquals(setOf("500"), PrescriptionParser.strengths("Napa 500mg"))
    }

    private val sample = """
        Dr. Karim Ahmed
        MBBS, FCPS (Medicine)
        Rx
        1. Tab. Napa 500mg
           1+0+1  after meal  x 7 days
        2. Cap. Seclo 20 mg  1+0+1  before meal  1 month
        3. Tab. Ace Plus SOS
        Next visit after 2 weeks
    """.trimIndent()

    @Test fun `a printed prescription becomes medicines with their instructions`() = runBlocking {
        val drafts = importer.read(sample)
        val names = drafts.map { it.medicine.brandName.lowercase() }
        assertTrue(names.toString(), names.containsAll(listOf("napa", "seclo", "ace plus")))
        val napa = drafts.first { it.medicine.brandName.equals("Napa", true) }
        assertEquals("500 mg", napa.medicine.strength)
        assertEquals("1+0+1", napa.instructions.pattern)
        assertEquals(MealTiming.AFTER_MEAL, napa.instructions.meal)
        assertEquals(7, napa.instructions.days)
        assertTrue(napa.confident)
        val seclo = drafts.first { it.medicine.brandName.equals("Seclo", true) }
        assertEquals(MealTiming.BEFORE_MEAL, seclo.instructions.meal)
        assertEquals(30, seclo.instructions.days)
        assertTrue(drafts.first { it.medicine.brandName.equals("Ace Plus", true) }.instructions.asNeeded)
    }

    @Test fun `adding them sets times from the meal times and the course end`() = runBlocking {
        val today = LocalDate.of(2026, 11, 21)
        val drafts = importer.read(sample)
        assertEquals(3, importer.add(drafts, today))
        val napa = db.medicationDao().getAll().first { it.name.equals("Napa", true) }
        val times = db.scheduleDao().forMedication(napa.id).map { it.minuteOfDay }.sorted()
        assertEquals(listOf(8 * 60 + 15, 21 * 60 + 15), times)
        assertEquals(today.plusDays(6).toEpochDay(), napa.endDate)
        val ace = db.medicationDao().getAll().first { it.name.equals("Ace Plus", true) }
        assertTrue(ace.isAsNeeded)
        assertFalse(db.scheduleDao().forMedication(ace.id).isNotEmpty())
    }
}
