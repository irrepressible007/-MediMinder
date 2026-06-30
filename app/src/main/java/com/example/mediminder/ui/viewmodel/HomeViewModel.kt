package com.example.mediminder.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.mediminder.data.local.GamificationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val gamificationManager: GamificationManager
) : ViewModel() {
    val gamificationState = gamificationManager.state
    
    fun simulateTakingMedication() {
        gamificationManager.logInteraction()
        gamificationManager.awardPoints(10)
    }
}
