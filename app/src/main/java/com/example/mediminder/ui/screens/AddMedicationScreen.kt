package com.example.mediminder.ui.screens

import android.app.TimePickerDialog
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mediminder.ui.theme.BlueSecondaryDark
import com.example.mediminder.ui.theme.GreenPrimaryDark
import com.example.mediminder.ui.viewmodel.AddMedicationViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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
    
    val dosage by viewModel.dosage.collectAsState()
    val selectedShape by viewModel.shape.collectAsState()
    val selectedColor by viewModel.color.collectAsState()
    val inventory by viewModel.inventory.collectAsState()
    val isPrn by viewModel.isPrn.collectAsState()
    val maxDaily by viewModel.maxDaily.collectAsState()
    val schedules by viewModel.schedules.collectAsState()
    val isSaved by viewModel.isSaved.collectAsState()

    var expanded by remember { mutableStateOf(false) }
    
    val shapes = listOf("Round", "Capsule", "Oblong", "Square")
    val colors = listOf(GreenPrimaryDark, BlueSecondaryDark, Color(0xFFEF4444), Color(0xFFF59E0B), Color(0xFF8B5CF6))
    
    val context = LocalContext.current
    val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

    LaunchedEffect(isSaved) {
        if (isSaved) {
            onSave()
        }
    }

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
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
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
                                viewModel.dosage.value = medicine.strength ?: ""
                                
                                // Attempt to guess shape
                                val form = medicine.dosageForm?.lowercase() ?: ""
                                if (form.contains("capsule")) viewModel.shape.value = "Capsule"
                                else if (form.contains("tablet")) viewModel.shape.value = "Round"
                                else if (form.contains("syrup") || form.contains("suspension")) viewModel.shape.value = "Square"
                                
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
                onValueChange = { viewModel.dosage.value = it },
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
                        onClick = { viewModel.shape.value = shape },
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
                            .clickable { viewModel.color.value = color }
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
                onValueChange = { viewModel.inventory.value = it },
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
                Column(modifier = Modifier.weight(1f)) {
                    Text("As Needed (PRN)", fontWeight = FontWeight.SemiBold)
                    Text("Only take when symptoms occur", style = MaterialTheme.typography.bodyMedium)
                }
                Switch(checked = isPrn, onCheckedChange = { viewModel.isPrn.value = it })
            }

            if (isPrn) {
                OutlinedTextField(
                    value = maxDaily,
                    onValueChange = { viewModel.maxDaily.value = it },
                    label = { Text("Max Daily Doses (Safety Limit)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            } else {
                Text("Schedules (Times per day)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                
                schedules.forEach { time ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(time.format(timeFormatter), fontWeight = FontWeight.Bold)
                        IconButton(
                            onClick = { viewModel.removeSchedule(time) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove time")
                        }
                    }
                }
                
                Button(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                viewModel.addSchedule(LocalTime.of(hour, minute))
                            },
                            8, 0, false
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Time")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.saveMedication() },
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
