package com.example.mediminder

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.mediminder.ui.screens.AddMedicationScreen
import com.example.mediminder.ui.screens.CameraScannerScreen
import com.example.mediminder.ui.screens.HomeScreen
import com.example.mediminder.ui.screens.MedicalVaultScreen

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(Main)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Main> {
                HomeScreen(
                    onAddMedication = { backStack += AddMedication },
                    onOpenVault = { backStack += MedicalVault }
                )
            }
            entry<AddMedication> {
                AddMedicationScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onSave = { backStack.removeLastOrNull() },
                    onLaunchScanner = { backStack += CameraScanner }
                )
            }
            entry<CameraScanner> {
                CameraScannerScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onTextFound = { _ ->
                        backStack.removeLastOrNull()
                    }
                )
            }
            entry<MedicalVault> {
                MedicalVaultScreen(
                    onBack = { backStack.removeLastOrNull() }
                )
            }
        },
    )
}
