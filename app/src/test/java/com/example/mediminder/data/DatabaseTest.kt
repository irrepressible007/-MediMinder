package com.example.mediminder.data

import com.example.mediminder.data.local.entity.Reading
import com.example.mediminder.domain.CareKind
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mediminder.data.catalogue.CatalogueDatabase
import com.example.mediminder.data.catalogue.CatalogueRepository
import com.example.mediminder.data.catalogue.CatalogueText
import com.example.mediminder.data.local.ALL_MIGRATIONS
import kotlinx.coroutines.flow.first
import com.example.mediminder.data.local.Schema3
import com.example.mediminder.data.local.MediMinderDatabase
import com.example.mediminder.data.local.entity.AlertType
import com.example.mediminder.data.local.entity.DaysOfWeek
import com.example.mediminder.data.local.entity.DoseStatus
import com.example.mediminder.data.local.entity.MealTiming
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The bundled medicine catalogue, opened exactly as the app opens it. */
@RunWith(AndroidJUnit4::class)
class CatalogueTest {
    private lateinit var db: CatalogueDatabase
    private lateinit var repo: CatalogueRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(CatalogueDatabase.FILE_NAME)
        db = CatalogueDatabase.build(context)
        repo = CatalogueRepository(db.catalogueDao())
    }

    @After fun tearDown() = db.close()

    @Test fun `bundled catalogue opens and holds the Bangladeshi medicine list`() = runBlocking {
        assertTrue(db.catalogueDao().count() > 50_000)
    }

    @Test fun `exact brand comes first`() = runBlocking {
        val results = repo.search("Napa")
        // The everyday product first: Napa 500 mg tablet, not the IV infusion
        assertEquals("napa", results.first().brandSearch)
        assertEquals("500 mg", results.first().strength)
        assertEquals("Tablet", results.first().dosageForm)
    }

    @Test fun `search by generic finds brands`() = runBlocking {
        val results = repo.search("omeprazole")
        assertTrue(results.size >= 20)
        assertTrue(results.all { CatalogueText.searchKey(it.genericName).contains("omeprazole") || it.brandSearch.contains("omeprazole") })
        // and the brand itself is found by its own name
        assertTrue(repo.search("seclo").first().brandSearch == "seclo")
    }

    @Test fun `typing with spaces or capitals still matches`() = runBlocking {
        assertTrue(repo.search("NAPA  extra").any { it.brandSearch == "napaextra" })
        assertTrue(repo.search("n").isEmpty())
    }

    @Test fun `text read off a box finds the brand`() = runBlocking {
        val found = repo.matchScannedText("NAPA EXTRA\nParacetamol 500 mg + Caffeine 65 mg\nBeximco Pharmaceuticals Ltd")
        assertEquals("napaextra", found.first().brandSearch)
    }

    @Test fun `alternatives share generic and strength`() = runBlocking {
        val alternatives = repo.alternatives("Paracetamol", "500 mg")
        assertTrue(alternatives.size >= 10)
        assertTrue(alternatives.all { CatalogueText.strengthKey(it.strength) == "500mg" })
        // cheapest known price first
        val prices = alternatives.mapNotNull { it.unitPrice }
        assertEquals(prices.sorted(), prices)
    }

    @Test fun `traditional medicines are included`() = runBlocking {
        val categories = setOf("ayurvedic", "unani", "herbal", "homeopathic")
        assertTrue(repo.search("sharbat").any { it.category in categories })
    }
}

/** Upgrading from 1.x keeps the user's medicines. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val name = "migration-test.db"
    private lateinit var context: Context

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(name)
    }

    @After fun tearDown() { context.deleteDatabase(name) }

    @Test fun `version 2 data (1_0_10) survives the upgrade`() = upgradesFrom(2)

    @Test fun `version 3 data (2_0_0) survives the upgrade to 4`() = runBlocking {
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            (listOf(Schema3.MEDICATIONS, Schema3.SCHEDULES, Schema3.SCHEDULES_INDEX, Schema3.DOSE_LOGS) + Schema3.DOSE_LOGS_INDEXES +
                listOf(Schema3.RESTOCKS, Schema3.RESTOCKS_INDEX, Schema3.DOCTORS, Schema3.APPOINTMENTS) + Schema3.APPOINTMENTS_INDEXES +
                listOf(Schema3.ACTIVE_ALERTS, Schema3.ACTIVE_ALERTS_INDEX, Schema3.VAULT_ITEMS)).forEach(old::execSQL)
            old.execSQL(
                "INSERT INTO medications (id, name, strength, form, shape, color, mealTiming, isAsNeeded, trackStock, stock, unit, " +
                    "refillThresholdDays, refillReminders, archived, createdAt, updatedAt) " +
                    "VALUES (7, 'Seclo', '20 mg', 'Capsule', 'Capsule', '#3B82F6', 'BEFORE_MEAL', 0, 1, 14, 'capsule', 5, 1, 0, 0, 0)"
            )
            old.version = 3
        }
        val db = Room.databaseBuilder(context, MediMinderDatabase::class.java, name)
            .addMigrations(*ALL_MIGRATIONS).allowMainThreadQueries().build()
        try {
            val seclo = db.medicationDao().getAll().single()
            assertEquals("Seclo", seclo.name)
            assertNull("not deleted", seclo.deletedAt)
            assertEquals(1, db.medicationDao().observeAllWithSchedules().first().size)
        } finally {
            db.close()
        }
    }

    @Test fun `upgrading to 5 gives each medicine its kind from its unit, and adds readings`() = runBlocking {
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            (listOf(Schema3.MEDICATIONS, Schema3.SCHEDULES, Schema3.SCHEDULES_INDEX, Schema3.DOSE_LOGS) + Schema3.DOSE_LOGS_INDEXES +
                listOf(Schema3.RESTOCKS, Schema3.RESTOCKS_INDEX, Schema3.DOCTORS, Schema3.APPOINTMENTS) + Schema3.APPOINTMENTS_INDEXES +
                listOf(Schema3.ACTIVE_ALERTS, Schema3.ACTIVE_ALERTS_INDEX, Schema3.VAULT_ITEMS)).forEach(old::execSQL)
            old.execSQL("ALTER TABLE medications ADD COLUMN deletedAt INTEGER")   // what 3 → 4 added
            listOf(1 to "tablet", 2 to "drop", 3 to "application", 4 to "puff", 5 to "ml").forEach { (id, unit) ->
                old.execSQL(
                    "INSERT INTO medications (id, name, strength, form, shape, color, mealTiming, isAsNeeded, trackStock, stock, unit, " +
                        "refillThresholdDays, refillReminders, archived, createdAt, updatedAt) " +
                        "VALUES ($id, 'M$id', '', 'x', 'Round', '#3B82F6', 'ANY', 0, 0, 0, '$unit', 5, 1, 0, 0, 0)"
                )
            }
            old.version = 4
        }
        val db = Room.databaseBuilder(context, MediMinderDatabase::class.java, name)
            .addMigrations(*ALL_MIGRATIONS).allowMainThreadQueries().build()
        try {
            val kinds = db.medicationDao().getAll().associate { it.id to it.kind }
            assertEquals(CareKind.PILL, kinds[1])
            assertEquals(CareKind.DROPS, kinds[2])
            assertEquals(CareKind.TOPICAL, kinds[3])
            assertEquals(CareKind.INHALED, kinds[4])
            assertEquals(CareKind.LIQUID, kinds[5])
            db.readingDao().insert(Reading(type = "WEIGHT", value = 64.5))
            assertEquals(64.5, db.readingDao().getAll().single().value, 0.0)
        } finally {
            db.close()
        }
    }

    @Test fun `version 1 data (1_0_1 to 1_0_9) survives the upgrade`() = upgradesFrom(1)

    @Test fun `an unreadable early schema starts fresh instead of crashing`() = runBlocking {
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            // a medications table without the columns 1.x had
            old.execSQL("CREATE TABLE medications (id INTEGER PRIMARY KEY, title TEXT)")
            old.execSQL("INSERT INTO medications VALUES (1, 'x')")
            old.version = 1
        }
        val db = Room.databaseBuilder(context, MediMinderDatabase::class.java, name)
            .addMigrations(*ALL_MIGRATIONS).allowMainThreadQueries().build()
        try {
            assertTrue(db.medicationDao().getAll().isEmpty())
            assertTrue(db.vaultItemDao().getAll().isEmpty())
        } finally {
            db.close()
        }
    }

    private fun upgradesFrom(version: Int) = runBlocking {
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            // The 1.x schema exactly as Room created it
            old.execSQL("CREATE TABLE IF NOT EXISTS `medications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `dosage` TEXT NOT NULL, `shape` TEXT NOT NULL, `color` TEXT NOT NULL, `photoUri` TEXT, `inventoryCount` INTEGER NOT NULL, `isAsNeeded` INTEGER NOT NULL, `maxDailyDoses` INTEGER)")
            old.execSQL("CREATE TABLE IF NOT EXISTS `schedules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `medicationId` INTEGER NOT NULL, `timeOfDayMillis` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, FOREIGN KEY(`medicationId`) REFERENCES `medications`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            // schema 2 only added these two indexes
            if (version >= 2) old.execSQL("CREATE INDEX IF NOT EXISTS `index_schedules_medicationId` ON `schedules` (`medicationId`)")
            old.execSQL("CREATE TABLE IF NOT EXISTS `logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `medicationId` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `status` TEXT NOT NULL, `symptoms` TEXT, FOREIGN KEY(`medicationId`) REFERENCES `medications`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            if (version >= 2) old.execSQL("CREATE INDEX IF NOT EXISTS `index_logs_medicationId` ON `logs` (`medicationId`)")
            old.execSQL("CREATE TABLE IF NOT EXISTS `medicine_dictionary` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `brand_id` TEXT, `brand_name` TEXT, `type` TEXT, `slug` TEXT, `dosage_form` TEXT, `generic_name` TEXT, `strength` TEXT, `manufacturer` TEXT, `package_container` TEXT)")
            old.execSQL("CREATE TABLE IF NOT EXISTS `vault_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `dateAdded` INTEGER NOT NULL, `localImagePath` TEXT NOT NULL)")
            old.execSQL("PRAGMA foreign_keys = ON")

            old.execSQL("INSERT INTO medications VALUES (1, 'Napa', '500 mg', 'Round', '#10B981', NULL, 18, 0, NULL)")
            old.execSQL("INSERT INTO medications VALUES (2, 'Seclo', '20 mg', 'Capsule', '#3B82F6', NULL, 0, 0, NULL)")
            old.execSQL("INSERT INTO medications VALUES (3, 'Ace Syrup', '120 mg/5 ml', 'Square', '#EF4444', NULL, 0, 1, 4)")
            old.execSQL("INSERT INTO schedules VALUES (1, 1, ${8 * 3_600_000L}, 1)")
            old.execSQL("INSERT INTO schedules VALUES (2, 1, ${(21 * 60 + 30) * 60_000L}, 1)")
            old.execSQL("INSERT INTO schedules VALUES (3, 2, ${7 * 3_600_000L}, 0)")
            old.execSQL("INSERT INTO logs VALUES (1, 1, 1767225600000, 'TAKEN', 'felt better')")
            old.execSQL("INSERT INTO logs VALUES (2, 1, 1767312000000, 'MISSED', NULL)")
            old.execSQL("INSERT INTO medicine_dictionary (brand_name) VALUES ('Napa')")
            old.execSQL("INSERT INTO vault_items VALUES (1, 'Prescription', 1767225600000, '/data/x.jpg')")
            old.version = version
        }

        // Opening with Room runs the migration and then validates the whole schema
        val db = Room.databaseBuilder(context, MediMinderDatabase::class.java, name)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val meds = db.medicationDao().getAll().associateBy { it.id }
            assertEquals(3, meds.size)
            val napa = meds.getValue(1)
            assertEquals("500 mg", napa.strength)
            assertTrue(napa.trackStock)
            assertEquals(18.0, napa.stock, 0.0)
            assertEquals("tablet", napa.unit)
            assertEquals(MealTiming.ANY, napa.mealTiming)
            assertFalse(meds.getValue(2).trackStock)
            assertEquals("capsule", meds.getValue(2).unit)
            assertTrue(meds.getValue(3).isAsNeeded)
            assertEquals(4, meds.getValue(3).maxDailyDoses)

            val schedules = db.scheduleDao().getAll().associateBy { it.id }
            assertEquals(8 * 60, schedules.getValue(1).minuteOfDay)
            assertEquals(21 * 60 + 30, schedules.getValue(2).minuteOfDay)
            assertFalse(schedules.getValue(3).enabled)
            assertEquals(DaysOfWeek.EVERY_DAY, schedules.getValue(1).daysMask)
            assertEquals(AlertType.REMINDER, schedules.getValue(1).alertType)

            // only "taken" history is carried over; a missing log already means missed
            val logs = db.doseLogDao().getAll()
            assertEquals(1, logs.size)
            assertEquals(DoseStatus.TAKEN, logs.single().status)
            assertEquals("felt better", logs.single().note)
            assertNull(logs.single().scheduleId)

            assertEquals(1, db.vaultItemDao().getAll().size)
            // New tables work
            assertTrue(db.appointmentDao().getAll().isEmpty())
            assertTrue(db.activeAlertDao().getAll().isEmpty())
        } finally {
            db.close()
        }
    }
}
