package com.example.mediminder.ui.more

import com.example.mediminder.ui.components.Art
import com.example.mediminder.ui.components.FeedbackBus
import com.example.mediminder.ui.components.FeedbackKind
import com.example.mediminder.ui.components.animatedCount
import com.example.mediminder.ui.theme.Hue
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediminder.R
import com.example.mediminder.data.MedicineRepository
import com.example.mediminder.data.local.MediMinderDatabase
import com.example.mediminder.data.local.dao.HistoryEntry
import com.example.mediminder.data.local.entity.DoseStatus
import com.example.mediminder.data.local.entity.Medication
import com.example.mediminder.domain.DoseState
import com.example.mediminder.domain.minuteOf
import com.example.mediminder.domain.toLocalDateTime
import com.example.mediminder.reminders.DoseActions
import com.example.mediminder.ui.components.AppTopBar
import com.example.mediminder.ui.components.ChoiceChips
import com.example.mediminder.ui.components.ConfirmDialog
import com.example.mediminder.ui.components.EmptyState
import com.example.mediminder.ui.components.MediCard
import com.example.mediminder.ui.components.MedicineImage
import com.example.mediminder.ui.components.QuietButton
import com.example.mediminder.ui.components.SecondaryButton
import com.example.mediminder.ui.components.SectionTitle
import com.example.mediminder.ui.components.StatusChip
import com.example.mediminder.ui.components.entrance
import com.example.mediminder.ui.components.rememberEntranceState
import com.example.mediminder.util.Formats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    db: MediMinderDatabase,
    private val doseActions: DoseActions,
    private val repository: MedicineRepository,
) : ViewModel() {
    /** Days back to show; 0 = everything. */
    val period = MutableStateFlow(30)
    val medicine = MutableStateFlow<Long?>(null)
    private var initialised = false

    val medicines: StateFlow<List<Medication>> = db.doseLogDao().observeMedicinesWithHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val entries: StateFlow<List<HistoryEntry>?> = combine(period, medicine) { p, m -> p to m }
        .flatMapLatest { (p, m) ->
            val from = if (p == 0) 0L else LocalDate.now().toEpochDay() - (p - 1)
            db.doseLogDao().observeHistory(from, m)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Opened from a medicine's page: start filtered to that medicine. */
    fun start(medicationId: Long) {
        if (initialised) return
        initialised = true
        if (medicationId != 0L) medicine.value = medicationId
    }

    /** "Recorded by mistake": removes the entry (stock goes back for a taken dose). */
    fun remove(entry: HistoryEntry) = viewModelScope.launch {
        doseActions.undoLog(entry.log.id)
        FeedbackBus.show(FeedbackKind.DELETED, R.string.feedback_entry_removed)
    }

    /** For a medicine already deleted (history kept): now remove the history too. */
    fun eraseHistory(medicationId: Long) = viewModelScope.launch {
        repository.delete(medicationId, keepHistory = false)
        medicine.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(medicationId: Long, onBack: () -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    LaunchedEffect(medicationId) { viewModel.start(medicationId) }
    val context = LocalContext.current
    val period by viewModel.period.collectAsState()
    val selected by viewModel.medicine.collectAsState()
    val medicines by viewModel.medicines.collectAsState()
    val entries by viewModel.entries.collectAsState()
    var sheetFor by remember { mutableStateOf<HistoryEntry?>(null) }
    var removing by remember { mutableStateOf<HistoryEntry?>(null) }
    var erasing by remember { mutableStateOf<Medication?>(null) }
    val entrance = rememberEntranceState()

    removing?.let { entry ->
        ConfirmDialog(
            title = stringResource(R.string.history_remove_title),
            text = stringResource(R.string.history_remove_body),
            confirmLabel = stringResource(R.string.history_remove),
            destructive = true,
            onConfirm = { viewModel.remove(entry) },
            onDismiss = { removing = null },
        )
    }
    erasing?.let { m ->
        ConfirmDialog(
            title = stringResource(R.string.history_erase_title, m.name),
            text = stringResource(R.string.history_erase_body),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { viewModel.eraseHistory(m.id) },
            onDismiss = { erasing = null },
        )
    }
    sheetFor?.let { entry ->
        ModalBottomSheet(onDismissRequest = { sheetFor = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(entryTitle(context, entry), style = MaterialTheme.typography.headlineSmall)
                Text(dayLabel(context, entry.log.scheduledDay), style = MaterialTheme.typography.bodyLarge)
                Text(entryDetail(context, entry), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                entry.log.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                QuietButton(
                    stringResource(R.string.history_remove),
                    onClick = { removing = entry; sheetFor = null },
                    icon = Icons.Default.Delete,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = stringResource(R.string.history_title), onBack = onBack) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(stringResource(R.string.history_intro), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.padding(4.dp))
                ChoiceChips(
                    listOf(7, 30, 90, 0), period,
                    { n -> if (n == 0) stringResource(R.string.history_all_time) else Formats.digits(context, context.resources.getQuantityString(R.plurals.days_count, n, n)) },
                    { viewModel.period.value = it },
                )
            }
            if (medicines.size > 1) item {
                ChoiceChips(
                    listOf<Long?>(null) + medicines.map { it.id },
                    selected,
                    { id ->
                        val m = medicines.firstOrNull { it.id == id }
                        when {
                            m == null -> stringResource(R.string.history_all_medicines)
                            m.deletedAt != null -> stringResource(R.string.history_deleted_name, m.name)
                            else -> m.name
                        }
                    },
                    { viewModel.medicine.value = it },
                )
            }
            val list = entries ?: return@LazyColumn
            if (list.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.History,
                        title = stringResource(R.string.history_empty_title),
                        body = stringResource(R.string.history_empty_body),
                        hue = Hue.VIOLET,
                        art = Art.CHECKLIST,
                    )
                }
                return@LazyColumn
            }
            item {
                val taken = list.count { it.log.status == DoseStatus.TAKEN }
                Text(
                    stringResource(R.string.history_summary, Formats.number(context, animatedCount(taken)), Formats.number(context, animatedCount(list.size - taken))),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            var shown = 0
            list.groupBy { it.log.scheduledDay }.forEach { (day, dayEntries) ->
                item(key = "day-$day") { SectionTitle(dayLabel(context, day)) }
                val first = shown   // stagger by position on screen, worked out up front
                shown += dayEntries.size
                itemsIndexed(dayEntries, key = { _, e -> e.log.id }) { i, entry ->
                    HistoryRow(entry, onClick = { sheetFor = entry }, modifier = Modifier.animateItem().entrance(entrance, first + i))
                }
            }
            item {
                Spacer(Modifier.padding(4.dp))
                SecondaryButton(stringResource(R.string.history_share), onClick = { share(context, list) }, icon = Icons.Default.Share)
                // A deleted medicine whose history was kept: offer to remove that too
                medicines.firstOrNull { it.id == selected && it.deletedAt != null }?.let { m ->
                    QuietButton(
                        stringResource(R.string.history_erase),
                        onClick = { erasing = m },
                        icon = Icons.Default.Delete,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    MediCard(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MedicineImage(entry.photoUri, entry.name, entry.color, size = 48)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(listOf(entry.name, entry.strength).filter { it.isNotBlank() }.joinToString(" "), style = MaterialTheme.typography.titleMedium)
                if (entry.deletedAt != null) {
                    Text(stringResource(R.string.history_deleted_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(entryDetail(context, entry), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            StatusChip(if (entry.log.status == DoseStatus.TAKEN) DoseState.TAKEN else DoseState.SKIPPED)
        }
    }
}

private fun entryTitle(context: Context, e: HistoryEntry): String {
    val name = listOf(e.name, e.strength).filter { it.isNotBlank() }.joinToString(" ")
    return if (e.deletedAt != null) context.getString(R.string.history_deleted_name, name) else name
}

/** "Taken at 8:40 AM · 1 tablet · 8:00 AM dose" — the dose time only when it adds something. */
private fun entryDetail(context: Context, e: HistoryEntry): String = buildList {
    val takenMinute = minuteOf(e.log.loggedAt.toLocalDateTime().toLocalTime())
    val taken = e.log.status == DoseStatus.TAKEN
    if (taken) add(context.getString(R.string.today_taken_at, Formats.time(context, takenMinute)))
    add(Formats.quantity(context, e.log.quantity, e.unit))
    val dose = e.log.scheduledMinute
    when {
        dose == null -> add(context.getString(R.string.history_when_needed))
        !taken || kotlin.math.abs(takenMinute - dose) > 5 -> add(context.getString(R.string.history_dose_at, Formats.time(context, dose)))
    }
}.joinToString(" · ")

private fun dayLabel(context: Context, epochDay: Long): String {
    val today = LocalDate.now().toEpochDay()
    return when (epochDay) {
        today -> context.getString(R.string.history_today)
        today - 1 -> context.getString(R.string.history_yesterday)
        else -> Formats.digits(context, LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Formats.locale(context))))
    }
}

/** A plain-text record for the doctor or family (WhatsApp, imo, email…). */
private fun share(context: Context, entries: List<HistoryEntry>) {
    val text = buildString {
        appendLine(context.getString(R.string.history_title))
        entries.groupBy { it.log.scheduledDay }.forEach { (day, dayEntries) ->
            appendLine()
            appendLine(dayLabel(context, day))
            dayEntries.forEach { e ->
                val status = context.getString(if (e.log.status == DoseStatus.TAKEN) R.string.state_taken else R.string.state_skipped)
                appendLine("• ${entryTitle(context, e)} — $status (${entryDetail(context, e)})")
            }
        }
        appendLine()
        append(context.getString(R.string.stats_report_footer))
    }
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
}
