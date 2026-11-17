package com.example.mediminder.ui.medicines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediminder.R
import com.example.mediminder.data.MedicineRepository
import com.example.mediminder.data.local.entity.MedicationWithSchedules
import com.example.mediminder.ui.components.EmptyState
import com.example.mediminder.ui.components.PillAvatar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class MedicinesViewModel @Inject constructor(repository: MedicineRepository) : ViewModel() {
    val medicines: StateFlow<List<MedicationWithSchedules>?> = repository.observeAll()
        .map { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicinesScreen(
    modifier: Modifier,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onOpenRefills: () -> Unit,
    viewModel: MedicinesViewModel = hiltViewModel(),
) {
    val all by viewModel.medicines.collectAsState()
    val list = all ?: return
    val today = LocalDate.now().toEpochDay()
    val active = list.filter { !it.medication.archived }
    val stopped = list.filter { it.medication.archived }
    val lowCount = active.count { com.example.mediminder.domain.Stock.isLow(it.medication, it.schedules, today) }
    var showStopped by rememberSaveable { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        Column {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_medicines), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenRefills) {
                        BadgedBox(badge = { if (lowCount > 0) Badge { Text(lowCount.toString()) } }) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = stringResource(R.string.refill_title))
                        }
                    }
                },
            )
            if (active.isEmpty() && stopped.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Medication,
                    title = stringResource(R.string.medicines_empty_title),
                    body = stringResource(R.string.medicines_empty_body),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                ) {
                    items(active, key = { it.medication.id }) { item -> MedicineRow(item, today) { onOpen(item.medication.id) } }
                    if (stopped.isNotEmpty()) {
                        item {
                            TextButton(onClick = { showStopped = !showStopped }) {
                                Text(stringResource(R.string.medicines_stopped, stopped.size))
                                Icon(if (showStopped) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                            }
                        }
                        if (showStopped) items(stopped, key = { it.medication.id }) { item -> MedicineRow(item, today) { onOpen(item.medication.id) } }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            // Static label (no expand/collapse animation)
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_add_medicine))
        }
    }
}

@Composable
fun MedicineRow(item: MedicationWithSchedules, today: Long, onClick: () -> Unit) {
    val context = LocalContext.current
    val m = item.medication
    val low = com.example.mediminder.domain.Stock.isLow(m, item.schedules, today)
    val daysLeft = com.example.mediminder.domain.Stock.daysLeft(m, item.schedules)
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (m.archived) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (m.archived) 0.dp else 2.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            PillAvatar(m.name, m.color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(listOf(m.name, m.strength).filter { it.isNotBlank() }.joinToString(" "), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                m.genericName?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Text(scheduleSummary(context, item), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                if (m.trackStock) {
                    Spacer(Modifier.height(6.dp))
                    val stockText = com.example.mediminder.util.Formats.quantity(context, m.stock, m.unit)
                    Text(
                        if (daysLeft != null) stringResource(R.string.stock_left_days, stockText, com.example.mediminder.util.Formats.number(context, daysLeft)) else stringResource(R.string.stock_left, stockText),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (low) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    if (daysLeft != null) {
                        LinearProgressIndicator(
                            progress = { (daysLeft.coerceAtMost(30) / 30f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/** "১+০+১ · খাবারের পরে", "3 times a day", "As needed", "Stopped" */
fun scheduleSummary(context: android.content.Context, item: MedicationWithSchedules): String {
    val m = item.medication
    if (m.archived) return context.getString(R.string.medicine_stopped)
    if (m.isAsNeeded) return context.getString(R.string.schedule_as_needed)
    val enabled = item.schedules.filter { it.enabled }
    if (enabled.isEmpty()) return context.getString(R.string.schedule_none)
    val parts = mutableListOf<String>()
    parts += m.dosePattern?.let { com.example.mediminder.util.Formats.digits(context, it) }
        ?: context.resources.getQuantityString(R.plurals.times_a_day, enabled.size, enabled.size)
    if (m.mealTiming != com.example.mediminder.data.local.entity.MealTiming.ANY) parts += com.example.mediminder.util.Formats.mealTiming(context, m.mealTiming)
    if (m.endDate != null) {
        val left = (m.endDate - LocalDate.now().toEpochDay() + 1).toInt()
        if (left > 0) parts += context.getString(R.string.course_days_left, com.example.mediminder.util.Formats.number(context, left))
        else parts += context.getString(R.string.course_finished)
    }
    return parts.joinToString(" · ")
}
