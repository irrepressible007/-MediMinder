package com.example.mediminder.ui.medicines

import com.example.mediminder.domain.OpenedItems
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.flow.map
import com.example.mediminder.ui.components.Art
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.lerp
import com.example.mediminder.ui.components.FeedbackBus
import com.example.mediminder.ui.components.Motion
import com.example.mediminder.ui.components.popOnEnter
import com.example.mediminder.ui.theme.Hue
import com.example.mediminder.ui.theme.color
import kotlinx.coroutines.delay
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediminder.R
import com.example.mediminder.ui.components.rememberEntranceState
import com.example.mediminder.ui.components.entrance
import androidx.compose.foundation.lazy.itemsIndexed
import com.example.mediminder.ui.components.MedicineImage
import com.example.mediminder.data.MedicineRepository
import com.example.mediminder.data.local.entity.MealTiming
import com.example.mediminder.data.local.entity.MedicationWithSchedules
import com.example.mediminder.domain.DosePattern
import com.example.mediminder.domain.Stock
import com.example.mediminder.ui.components.EmptyState
import com.example.mediminder.ui.components.ExpandableSection
import com.example.mediminder.ui.components.MediCard
import com.example.mediminder.ui.components.NoticeCard
import com.example.mediminder.ui.components.PillAvatar
import com.example.mediminder.ui.components.ScreenHeader
import com.example.mediminder.ui.components.Tone
import com.example.mediminder.ui.theme.LocalStatusColors
import com.example.mediminder.util.Formats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class MedicinesViewModel @Inject constructor(repository: MedicineRepository) : ViewModel() {
    val medicines: StateFlow<List<MedicationWithSchedules>?> = repository.observeAll()
        .map { list -> list.filter { it.medication.kind.isMedicine } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun MedicinesScreen(
    modifier: Modifier,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onOpenRefills: () -> Unit,
    viewModel: MedicinesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val all by viewModel.medicines.collectAsState()
    val list = all ?: return
    val today = LocalDate.now().toEpochDay()
    val active = list.filter { !it.medication.archived }
    val stopped = list.filter { it.medication.archived }
    val lowCount = active.count { Stock.isLow(it.medication, it.schedules, today) }

    val entrance = rememberEntranceState()
    // The medicine just added or changed glows once when the list is next seen
    val highlight by FeedbackBus.highlight.collectAsState()
    val glowId = highlight?.takeIf { System.currentTimeMillis() - it.second < 120_000 }?.first
    LaunchedEffect(glowId) {
        if (glowId != null) {
            delay(4000)
            FeedbackBus.clearHighlight()
        }
    }
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ScreenHeader(stringResource(R.string.medicines_title), icon = Icons.Default.Medication, hue = Hue.CORAL) }
            if (active.isEmpty() && stopped.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Medication,
                        title = stringResource(R.string.medicines_empty_title),
                        body = stringResource(R.string.medicines_empty_body),
                        hue = Hue.CORAL,
                        art = Art.BLISTER,
                    )
                }
                return@LazyColumn
            }
            if (lowCount > 0) item {
                NoticeCard(
                    text = stringResource(R.string.today_low_stock_banner, Formats.number(context, lowCount)),
                    icon = Icons.Default.ShoppingCart,
                    tone = Tone.WARNING,
                    onClick = onOpenRefills,
                )
            }
            itemsIndexed(active, key = { _, it -> it.medication.id }) { i, item ->
                MedicineRow(item, today, Modifier.animateItem().entrance(entrance, i), highlighted = item.medication.id == glowId) { onOpen(item.medication.id) }
            }
            if (stopped.isNotEmpty()) {
                item {
                    ExpandableSection(stringResource(R.string.medicines_stopped, stopped.size)) {
                        stopped.forEach { item -> MedicineRow(item, today) { onOpen(item.medication.id) } }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).heightIn(min = 64.dp).popOnEnter(),
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.action_add_medicine), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun MedicineRow(item: MedicationWithSchedules, today: Long, modifier: Modifier = Modifier, highlighted: Boolean = false, onClick: () -> Unit) {
    val context = LocalContext.current
    val status = LocalStatusColors.current
    val m = item.medication
    val low = Stock.isLow(m, item.schedules, today)
    val daysLeft = Stock.daysLeft(m, item.schedules)
    val glow = remember { Animatable(0f) }
    LaunchedEffect(highlighted) {
        if (highlighted) {
            glow.animateTo(1f, tween(Motion.MEDIUM))
            delay(900)
            glow.animateTo(0f, tween(1600))
        } else glow.animateTo(0f, tween(Motion.SHORT))
    }
    val base = if (m.archived) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface
    MediCard(
        onClick = onClick,
        modifier = modifier,
        containerColor = lerp(base, MaterialTheme.colorScheme.secondaryContainer, glow.value),
        borderColor = lerp(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.secondary, glow.value),
        borderWidth = if (glow.value > 0.02f) 2.dp else 1.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MedicineImage(m.photoUri, m.name, m.color, size = 64, kind = m.kind)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(listOf(m.name, m.strength).filter { it.isNotBlank() }.joinToString(" "), style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (m.isAsNeeded) Icons.Default.MedicalServices else Icons.Default.Schedule,
                        contentDescription = null,
                        tint = if (m.archived) MaterialTheme.colorScheme.onSurfaceVariant else Hue.SKY.color,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(scheduleSummary(context, item), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (!m.archived && OpenedItems.needsReplacing(m, LocalDate.now().toEpochDay())) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Autorenew, contentDescription = null, tint = status.due, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.replace_chip), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = status.due)
            }
        }
        if (m.trackStock) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (low) Icons.Default.Warning else Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = if (low) status.due else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                val stockText = Formats.quantity(context, m.stock, m.unit)
                Text(
                    if (daysLeft != null) stringResource(R.string.stock_left_days, stockText, Formats.number(context, daysLeft)) else stringResource(R.string.stock_left, stockText),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (low) status.onDueContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Plain words for the common prescription patterns, e.g. "1+0+1" → "Morning and night". */
fun patternName(context: Context, pattern: String?): String? {
    val text = pattern?.let { DosePattern.parse(it)?.text } ?: return null
    val id = when (text) {
        "1+0+0" -> R.string.pattern_morning
        "0+1+0" -> R.string.pattern_noon
        "0+0+1" -> R.string.pattern_night
        "1+0+1" -> R.string.pattern_morning_night
        "1+1+1" -> R.string.pattern_three
        "1+1+1+1" -> R.string.pattern_four
        else -> return null
    }
    return context.getString(id)
}

/** "Morning and night · After meal", "3 times a day", "As needed", "Stopped" */
fun scheduleSummary(context: Context, item: MedicationWithSchedules): String {
    val m = item.medication
    if (m.archived) return context.getString(R.string.medicine_stopped)
    if (m.isAsNeeded) return context.getString(R.string.schedule_as_needed)
    val enabled = item.schedules.filter { it.enabled }
    if (enabled.isEmpty()) return context.getString(R.string.schedule_none)
    val parts = mutableListOf<String>()
    parts += patternName(context, m.dosePattern)
        ?: m.dosePattern?.let { Formats.digits(context, it) }
        ?: context.resources.getQuantityString(R.plurals.times_a_day, enabled.size, enabled.size).let { Formats.digits(context, it) }
    if (m.mealTiming != MealTiming.ANY) parts += Formats.mealTiming(context, m.mealTiming)
    if (m.endDate != null) {
        val left = (m.endDate - LocalDate.now().toEpochDay() + 1).toInt()
        parts += if (left > 0) context.getString(R.string.course_days_left, Formats.number(context, left)) else context.getString(R.string.course_finished)
    }
    return parts.joinToString(" · ")
}
