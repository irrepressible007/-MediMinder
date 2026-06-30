package com.example.mediminder.ui.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediminder.data.local.dao.MedicationDao
import com.example.mediminder.data.local.dao.MedicineDictionaryDao
import com.example.mediminder.data.local.dao.ScheduleDao
import com.example.mediminder.data.local.entity.Medication
import com.example.mediminder.data.local.entity.MedicineDictionary
import com.example.mediminder.data.local.entity.Schedule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class AddMedicationViewModel @Inject constructor(
    private val dictionaryDao: MedicineDictionaryDao,
    private val medicationDao: MedicationDao,
    private val scheduleDao: ScheduleDao,
    private val alarmScheduler: com.example.mediminder.alarm.AlarmScheduler
) : ViewModel() {

    // Search state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<MedicineDictionary>> = _searchQuery
        .flatMapLatest { query ->
            if (query.length >= 2) {
                dictionaryDao.searchMedicine(query)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Form state
    val dosage = MutableStateFlow("")
    val shape = MutableStateFlow("Round")
    val color = MutableStateFlow(Color(0xFF10B981)) // GreenPrimaryDark approx
    val inventory = MutableStateFlow("")
    val isPrn = MutableStateFlow(false)
    val maxDaily = MutableStateFlow("")
    
    // Schedules
    private val _schedules = MutableStateFlow<List<LocalTime>>(emptyList())
    val schedules: StateFlow<List<LocalTime>> = _schedules

    // Save status
    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }
    
    fun addSchedule(time: LocalTime) {
        if (!_schedules.value.contains(time)) {
            _schedules.value = _schedules.value + time
        }
    }
    
    fun removeSchedule(time: LocalTime) {
        _schedules.value = _schedules.value - time
    }

    fun saveMedication() {
        val name = _searchQuery.value
        if (name.isBlank()) return
        
        viewModelScope.launch {
            val medColorHex = String.format("#%06X", 0xFFFFFF and color.value.toArgb())
            val med = Medication(
                name = name,
                dosage = dosage.value,
                shape = shape.value,
                color = medColorHex,
                photoUri = null,
                inventoryCount = inventory.value.toIntOrNull() ?: 0,
                isAsNeeded = isPrn.value,
                maxDailyDoses = if (isPrn.value) maxDaily.value.toIntOrNull() else null
            )
            val medId = medicationDao.insertMedication(med)
            
            if (!isPrn.value) {
                _schedules.value.forEach { time ->
                    val millis = (time.hour * 60 * 60 * 1000L) + (time.minute * 60 * 1000L)
                    val scheduleId = scheduleDao.insertSchedule(
                        Schedule(
                            medicationId = medId,
                            timeOfDayMillis = millis,
                            isActive = true
                        )
                    )
                    
                    // Schedule with Android AlarmManager
                    val calendar = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.HOUR_OF_DAY, time.hour)
                        set(java.util.Calendar.MINUTE, time.minute)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                        if (before(java.util.Calendar.getInstance())) {
                            add(java.util.Calendar.DATE, 1)
                        }
                    }
                    alarmScheduler.schedule(medId, calendar.timeInMillis)
                }
            }
            
            _isSaved.value = true
        }
    }
}
