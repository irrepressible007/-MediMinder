package com.example.mediminder.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mediminder.ui.components.TimelineWheel
import com.example.mediminder.ui.components.WheelItem
import com.example.mediminder.ui.theme.GreenPrimaryDark
import com.example.mediminder.ui.theme.BlueSecondaryDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddMedication: () -> Unit = {},
    onOpenVault: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Today's Schedule",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                actions = {
                    IconButton(onClick = onOpenVault) {
                        Icon(Icons.Default.Lock, contentDescription = "Medical Vault")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddMedication,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Medication")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Mock data representing pills at different times of the day
            val mockItems = listOf(
                WheelItem(1, 8 * 3600000L, "Vitamin C", GreenPrimaryDark), // 8:00 AM
                WheelItem(2, 13 * 3600000L, "Aspirin", Color(0xFFEF4444)), // 1:00 PM
                WheelItem(3, 20 * 3600000L, "Melatonin", BlueSecondaryDark) // 8:00 PM
            )

            TimelineWheel(
                items = mockItems,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                onItemClick = { item ->
                    // Handle micro-animation expanding and selection
                }
            )
            
            Text(
                text = "Next: Aspirin at 1:00 PM",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 64.dp)
            )
        }
    }
}
