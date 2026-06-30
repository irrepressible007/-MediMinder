package com.example.mediminder.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Calendar

data class GamificationState(
    val currentStreak: Int = 0,
    val totalPoints: Int = 0
)

@Singleton
class GamificationManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gamification_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        GamificationState(
            currentStreak = prefs.getInt("currentStreak", 0),
            totalPoints = prefs.getInt("totalPoints", 0)
        )
    )
    val state: StateFlow<GamificationState> = _state.asStateFlow()

    fun awardPoints(points: Int) {
        val currentPoints = prefs.getInt("totalPoints", 0)
        val newPoints = currentPoints + points
        prefs.edit().putInt("totalPoints", newPoints).apply()
        updateState()
    }

    fun logInteraction() {
        val lastLogTime = prefs.getLong("lastLogTime", 0L)
        val currentTime = System.currentTimeMillis()
        
        val lastCal = Calendar.getInstance().apply { timeInMillis = lastLogTime }
        val currentCal = Calendar.getInstance().apply { timeInMillis = currentTime }
        
        val isSameDay = lastCal.get(Calendar.YEAR) == currentCal.get(Calendar.YEAR) &&
                        lastCal.get(Calendar.DAY_OF_YEAR) == currentCal.get(Calendar.DAY_OF_YEAR)
                        
        // Calculate day difference roughly
        val diffDays = (currentTime - lastLogTime) / (1000 * 60 * 60 * 24)
        
        var currentStreak = prefs.getInt("currentStreak", 0)

        if (!isSameDay) {
            if (diffDays <= 1) {
                currentStreak += 1
            } else if (lastLogTime != 0L) {
                currentStreak = 1
            } else {
                currentStreak = 1
            }
            prefs.edit().putInt("currentStreak", currentStreak)
                .putLong("lastLogTime", currentTime)
                .apply()
            updateState()
        }
    }

    private fun updateState() {
        _state.value = GamificationState(
            currentStreak = prefs.getInt("currentStreak", 0),
            totalPoints = prefs.getInt("totalPoints", 0)
        )
    }
}
