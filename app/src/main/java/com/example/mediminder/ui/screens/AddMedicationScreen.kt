package com.example.mediminder.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mediminder.ui.viewmodel.AddMedicationViewModel
import com.example.mediminder.ui.theme.BlueSecondaryDark
import com.example.mediminder.ui.theme.GreenPrimaryDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMedicationScreen(
    onBack: () -> Unit,
    onSave: () -> Unit,
    onLaunchScanner: () -> Unit = {},
    viewModel: AddMedicationViewModel = hiltViewModel()
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var dosage by remember { mutableStateOf("") }
    var selectedShape by remember { mutableStateOf("Round") }
    val shapes = listOf("Round", "Capsule", "Oblong", "Square")
    var selectedColor by remember { mutableStateOf(GreenPrimaryDark) }
    val colors = listOf(GreenPrimaryDark, BlueSecondaryDark, Color(0xFFEF4444), Color(0xFFF59E0B), Color(0xFF8B5CF6))
    var inventory by remember { mutableStateOf("") }
    var isPrn by remember { mutableStateOf(false) }
    var maxDaily by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Medication", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded && searchResults.isNotEmpty(),
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { 
                        viewModel.onSearchQueryChanged(it)
                        expanded = true 
                    },
                    label = { Text("Medication Name") },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    singleLine = true,
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    trailingIcon = {
                        IconButton(onClick = onLaunchScanner) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Scan with Camera")
                        }
                    }
                )
                
                ExposedDropdownMenu(
                    expanded = expanded && searchResults.isNotEmpty(),
                    onDismissRequest = { expanded = false }
                ) {
                    searchResults.forEach { medicine ->
                        DropdownMenuItem(
                            text = { Text(medicine.brandName ?: "") },
                            onClick = {
                                viewModel.onSearchQueryChanged(medicine.brandName ?: "")
                                dosage = medicine.strength ?: ""
                                
                                // Attempt to guess shape
                                val form = medicine.dosageForm?.lowercase() ?: ""
                                if (form.contains("capsule")) selectedShape = "Capsule"
                                else if (form.contains("tablet")) selectedShape = "Round"
                                else if (form.contains("syrup") || form.contains("suspension")) selectedShape = "Square"
                                
                                expanded = false
                            },
                            trailingIcon = {
                                Text(medicine.genericName ?: "", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = dosage,
                onValueChange = { dosage = it },
                label = { Text("Dosage (e.g., 500mg)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text("Pill Shape", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                shapes.forEach { shape ->
                    FilterChip(
                        selected = selectedShape == shape,
                        onClick = { selectedShape = shape },
                        label = { Text(shape) }
                    )
                }
            }

            Text("Pill Color", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                colors.forEach { color ->
                    val isSelected = selectedColor == color
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { selectedColor = color }
                            .border(
                                width = if (isSelected) 3.dp else 0.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                shape = CircleShape
                            )
                    )
                }
            }

            OutlinedTextField(
                value = inventory,
                onValueChange = { inventory = it },
                label = { Text("Current Inventory Count") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("As Needed (PRN)", fontWeight = FontWeight.SemiBold)
                    Text("Only take when symptoms occur", style = MaterialTheme.typography.bodyMedium)
                }
                Switch(checked = isPrn, onCheckedChange = { isPrn = it })
            }

            if (isPrn) {
                OutlinedTextField(
                    value = maxDaily,
                    onValueChange = { maxDaily = it },
                    label = { Text("Max Daily Doses (Safety Limit)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Save Medication", style = MaterialTheme.typography.titleMedium)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
