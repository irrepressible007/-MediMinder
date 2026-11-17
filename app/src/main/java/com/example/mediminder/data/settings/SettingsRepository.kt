package com.example.mediminder.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.example.mediminder.data.local.entity.AlertType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Daily meal times; prescription patterns like "1+0+1, after meal" are anchored to these. */
@kotlinx.serialization.Serializable
data class MealTimes(
    val breakfast: Int = 8 * 60,        // 8:00 AM
    val lunch: Int = 13 * 60 + 30,      // 1:30 PM
    val evening: Int = 17 * 60 + 30,    // 5:30 PM (bikel / evening snack)
    val dinner: Int = 21 * 60,          // 9:00 PM
    val bedtime: Int = 22 * 60 + 30,    // 10:30 PM
)

/** Health details shown on the Medical ID screen and used for allergy warnings. */
@kotlinx.serialization.Serializable
data class MedicalProfile(
    val bloodGroup: String = "",
    val allergies: String = "",
    val conditions: String = "",
    val emergencyName: String = "",
    val emergencyPhone: String = "",
    val birthYear: Int? = null,
)

@kotlinx.serialization.Serializable
data class AppSettings(
    val onboardingDone: Boolean = false,
    /** "" = follow the phone, "bn" or "en" */
    val language: String = "",
    val userName: String = "",
    val mealTimes: MealTimes = MealTimes(),
    /** "Before meal" = this many minutes before the meal. */
    val beforeMealMinutes: Int = 30,
    /** "After meal" = this many minutes after the meal starts. */
    val afterMealMinutes: Int = 15,
    val defaultAlertType: AlertType = AlertType.REMINDER,
    val snoozeMinutes: Int = 10,
    /** Re-alert unanswered doses every [repeatIntervalMinutes], up to [maxRepeats] times. */
    val repeatReminders: Boolean = true,
    val repeatIntervalMinutes: Int = 10,
    val maxRepeats: Int = 3,
    /** How long a dose alarm rings before going quiet (it re-rings on the next repeat). */
    val alarmRingMinutes: Int = 3,
    /** Time of day for the daily "running low on medicine" check. */
    val refillCheckMinute: Int = 10 * 60,
    /** The refill list suggests buying enough for this many days. */
    val refillBuyDays: Int = 30,
    val profile: MedicalProfile = MedicalProfile(),
    val lastBackupAt: Long = 0,
)

/**
 * App settings in SharedPreferences, exposed as a [StateFlow].
 * SharedPreferences (not DataStore) so alarm receivers can read them synchronously.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val current: AppSettings get() = _settings.value

    fun update(transform: (AppSettings) -> AppSettings) {
        synchronized(this) {
            val next = transform(_settings.value)
            write(next)
            _settings.value = next
        }
    }

    private fun read(): AppSettings = with(prefs) {
        val defaults = AppSettings()
        val meals = MealTimes()
        AppSettings(
            onboardingDone = getBoolean("onboardingDone", false),
            language = getString("language", "") ?: "",
            userName = getString("userName", "") ?: "",
            mealTimes = MealTimes(
                breakfast = getInt("meal.breakfast", meals.breakfast),
                lunch = getInt("meal.lunch", meals.lunch),
                evening = getInt("meal.evening", meals.evening),
                dinner = getInt("meal.dinner", meals.dinner),
                bedtime = getInt("meal.bedtime", meals.bedtime),
            ),
            beforeMealMinutes = getInt("beforeMealMinutes", defaults.beforeMealMinutes),
            afterMealMinutes = getInt("afterMealMinutes", defaults.afterMealMinutes),
            defaultAlertType = runCatching { AlertType.valueOf(getString("defaultAlertType", null) ?: "") }
                .getOrDefault(defaults.defaultAlertType),
            snoozeMinutes = getInt("snoozeMinutes", defaults.snoozeMinutes),
            repeatReminders = getBoolean("repeatReminders", defaults.repeatReminders),
            repeatIntervalMinutes = getInt("repeatIntervalMinutes", defaults.repeatIntervalMinutes),
            maxRepeats = getInt("maxRepeats", defaults.maxRepeats),
            alarmRingMinutes = getInt("alarmRingMinutes", defaults.alarmRingMinutes),
            refillCheckMinute = getInt("refillCheckMinute", defaults.refillCheckMinute),
            refillBuyDays = getInt("refillBuyDays", defaults.refillBuyDays),
            profile = MedicalProfile(
                bloodGroup = getString("profile.bloodGroup", "") ?: "",
                allergies = getString("profile.allergies", "") ?: "",
                conditions = getString("profile.conditions", "") ?: "",
                emergencyName = getString("profile.emergencyName", "") ?: "",
                emergencyPhone = getString("profile.emergencyPhone", "") ?: "",
                birthYear = getInt("profile.birthYear", 0).takeIf { it > 0 },
            ),
            lastBackupAt = getLong("lastBackupAt", 0),
        )
    }

    private fun write(s: AppSettings) {
        prefs.edit()
            .putBoolean("onboardingDone", s.onboardingDone)
            .putString("language", s.language)
            .putString("userName", s.userName)
            .putInt("meal.breakfast", s.mealTimes.breakfast)
            .putInt("meal.lunch", s.mealTimes.lunch)
            .putInt("meal.evening", s.mealTimes.evening)
            .putInt("meal.dinner", s.mealTimes.dinner)
            .putInt("meal.bedtime", s.mealTimes.bedtime)
            .putInt("beforeMealMinutes", s.beforeMealMinutes)
            .putInt("afterMealMinutes", s.afterMealMinutes)
            .putString("defaultAlertType", s.defaultAlertType.name)
            .putInt("snoozeMinutes", s.snoozeMinutes)
            .putBoolean("repeatReminders", s.repeatReminders)
            .putInt("repeatIntervalMinutes", s.repeatIntervalMinutes)
            .putInt("maxRepeats", s.maxRepeats)
            .putInt("alarmRingMinutes", s.alarmRingMinutes)
            .putInt("refillCheckMinute", s.refillCheckMinute)
            .putInt("refillBuyDays", s.refillBuyDays)
            .putString("profile.bloodGroup", s.profile.bloodGroup)
            .putString("profile.allergies", s.profile.allergies)
            .putString("profile.conditions", s.profile.conditions)
            .putString("profile.emergencyName", s.profile.emergencyName)
            .putString("profile.emergencyPhone", s.profile.emergencyPhone)
            .putInt("profile.birthYear", s.profile.birthYear ?: 0)
            .putLong("lastBackupAt", s.lastBackupAt)
            .apply()
    }

    companion object {
        const val FILE = "mediminder_settings"

        /** Language read without Hilt, for receivers that build a localized context. */
        fun storedLanguage(context: Context): String =
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("language", "") ?: ""
    }
}
