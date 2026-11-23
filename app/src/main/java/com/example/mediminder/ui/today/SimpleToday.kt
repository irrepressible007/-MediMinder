package com.example.mediminder.ui.today

import com.example.mediminder.domain.CareKind
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.mediminder.R
import com.example.mediminder.data.local.entity.Medication
import com.example.mediminder.domain.DoseState
import com.example.mediminder.ui.theme.LocalStatusColors
import com.example.mediminder.ui.components.MedicineImage
import com.example.mediminder.ui.components.NoticeCard
import com.example.mediminder.ui.components.PrimaryButton
import com.example.mediminder.ui.components.Tone
import com.example.mediminder.util.Formats
import java.time.format.FormatStyle

/**
 * Today in Simple view, for people who mostly need "what do I take now, and did I take it":
 * the date in plain words, the next dose large with one big button, then the day as a plain
 * list. No pictures but the medicine's own photo, no motion, nothing else to read.
 */
@Composable
internal fun SimpleToday(
    state: TodayUi,
    setupCard: @Composable () -> Unit,
    onTake: (DoseRow) -> Unit,
    onOptions: (DoseRow) -> Unit,
    onAddMedicine: () -> Unit,
    onOpenRefills: () -> Unit,
    onTakeAsNeeded: (Medication) -> Unit,
    onOpenMedicine: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text(stringResource(R.string.simple_today), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(
                    Formats.date(context, state.date, FormatStyle.FULL),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { setupCard() }

        if (!state.hasMedicines) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.today_empty_title), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.today_empty_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PrimaryButton(stringResource(R.string.action_add_medicine), onAddMedicine, icon = Icons.Default.Add)
                }
            }
            return@LazyColumn
        }

        if (state.lowStock > 0) item {
            NoticeCard(
                text = stringResource(R.string.today_low_stock_banner, Formats.number(context, state.lowStock)),
                icon = Icons.Default.ShoppingCart,
                tone = Tone.WARNING,
                onClick = onOpenRefills,
            )
        }

        // The one thing that matters most: the dose to take now, or the next one
        val next = state.rows.firstOrNull { it.state in ACTIONABLE } ?: state.rows.firstOrNull { it.state == DoseState.UPCOMING }
        // everything due at that same time, together (three medicines at 3:15 are one moment)
        val nextGroup = next?.let { n -> state.rows.filter { it.minuteOfDay == n.minuteOfDay && (it.state in ACTIONABLE) == (n.state in ACTIONABLE) && it.state != DoseState.TAKEN && it.state != DoseState.SKIPPED }
            // a health check is answered by writing the reading down: it keeps its own button below
            .filter { it === n || it.kind != CareKind.MEASURE } }.orEmpty()
        when {
            next != null -> item(key = "next") { NextDose(nextGroup, actionable = state.isToday && next.state in ACTIONABLE, onTake, onOptions) }
            state.rows.isNotEmpty() -> item(key = "done") { AllDone() }
        }

        if (state.rows.isNotEmpty()) {
            item { Text(stringResource(R.string.simple_todays_list), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    state.rows.forEachIndexed { i, row ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        // the dose in the big card above has its button there; here it just says "Now"
                        val isNext = next != null && next.state in ACTIONABLE && nextGroup.any { it.scheduleId == row.scheduleId && it.day == row.day }
                        SimpleDoseRow(row, actionable = state.isToday && row.state in ACTIONABLE, isNext = isNext, onTake, onOptions)
                    }
                }
            }
        }

        if (state.asNeeded.isNotEmpty() && state.isToday) {
            item { Text(stringResource(R.string.today_as_needed), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            items(state.asNeeded, key = { "prn-${it.id}" }) { med ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.clickable { onOpenMedicine(med.id) },
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            listOf(med.name, med.strength).filter { it.isNotBlank() }.joinToString(" "),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Button(onClick = { onTakeAsNeeded(med) }, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.today_prn_took), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

/** The doses to take now (or the next ones), large, with a single big button for them all. */
@Composable
private fun NextDose(rows: List<DoseRow>, actionable: Boolean, onTake: (DoseRow) -> Unit, onOptions: (DoseRow) -> Unit) {
    val row = rows.first()
    val context = LocalContext.current
    val status = LocalStatusColors.current
    val heading = when (row.state) {
        DoseState.DUE -> stringResource(R.string.simple_take_now)
        DoseState.MISSED -> stringResource(R.string.simple_not_taken)
        DoseState.SNOOZED -> stringResource(R.string.simple_snoozed)
        else -> stringResource(R.string.simple_next)
    }
    val accent = when (row.state) {
        DoseState.MISSED -> status.missed
        DoseState.DUE, DoseState.SNOOZED -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(if (actionable) 3.dp else 1.dp, accent),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(heading, style = MaterialTheme.typography.titleLarge, color = accent, fontWeight = FontWeight.Bold)
            Text(Formats.time(context, row.minuteOfDay), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
            rows.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MedicineImage(r.photo, r.name, r.color, size = if (rows.size == 1) 72 else 56, kind = r.kind, subtype = r.subtype)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(rowName(r), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(doseDetail(r), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (actionable) {
                Button(
                    onClick = { rows.forEach(onTake) },
                    colors = ButtonDefaults.buttonColors(containerColor = status.taken, contentColor = Color.White),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(if (rows.size == 1) R.string.dose_sheet_take else R.string.simple_took_all), style = MaterialTheme.typography.headlineSmall)
                }
                TextButton(onClick = { onOptions(row) }, modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.simple_other_choices), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun AllDone() {
    val status = LocalStatusColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(3.dp, status.taken),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = status.taken, modifier = Modifier.size(48.dp))
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.simple_all_done), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** One dose in the day's list: time, medicine, and either a Taken button or a plain word. */
@Composable
private fun SimpleDoseRow(row: DoseRow, actionable: Boolean, isNext: Boolean, onTake: (DoseRow) -> Unit, onOptions: (DoseRow) -> Unit) {
    val context = LocalContext.current
    val status = LocalStatusColors.current
    Row(
        Modifier.fillMaxWidth().clickable { onOptions(row) }.heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Formats.time(context, row.minuteOfDay),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.widthIn(min = 92.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(rowName(row), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(doseDetail(row), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        if (isNext) {
            Text(stringResource(R.string.simple_now), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        } else if (actionable) {
            Button(
                onClick = { onTake(row) },
                colors = ButtonDefaults.buttonColors(containerColor = status.taken, contentColor = Color.White),
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.simple_took_it), style = MaterialTheme.typography.titleMedium) }
        } else {
            val (word, color) = when (row.state) {
                DoseState.TAKEN -> stringResource(R.string.action_taken) to status.taken
                DoseState.SKIPPED -> stringResource(R.string.state_skipped) to MaterialTheme.colorScheme.onSurfaceVariant
                DoseState.MISSED -> stringResource(R.string.state_missed) to status.missed
                else -> stringResource(R.string.simple_later) to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (row.state == DoseState.TAKEN) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(word, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
            }
        }
    }
}
