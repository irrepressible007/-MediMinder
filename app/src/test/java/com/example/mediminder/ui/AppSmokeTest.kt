package com.example.mediminder.ui

import android.Manifest
import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.mediminder.MainActivity
import com.example.mediminder.R
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * Boots the real app (Hilt graph, databases, navigation, every screen) on Robolectric and
 * walks through the main journey. Catches runtime crashes the compiler cannot.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
// A common Bangladeshi phone size (e.g. Redmi / Samsung A series)
@Config(application = HiltTestApplication::class, sdk = [34], qualifiers = "w393dp-h873dp-xxhdpi")
class AppSmokeTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    @Before fun setUp() {
        hilt.inject()
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * Waits for [value] to appear. Robolectric's main looper is paused, so delayed work
     * (the search debounce, coroutine delays) only runs when its clock is moved forward.
     */
    private fun waitForText(value: String, timeoutMs: Long = 15_000) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            compose.waitForIdle()
            if (compose.onAllNodesWithText(value, substring = true).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(25) // let database work on background threads finish
        }
        throw AssertionError("Never showed: \"$value\". On screen:\n" + compose.onRoot().printToString(maxDepth = 30))
    }

    @Test fun `first run, add a medicine from the catalogue, visit every screen`() {
        // ── Onboarding ──
        waitForText(text(R.string.onboarding_welcome))
        compose.onNodeWithText(text(R.string.action_next)).performClick()
        compose.onNodeWithText(text(R.string.onboarding_meals_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_next)).performClick()
        compose.onNodeWithText(text(R.string.onboarding_permissions_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_get_started)).performClick()

        // ── Today, empty ──
        waitForText(text(R.string.today_empty_title))
        compose.onAllNodesWithText(text(R.string.action_add_medicine)).onFirst().performClick()

        // ── Add medicine: search the catalogue and pick Napa ──
        waitForText(text(R.string.editor_title_new))
        compose.onNode(hasSetTextAction()).performTextInput("Napa")
        waitForText("Beximco")
        // a result row (not the search box, which also says "Napa")
        compose.onAllNodes(hasText("Napa", substring = false) and !hasSetTextAction()).onFirst().performClick()
        waitForText(text(R.string.editor_from_catalogue))
        compose.onNodeWithText(text(R.string.editor_section_schedule)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_save_medicine)).performClick()

        // ── Saved: detail screen shows the prescription pattern and food timing ──
        waitForText("1+0+1 · " + text(R.string.meal_after))
        compose.onNodeWithText(text(R.string.action_restock)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.cd_back)).performClick()

        // ── Today now lists the doses ──
        waitForText(text(R.string.today_taken_label))
        compose.onAllNodesWithText("Napa", substring = true).onFirst().assertIsDisplayed()

        // ── Every other tab and screen opens ──
        compose.onNodeWithText(text(R.string.tab_medicines)).performClick()
        waitForText(text(R.string.action_add_medicine))
        compose.onNodeWithText("Napa 500 mg").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.tab_appointments)).performClick()
        waitForText(text(R.string.doctors_title))
        compose.onNodeWithText(text(R.string.action_new_appointment)).performClick()
        waitForText(text(R.string.appt_section_doctor))
        compose.onNodeWithContentDescription(text(R.string.cd_back)).performClick()

        compose.onNodeWithText(text(R.string.tab_more)).performClick()
        for ((entry, screenText) in listOf(
            R.string.refill_title to R.string.refill_title,
            R.string.stats_title to R.string.stats_rate_label,
            R.string.medical_id_title to R.string.medical_id_blood,
            R.string.vault_title to R.string.vault_empty,
            R.string.reliability_title to R.string.reliability_intro,
            R.string.settings_title to R.string.settings_meal_times,
        )) {
            waitForText(text(entry))
            compose.onAllNodesWithText(text(entry)).onFirst().performClick()
            waitForText(text(screenText))
            compose.onNodeWithContentDescription(text(R.string.cd_back)).performClick()
            waitForText(text(R.string.tab_more))
        }
    }
}
