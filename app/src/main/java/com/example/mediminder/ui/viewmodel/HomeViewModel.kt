package com.example.mediminder.ui.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediminder.data.local.GamificationManager
import com.example.mediminder.data.local.dao.LogDao
import com.example.mediminder.data.local.dao.MedicationDao
import com.example.mediminder.data.local.entity.Log
import com.example.mediminder.ui.components.WheelItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val gamificationManager: GamificationManager,
    private val medicationDao: MedicationDao,
    private val logDao: LogDao
) : ViewModel() {
    
    val gamificationState = gamificationManager.state
    
    val wheelItems = medicationDao.getMedicationsWithSchedules().map { list ->
        val items = mutableListOf<WheelItem>()
        list.forEach { item ->
            val color = try {
                Color(android.graphics.Color.parseColor(item.medication.color))
            } catch (e: Exception) {
                Color(0xFF60A5FA)
            }
            item.schedules.forEach { schedule ->
                items.add(
                    WheelItem(
                        id = schedule.id,
                        medicationId = item.medication.id,
                        timeOfDayMillis = schedule.timeOfDayMillis,
                        label = item.medication.name,
                        color = color
                    )
                )
            }
        }
        items
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    fun takeMedication(wheelItem: WheelItem) {
        viewModelScope.launch {
            val log = Log(
                medicationId = wheelItem.medicationId,
                timestamp = System.currentTimeMillis(),
                status = "TAKEN",
                symptoms = null
            )
            logDao.insertLog(log)
            gamificationManager.logInteraction()
            gamificationManager.awardPoints(10)
        }
    }
}
