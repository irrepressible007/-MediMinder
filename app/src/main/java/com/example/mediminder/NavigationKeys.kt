package com.example.mediminder

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main
@Serializable data object AddMedication
@Serializable data object CameraScanner
@Serializable data object MedicalVault : NavKey
