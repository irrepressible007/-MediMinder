package com.example.mediminder

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey
@Serializable data object AddMedication : NavKey
@Serializable data object CameraScanner : NavKey
@Serializable data object MedicalVault : NavKey
