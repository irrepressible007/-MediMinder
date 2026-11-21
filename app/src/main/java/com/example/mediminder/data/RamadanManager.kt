package com.example.mediminder.data

import com.example.mediminder.data.settings.SettingsRepository
import com.example.mediminder.domain.Ramadan
import com.example.mediminder.reminders.Notifier
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns Ramadan mode on and off. While it is on, the meal times are Sehri, after Taraweeh and
 * Iftar, and every meal-based dose follows them; the usual meal times are kept and put back
 * afterwards (by hand, or by itself the day after [RamadanSettings.until]).
 */
@Singleton
class RamadanManager @Inject constructor(
    private val settings: SettingsRepository,
    private val medicines: MedicineRepository,
    private val notifier: Notifier,
) {
    /** Starts Ramadan mode until [until] (the last day of fasting). Returns how many dose times moved. */
    suspend fun turnOn(until: LocalDate): Int {
        settings.update { s ->
            val usual = s.ramadan.usualMeals ?: s.mealTimes
            s.copy(
                mealTimes = Ramadan.meals(s.ramadan),
                ramadan = s.ramadan.copy(on = true, until = until.toEpochDay(), usualMeals = usual),
            )
        }
        return medicines.applyMealTimes()
    }

    /** Back to the usual meal times. Returns how many dose times moved back. */
    suspend fun turnOff(): Int {
        settings.update { s ->
            s.copy(
                mealTimes = s.ramadan.usualMeals ?: s.mealTimes,
                ramadan = s.ramadan.copy(on = false, until = null, usualMeals = null),
            )
        }
        return medicines.applyMealTimes()
    }

    /** New Sehri, Iftar or late-night times; while on, the doses move at once. */
    suspend fun setTimes(sehri: Int? = null, iftar: Int? = null, lateNight: Int? = null) {
        settings.update { s ->
            val r = s.ramadan.copy(
                sehri = sehri ?: s.ramadan.sehri,
                iftar = iftar ?: s.ramadan.iftar,
                lateNight = lateNight ?: s.ramadan.lateNight,
            )
            if (r.on) s.copy(ramadan = r, mealTimes = Ramadan.meals(r)) else s.copy(ramadan = r)
        }
        if (settings.current.ramadan.on) medicines.applyMealTimes()
    }

    fun setUntil(until: LocalDate) = settings.update { s -> s.copy(ramadan = s.ramadan.copy(until = until.toEpochDay())) }

    /** "Not now" on the card that says Ramadan is coming: hidden until next year's. */
    fun dismissSuggestion(year: Int) = settings.update { s -> s.copy(ramadan = s.ramadan.copy(dismissedYear = year)) }

    /** Should Today suggest Ramadan mode? Returns the expected first day, or null. */
    fun suggestion(today: LocalDate): LocalDate? {
        val r = settings.current.ramadan
        if (r.on || !Ramadan.isNear(today)) return null
        val start = Ramadan.estimatedStart(today) ?: return null
        return start.takeIf { r.dismissedYear != it.year }
    }

    /** Called daily: once the last day has passed, the usual times come back by themselves. */
    suspend fun endIfOver(today: LocalDate = LocalDate.now()): Boolean {
        val r = settings.current.ramadan
        val until = r.until ?: return false
        if (!r.on || today.toEpochDay() <= until) return false
        turnOff()
        notifier.showRamadanOver()
        return true
    }
}
