package com.example.mediminder.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.mediminder.R
import com.example.mediminder.data.local.entity.AlertType
import com.example.mediminder.data.local.entity.MealTiming
import com.example.mediminder.domain.DayPart
import com.example.mediminder.domain.DoseState
import com.example.mediminder.domain.dayPartOf
import com.example.mediminder.domain.minuteOf
import com.example.mediminder.domain.timeOf
import com.example.mediminder.domain.toLocalDateTime
import com.example.mediminder.reminders.Reliability
import com.example.mediminder.ui.components.EmptyState
import com.example.mediminder.ui.components.MediCard
import com.example.mediminder.ui.components.NoticeCard
import com.example.mediminder.ui.components.PillAvatar
import com.example.mediminder.ui.components.PrimaryButton
import com.example.mediminder.ui.components.QuietButton
import com.example.mediminder.ui.components.SecondaryButton
import com.example.mediminder.ui.components.StatusChip
import com.example.mediminder.ui.components.TakenButton
import com.example.mediminder.ui.components.Tone
import com.example.mediminder.ui.components.stateLabel
import com.example.mediminder.ui.theme.LocalStatusColors
import com.example.mediminder.util.Formats
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    modifier: Modifier,
    onAddMedicine: () -> Unit,
    onOpenMedicine: (Long) -> Unit,
    onOpenRefills: () -> Unit,
    onOpenReliability: () -> Unit,
    onOpenAppointments: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val ui by viewModel.state.collectAsState()
    var reliabilityOk by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reliabilityOk = Reliability.allCriticalOk(context) }

    val snackbar = remember { SnackbarHostState() }
    var limitDialog by remember { mutableStateOf<TodayEvent.LimitReached?>(null) }
    var sheetFor by remember { mutableStateOf<DoseRow?>(null) }
    val loggedText = stringResource(R.string.today_prn_logged)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TodayEvent.LimitReached -> limitDialog = event
                is TodayEvent.AsNeededLogged -> snackbar.showSnackbar(loggedText.format(event.name))
            }
        }
    }

    limitDialog?.let { event ->
        AlertDialog(
            onDismissRequest = { limitDialog = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text(stringResource(R.string.today_prn_limit_title), style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    stringResource(R.string.today_prn_limit_body, event.medication.name, Formats.number(context, event.takenToday), Formats.number(context, event.limit)),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.takeAsNeeded(event.medication, override = true); limitDialog = null }) { Text(stringResource(R.string.today_prn_limit_take_anyway)) } },
            dismissButton = { TextButton(onClick = { limitDialog = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    val state = ui ?: return

    sheetFor?.let { row ->
        // Re-read the row so the sheet shows the latest state
        val live = state.rows.firstOrNull { it.scheduleId == row.scheduleId && it.day == row.day } ?: row
        DoseOptionsSheet(
            row = live,
            canAct = !state.date.isAfter(LocalDate.now()),
            canSnooze = state.isToday && live.state != DoseState.UPCOMING,
            snoozeMinutes = state.snoozeMinutes,
            onDismiss = { sheetFor = null },
            onTake = { viewModel.take(live); sheetFor = null },
            onSnooze = { viewModel.snooze(live); sheetFor = null },
            onSkip = { viewModel.skip(live); sheetFor = null },
            onUndo = { viewModel.undo(live); sheetFor = null },
            onDetails = { sheetFor = null; onOpenMedicine(live.medicationId) },
        )
    }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Header(state, onPrev = viewModel::previousDay, onNext = viewModel::nextDay, onToday = viewModel::goToToday) }

            if (!state.hasMedicines) {
                item {
                    EmptyState(
                        icon = Icons.Default.MedicalServices,
                        title = stringResource(R.string.today_empty_title),
                        body = stringResource(R.string.today_empty_body),
                    ) { PrimaryButton(stringResource(R.string.action_add_medicine), onAddMedicine, icon = Icons.Default.Add) }
                }
                return@LazyColumn
            }

            if (!reliabilityOk) item {
                NoticeCard(
                    text = stringResource(R.string.today_reliability_banner),
                    icon = Icons.Default.NotificationsOff,
                    tone = Tone.DANGER,
                    actionLabel = stringResource(R.string.action_fix),
                    onClick = onOpenReliability,
                )
            }

            if (state.rows.isNotEmpty()) item { SummaryCard(state) }

            if (state.lowStock > 0) item {
                NoticeCard(
                    text = stringResource(R.string.today_low_stock_banner, Formats.number(context, state.lowStock)),
                    icon = Icons.Default.ShoppingCart,
                    tone = Tone.WARNING,
                    onClick = onOpenRefills,
                )
            }
            state.nextAppointment?.takeIf { state.isToday }?.let { appt ->
                item {
                    val at = appt.dateTime.toLocalDateTime()
                    NoticeCard(
                        text = stringResource(
                            R.string.today_next_appointment,
                            appt.doctorName,
                            Formats.shortDate(context, at.toLocalDate()) + ", " + Formats.time(context, minuteOf(at.toLocalTime())),
                        ),
                        icon = Icons.Default.CalendarMonth,
                        tone = Tone.INFO,
                        onClick = onOpenAppointments,
                    )
                }
            }

            if (state.rows.isEmpty() && state.asNeeded.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.today_nothing_scheduled),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                }
            }

            state.rows.groupBy { dayPartOf(it.minuteOfDay) }.toSortedMap().forEach { (part, rows) ->
                item(key = "part-$part") { DayPartHeader(part) }
                items(rows, key = { "${it.scheduleId}:${it.day}" }) { row ->
                    DoseCard(
                        row = row,
                        actionable = state.isToday && row.state in ACTIONABLE,
                        onTake = { viewModel.take(row) },
                        onOptions = { sheetFor = row },
                    )
                }
            }

            if (state.asNeeded.isNotEmpty() && state.isToday) {
                item { SectionHeading(Icons.Default.MedicalServices, stringResource(R.string.today_as_needed)) }
                items(state.asNeeded, key = { "prn-${it.id}" }) { med ->
                    MediCard(onClick = { onOpenMedicine(med.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PillAvatar(med.name, med.color, size = 44)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(listOf(med.name, med.strength).filter { it.isNotBlank() }.joinToString(" "), style = MaterialTheme.typography.titleLarge)
                                val taken = state.asNeededTakenToday[med.id] ?: 0
                                Text(
                                    if (med.maxDailyDoses != null) stringResource(R.string.today_prn_count_limit, Formats.number(context, taken), Formats.number(context, med.maxDailyDoses))
                                    else stringResource(R.string.today_prn_count, Formats.number(context, taken)),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        SecondaryButton(stringResource(R.string.today_prn_took), onClick = { viewModel.takeAsNeeded(med) }, icon = Icons.Default.Add)
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

private val ACTIONABLE = setOf(DoseState.DUE, DoseState.SNOOZED, DoseState.MISSED)

@Composable
private fun Header(state: TodayUi, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    val context = LocalContext.current
    val greeting = stringResource(
        when (LocalTime.now().hour) {
            in 4..11 -> R.string.greeting_morning
            in 12..16 -> R.string.greeting_afternoon
            in 17..20 -> R.string.greeting_evening
            else -> R.string.greeting_night
        }
    )
    val date = Formats.digits(context, state.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Formats.locale(context))))
    Column(Modifier.padding(top = 12.dp)) {
        Text(
            if (state.userName.isBlank()) greeting else "$greeting, ${state.userName}",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (state.isToday) stringResource(R.string.today_label, date) else date,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            IconButton(onClick = onPrev, modifier = Modifier.size(52.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.cd_previous_day), modifier = Modifier.size(30.dp))
            }
            IconButton(onClick = onNext, modifier = Modifier.size(52.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.cd_next_day), modifier = Modifier.size(30.dp))
            }
        }
        if (!state.isToday) {
            SecondaryButton(stringResource(R.string.action_back_to_today), onClick = onToday, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** "2 of 3 taken" with a thick bar, and what comes next. */
@Composable
private fun SummaryCard(state: TodayUi) {
    val context = LocalContext.current
    val status = LocalStatusColors.current
    val allDone = state.total > 0 && state.taken == state.total
    val next = state.rows.mapNotNull { row ->
        when (row.state) {
            DoseState.DUE, DoseState.UPCOMING -> LocalDate.ofEpochDay(row.day).atTime(timeOf(row.minuteOfDay)) to row
            DoseState.SNOOZED -> (row.snoozedUntil?.toLocalDateTime() ?: LocalDate.ofEpochDay(row.day).atTime(timeOf(row.minuteOfDay))) to row
            else -> null
        }
    }.minByOrNull { it.first }

    MediCard(
        containerColor = if (allDone) status.takenContainer else MaterialTheme.colorScheme.primaryContainer,
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0f),
    ) {
        val onColor = if (allDone) status.onTakenContainer else MaterialTheme.colorScheme.onPrimaryContainer
        Text(
            stringResource(R.string.today_summary, Formats.number(context, state.taken), Formats.number(context, state.total)),
            style = MaterialTheme.typography.headlineSmall,
            color = onColor,
        )
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { if (state.total == 0) 0f else state.taken / state.total.toFloat() },
            modifier = Modifier.fillMaxWidth().height(14.dp).clip(CircleShape),
            color = status.taken,
            trackColor = MaterialTheme.colorScheme.surface,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        if (state.isToday && (allDone || next != null)) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (allDone) Icons.Default.CheckCircle else Icons.Default.Alarm, contentDescription = null, tint = onColor, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    if (allDone) stringResource(R.string.today_all_done)
                    else stringResource(R.string.today_next_named, next!!.second.name, Formats.time(context, minuteOf(next.first.toLocalTime()))),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = onColor,
                )
            }
        }
    }
}

@Composable
private fun SectionHeading(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.padding(top = 10.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun DayPartHeader(part: DayPart) {
    val (icon, label) = when (part) {
        DayPart.MORNING -> Icons.Default.WbSunny to R.string.part_morning
        DayPart.AFTERNOON -> Icons.Default.WbSunny to R.string.part_afternoon
        DayPart.EVENING -> Icons.Default.WbTwilight to R.string.part_evening
        DayPart.NIGHT -> Icons.Default.Bedtime to R.string.part_night
    }
    SectionHeading(icon, stringResource(label))
}

@Composable
private fun doseDetail(row: DoseRow): String {
    val context = LocalContext.current
    return buildList {
        add(Formats.quantity(context, row.quantity, row.unit))
        if (row.mealTiming != MealTiming.ANY) add(Formats.mealTiming(context, row.mealTiming))
    }.joinToString(" · ")
}

@Composable
private fun DoseCard(row: DoseRow, actionable: Boolean, onTake: () -> Unit, onOptions: () -> Unit) {
    val context = LocalContext.current
    val status = LocalStatusColors.current
    val answered = row.state == DoseState.TAKEN || row.state == DoseState.SKIPPED
    val accent = when {
        row.state == DoseState.MISSED -> status.missed
        row.state == DoseState.DUE || row.ringing -> status.due
        else -> null
    }
    val chipLabel = when {
        row.state == DoseState.TAKEN && row.loggedAt != null ->
            stringResource(R.string.today_taken_at, Formats.time(context, minuteOf(row.loggedAt.toLocalDateTime().toLocalTime())))
        row.state == DoseState.SNOOZED && row.snoozedUntil != null ->
            stringResource(R.string.state_snoozed_until, Formats.time(context, minuteOf(row.snoozedUntil.toLocalDateTime().toLocalTime())))
        else -> stateLabel(row.state)
    }

    MediCard(
        onClick = onOptions,
        containerColor = if (answered) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface,
        borderColor = accent ?: MaterialTheme.colorScheme.outlineVariant,
        borderWidth = if (accent != null) 2.dp else 1.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (row.alertType == AlertType.ALARM) {
                Icon(Icons.Default.Alarm, contentDescription = stringResource(R.string.cd_alarm), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                Formats.time(context, row.minuteOfDay),
                style = MaterialTheme.typography.titleLarge,
                color = if (answered) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            StatusChip(row.state, chipLabel)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PillAvatar(row.name, row.color, size = 44)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(listOf(row.name, row.strength).filter { it.isNotBlank() }.joinToString(" "), style = MaterialTheme.typography.titleLarge)
                Text(doseDetail(row), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (actionable) {
            Spacer(Modifier.height(16.dp))
            TakenButton(onClick = onTake)
            QuietButton(
                stringResource(R.string.dose_snooze_or_skip),
                onClick = onOptions,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DoseOptionsSheet(
    row: DoseRow,
    canAct: Boolean,
    canSnooze: Boolean,
    snoozeMinutes: Int,
    onDismiss: () -> Unit,
    onTake: () -> Unit,
    onSnooze: () -> Unit,
    onSkip: () -> Unit,
    onUndo: () -> Unit,
    onDetails: () -> Unit,
) {
    val context = LocalContext.current
    val answered = row.state == DoseState.TAKEN || row.state == DoseState.SKIPPED
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(listOf(row.name, row.strength).filter { it.isNotBlank() }.joinToString(" "), style = MaterialTheme.typography.headlineSmall)
            Text(
                Formats.time(context, row.minuteOfDay) + " · " + doseDetail(row),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            if (canAct && !answered) {
                TakenButton(onClick = onTake, text = stringResource(R.string.dose_sheet_take))
                if (canSnooze) SecondaryButton(stringResource(R.string.dose_sheet_snooze, Formats.number(context, snoozeMinutes)), onSnooze, icon = Icons.Default.Snooze)
                SecondaryButton(stringResource(R.string.dose_sheet_skip), onSkip, icon = Icons.Default.Block, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (canAct && answered) {
                SecondaryButton(stringResource(R.string.dose_sheet_undo), onUndo, icon = Icons.AutoMirrored.Filled.Undo)
            }
            QuietButton(stringResource(R.string.dose_sheet_details), onDetails, icon = Icons.Default.Info, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}
