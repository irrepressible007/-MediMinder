package com.example.mediminder.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mediminder.ui.components.TimelineWheel
import com.example.mediminder.ui.components.WheelItem
import com.example.mediminder.ui.viewmodel.HomeViewModel
import com.example.mediminder.updater.UpdateManager
import com.example.mediminder.updater.UpdateResult
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddMedication: () -> Unit = {},
    onOpenVault: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val gamificationState by viewModel.gamificationState.collectAsState()
    val wheelItems by viewModel.wheelItems.collectAsState()
    
    var selectedItem by remember { mutableStateOf<WheelItem?>(null) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            "Today's Schedule",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("🔥 ${gamificationState.currentStreak} Days", style = MaterialTheme.typography.bodySmall, color = Color(0xFFF59E0B))
                            Text("🏆 ${gamificationState.totalPoints} Pts", style = MaterialTheme.typography.bodySmall, color = Color(0xFF8B5CF6))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            Toast.makeText(context, "Checking for updates...", Toast.LENGTH_SHORT).show()
                            when (val result = UpdateManager.checkForUpdates(context)) {
                                is UpdateResult.UpdateAvailable -> {
                                    UpdateManager.downloadAndInstallUpdate(context, result.downloadUrl, result.version)
                                }
                                is UpdateResult.UpToDate -> {
                                    Toast.makeText(context, "You are on the latest version!", Toast.LENGTH_SHORT).show()
                                }
                                is UpdateResult.Error -> {
                                    Toast.makeText(context, "Error: ${result.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Check for Updates")
                    }
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
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    )
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            
            if (wheelItems.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("No medications scheduled for today.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            } else {
                TimelineWheel(
                    items = wheelItems,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onItemClick = { item ->
                        selectedItem = item
                    }
                )
                
                AnimatedVisibility(
                    visible = selectedItem != null,
                    enter = expandVertically(animationSpec = tween(300)),
                    exit = shrinkVertically(animationSpec = tween(300))
                ) {
                    selectedItem?.let { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            shape = RoundedCornerShape(24.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                                // Assuming timeOfDayMillis is relative to start of day for this demo
                                // If it's absolute, formatting works as is. Let's assume it's just time of day millis.
                                val calendar = Calendar.getInstance().apply {
                                    set(Calendar.HOUR_OF_DAY, 0)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                    add(Calendar.MILLISECOND, item.timeOfDayMillis.toInt())
                                }
                                
                                Text(
                                    text = "${item.label} at ${timeFormat.format(calendar.time)}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        viewModel.takeMedication(item)
                                        Toast.makeText(context, "Medication Taken! +10 Points", Toast.LENGTH_SHORT).show()
                                        selectedItem = null
                                    },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Take")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Take Medication", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
