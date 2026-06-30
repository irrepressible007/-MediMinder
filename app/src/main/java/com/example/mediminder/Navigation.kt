package com.example.mediminder

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.mediminder.ui.screens.AddMedicationScreen
import com.example.mediminder.ui.screens.HomeScreen

import com.example.mediminder.ui.screens.CameraScannerScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> {
          HomeScreen(onAddMedication = { backStack.add(AddMedication) })
        }
        entry<AddMedication> {
          AddMedicationScreen(
            onBack = { backStack.removeLast() },
            onSave = { backStack.removeLast() },
            onLaunchScanner = { backStack.add(CameraScanner) }
          )
        }
        entry<CameraScanner> {
          CameraScannerScreen(
            onBack = { backStack.removeLast() },
            onTextFound = { scannedText ->
                // In a real app we would pass this text back to the AddMedicationViewModel 
                // to populate the search query. For now, we pop back.
                backStack.removeLast()
            }
          )
        }
      },
  )
}
