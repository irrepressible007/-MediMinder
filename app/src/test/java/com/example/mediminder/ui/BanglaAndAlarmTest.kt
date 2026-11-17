package com.example.mediminder.ui

import android.Manifest
import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.mediminder.MainActivity
import com.example.mediminder.data.MedicineRepository
import com.example.mediminder.data.local.entity.AlertType
import com.example.mediminder.data.local.entity.Medication
import com.example.mediminder.data.local.entity.Schedule
import com.example.mediminder.data.local.MediMinderDatabase
import com.example.mediminder.domain.minuteOf
import com.example.mediminder.reminders.AlarmActivity
import com.example.mediminder.reminders.ReminderEngine
import com.example.mediminder.reminders.ReminderScheduler
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

/** Waits for text, moving Robolectric's paused main-looper clock forward as it goes. */
internal fun ComposeTestRule.waitForText(value: String, timeoutMs: Long = 15_000) {
    val end = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < end) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        waitForIdle()
        if (onAllNodesWithText(value, substring = true).fetchSemanticsNodes().isNotEmpty()) return
        Thread.sleep(25)
    }
    throw AssertionError("Never showed: \"$value\". On screen:\n" + onRoot().printToString(maxDepth = 30))
}

/** The whole first-run journey in Bangla: Bangla text, Bangla digits, "সকাল ৮:১৫". */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [34], qualifiers = "bn-w393dp-h873dp-xxhdpi")
class BanglaTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun `app works in Bangla`() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        compose.waitForText("MediMinder-এ স্বাগতম")
        compose.onNodeWithText("পরবর্তী").performClick()
        compose.waitForText("আপনি কখন খান?")
        compose.onNodeWithText("পরবর্তী").performClick()
        compose.onNodeWithText("শুরু করুন").performClick()

        compose.waitForText("এখনো কোনো ওষুধ নেই")
        compose.onAllNodesWithText("ওষুধ যোগ করুন").onFirst().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Napa")
        compose.waitForText("Beximco")
        compose.onAllNodes(hasText("Napa", substring = false) and !hasSetTextAction()).onFirst().performClick()
        // default pattern ১+০+১ after meal → 8:15 AM and 9:15 PM, written the Bangladeshi way
        compose.waitForText("সকাল ৮:১৫")
        compose.waitForText("রাত ৯:১৫")
        compose.onNodeWithText("ওষুধ সংরক্ষণ").performClick()
        compose.waitForText("১+০+১ · খাবারের পরে")
    }
}

/** The full-screen alarm lists what is ringing and closes once it is answered. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [34], qualifiers = "w393dp-h873dp-xxhdpi")
class AlarmScreenTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()

    @Inject lateinit var medicines: MedicineRepository
    @Inject lateinit var engine: ReminderEngine
    @Inject lateinit var db: MediMinderDatabase

    @Test fun `alarm page shows the dose and Taken closes it`() {
        hilt.inject()
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val due = LocalDateTime.now().withSecond(0).withNano(0).minusMinutes(1)
        val scheduleId = runBlocking {
            val id = medicines.save(
                Medication(name = "Seclo", strength = "20 mg", trackStock = true, stock = 14.0, unit = "capsule"),
                listOf(Schedule(medicationId = 0, minuteOfDay = minuteOf(due.toLocalTime()), alertType = AlertType.ALARM)),
            )
            val schedule = db.scheduleDao().forMedication(id).single()
            engine.onDoseAlarm(schedule.id, due.toLocalDate().toEpochDay(), ReminderScheduler.KIND_REGULAR)
            schedule.id
        }

        ActivityScenario.launch(AlarmActivity::class.java).use { scenario ->
            compose.waitForText("Seclo 20 mg")
            compose.waitForText("1 capsule")
            compose.onAllNodesWithText("Taken").onFirst().performClick()

            // answered → stock used, nothing ringing, the alarm page finishes
            var finishing = false
            val end = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < end && !finishing) {
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
                compose.waitForIdle()
                scenario.onActivity { finishing = it.isFinishing || it.isDestroyed }
                Thread.sleep(25)
            }
            assertTrue("alarm page closed itself", finishing)
            assertTrue(runBlocking { db.activeAlertDao().getAll() }.isEmpty())
            assertEquals(13.0, runBlocking { db.medicationDao().getAll().single().stock }, 0.0)
        }
        assertTrue(scheduleId > 0)
    }
}
