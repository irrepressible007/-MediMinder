package com.example.mediminder.ui

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
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
    @get:Rule(order = 1) val calm = CalmAnimationsRule()
    @get:Rule(order = 2) val compose = createAndroidComposeRule<MainActivity>()

    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)
    private fun waitForText(value: String) = compose.waitForText(value)

    @Before fun setUp() {
        hilt.inject()
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test fun `first run, add a medicine with the wizard, visit every screen`() {
        // ── Onboarding: language & text size → meal times → permissions ──
        waitForText(text(R.string.onboarding_welcome))
        compose.onNodeWithText(text(R.string.text_size_large)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_next)).performClick()
        // builds with sign-in ask for it next; skipping is always possible
        if (com.example.mediminder.cloud.CloudConfig.available) {
            waitForText(text(R.string.onboarding_account_title))
            compose.onNodeWithText(text(R.string.account_google)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.onboarding_skip)).performClick()
        }
        waitForText(text(R.string.onboarding_meals_title))
        compose.onNodeWithText(text(R.string.action_next)).performClick()
        waitForText(text(R.string.onboarding_permissions_title))
        compose.onNodeWithText(text(R.string.action_get_started)).performClick()

        // ── Today, empty ──
        waitForText(text(R.string.today_empty_title))
        compose.onNodeWithText(text(R.string.action_add_medicine)).performClick()

        // ── Wizard 1: which medicine ──
        waitForText(text(R.string.wiz_name_title))
        compose.onNode(hasSetTextAction()).performTextInput("Napa")
        waitForText("Beximco")
        // a result row (not the search box, which also says "Napa")
        compose.onAllNodes(hasText("Napa", substring = false) and !hasSetTextAction()).onFirst().performClick()
        waitForText(text(R.string.wiz_confirm_title))
        compose.onNodeWithText(text(R.string.action_next)).performClick()

        // ── Wizard 2: when — "morning and night, after food" is the default ──
        waitForText(text(R.string.wiz_when_title))
        compose.onNodeWithText(text(R.string.pattern_morning_night)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_next)).performClick()

        // ── Wizard 3: the times, from the meal times ──
        waitForText(text(R.string.wiz_times_title))
        waitForText("8:15 AM")
        waitForText("9:15 PM")
        compose.onNodeWithText(text(R.string.action_next)).performClick()

        // ── Wizard 4: optional extras, then save ──
        waitForText(text(R.string.wiz_finish_title))
        compose.onNodeWithText(text(R.string.action_save_medicine)).performClick()

        // ── Saved: detail screen in plain words ──
        waitForText(text(R.string.pattern_morning_night) + " · " + text(R.string.meal_after))
        compose.onNodeWithText(text(R.string.detail_start_counting)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.cd_back)).performClick()

        // ── Today: how many of the two doses show depends on the time of day (a dose whose time
        //    had passed before the medicine was added isn't listed), so only the screen is checked ──
        waitForText(text(R.string.tab_today))

        // ── Every other tab and screen opens ──
        compose.onNodeWithText(text(R.string.tab_medicines)).performClick()
        waitForText(text(R.string.medicines_title))
        compose.onNodeWithText("Napa 500 mg").assertIsDisplayed()

        compose.onNodeWithText(text(R.string.tab_appointments)).performClick()
        waitForText(text(R.string.appt_title))
        compose.onNodeWithText(text(R.string.action_new_appointment)).performClick()
        waitForText(text(R.string.appt_which_doctor))
        // task flows leave through a labelled Cancel (nothing entered, so no "discard?" question)
        compose.onNodeWithText(text(R.string.action_cancel)).performClick()

        compose.onNodeWithText(text(R.string.tab_more)).performClick()
        for ((entry, screenText) in listOf(
            R.string.history_title to R.string.history_intro,
            R.string.refill_title to R.string.refill_none_tracked_title,
            R.string.stats_title to R.string.stats_rate_label,
            R.string.medical_id_title to R.string.medical_id_blood,
            R.string.vault_title to R.string.vault_empty,
            R.string.reliability_title to R.string.reliability_intro,
            R.string.settings_title to R.string.settings_text_size,
        )) {
            waitForText(text(entry))
            compose.onAllNodesWithText(text(entry)).onFirst().performScrollTo().performClick()
            waitForText(text(screenText))
            compose.onNodeWithContentDescription(text(R.string.cd_back)).performClick()
            waitForText(text(R.string.tab_more))
        }
    }
}
