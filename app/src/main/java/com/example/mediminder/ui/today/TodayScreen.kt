package com.example.mediminder.ui.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import java.time.LocalDate
import com.example.mediminder.reminders.Reliability
import com.example.mediminder.ui.components.Banner
import com.example.mediminder.ui.components.EmptyState
import com.example.mediminder.ui.components.PillAvatar
import com.example.mediminder.ui.components.StatusChip
import com.example.mediminder.ui.components.stateLabel
import com.example.mediminder.ui.components.TimelineWheel
import com.example.mediminder.ui.components.WheelItem
import com.example.mediminder.ui.components.parseColor
import com.example.mediminder.ui.components.stateColor
import com.example.mediminder.util.Formats
import java.time.LocalTime

@Composable
fun TodayScreen(
    modifier: Modifier,
    onAddMedicine: () -> Unit,
    onOpenMedicine: (Long) -> Unit,
    onOpenRefills: () -> Unit,
    onOpenReliability: () -> Unit,
    onOpenAppointments: () -> Unit,
    onOpenStats: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val ui by viewModel.state.collectAsState()
    var reliabilityOk by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reliabilityOk = Reliability.allCriticalOk(context) }

    val snackbar = remember { SnackbarHostState() }
    var limitDialog by remember { mutableStateOf<TodayEvent.LimitReached?>(null) }
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
            title = { Text(stringResource(R.string.today_prn_limit_title)) },
            text = {
                Text(stringResource(R.string.today_prn_limit_body, event.medication.name, Formats.number(context, event.takenToday), Formats.number(context, event.limit)))
            },
            confirmButton = { TextButton(onClick = { viewModel.takeAsNeeded(event.medication, override = true); limitDialog = null }) { Text(stringResource(R.string.today_prn_limit_take_anyway)) } },
            dismissButton = { TextButton(onClick = { limitDialog = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    val state = ui ?: return
    androidx.compose.foundation.layout.Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header(state, onPrev = viewModel::previousDay, onNext = viewModel::nextDay, onToday = viewModel::goToToday) }

            if (!state.hasMedicines) {
                item {
                    EmptyState(
                        icon = Icons.Default.MedicalServices,
                        title = stringResource(R.string.today_empty_title),
                        body = stringResource(R.string.today_empty_body),
                    ) {
                        Button(onClick = onAddMedicine) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_add_medicine))
                        }
                    }
                }
                return@LazyColumn
            }

            item { ProgressCard(state, onOpenStats) }

            if (!reliabilityOk) item {
                Banner(
                    text = stringResource(R.string.today_reliability_banner),
                    icon = Icons.Default.Notifications,
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                    actionLabel = stringResource(R.string.action_fix),
                    onClick = onOpenReliability,
                )
            }
            if (state.lowStock > 0) item {
                Banner(
                    text = stringResource(R.string.today_low_stock_banner, Formats.number(context, state.lowStock)),
                    icon = Icons.Default.ShoppingCart,
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                    actionLabel = stringResource(R.string.action_view),
                    onClick = onOpenRefills,
                )
            }
            state.nextAppointment?.let { appt ->
                item {
                    val at = appt.dateTime.toLocalDateTime()
                    Banner(
                        text = stringResource(
                            R.string.today_next_appointment,
                            appt.doctorName,
                            Formats.shortDate(context, at.toLocalDate()) + ", " + Formats.time(context, minuteOf(at.toLocalTime())),
                        ),
                        icon = Icons.Default.CalendarMonth,
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
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

            val canAct = !state.date.isAfter(java.time.LocalDate.now())
            state.rows.groupBy { dayPartOf(it.minuteOfDay) }.toSortedMap().forEach { (part, rows) ->
                item(key = "part-$part") { DayPartHeader(part) }
                items(rows, key = { "${it.scheduleId}:${it.day}" }) { row ->
                    DoseCard(
                        row = row,
                        canAct = canAct,
                        canSnooze = state.isToday,
                        onTake = { viewModel.take(row) },
                        onSkip = { viewModel.skip(row) },
                        onSnooze = { viewModel.snooze(row) },
                        onUndo = { viewModel.undo(row) },
                        onOpen = { onOpenMedicine(row.medicationId) },
                    )
                }
            }

            if (state.asNeeded.isNotEmpty() && state.isToday) {
                item { Text(stringResource(R.string.today_as_needed), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                items(state.asNeeded, key = { "prn-${it.id}" }) { med ->
                    Card(Modifier.fillMaxWidth().clickable { onOpenMedicine(med.id) }, shape = RoundedCornerShape(20.dp)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            PillAvatar(med.name, med.color, size = 40)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(listOf(med.name, med.strength).filter { it.isNotBlank() }.joinToString(" "), fontWeight = FontWeight.SemiBold)
                                val taken = state.asNeededTakenToday[med.id] ?: 0
                                Text(
                                    if (med.maxDailyDoses != null) stringResource(R.string.today_prn_count_limit, Formats.number(context, taken), Formats.number(context, med.maxDailyDoses))
                                    else stringResource(R.string.today_prn_count, Formats.number(context, taken)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            FilledTonalButton(onClick = { viewModel.takeAsNeeded(med) }) { Text(stringResource(R.string.action_take_now)) }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Header(state: TodayUi, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    val context = LocalContext.current
    val hour = LocalTime.now().hour
    val greeting = stringResource(
        when (hour) {
            in 4..11 -> R.string.greeting_morning
            in 12..16 -> R.string.greeting_afternoon
            in 17..20 -> R.string.greeting_evening
            else -> R.string.greeting_night
        }
    )
    Column {
        Text(
            if (state.userName.isBlank()) greeting else "$greeting, ${state.userName}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.cd_previous_day)) }
            Text(
                if (state.isToday) stringResource(R.string.today_label, Formats.shortDate(context, state.date)) else Formats.shortDate(context, state.date),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.cd_next_day)) }
        }
        if (!state.isToday) {
            TextButton(onClick = onToday, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.action_back_to_today)) }
        }
    }
}

@Composable
private fun ProgressCard(state: TodayUi, onOpenStats: () -> Unit) {
    val context = LocalContext.current
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onOpenStats),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            TimelineWheel(
                items = state.rows.map { WheelItem("${it.scheduleId}", it.minuteOfDay, stateColor(it.state)) },
                nowMinute = if (state.isToday) state.nowMinute else null,
                modifier = Modifier.size(150.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${Formats.number(context, state.taken)}/${Formats.number(context, state.total)}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(stringResource(R.string.today_taken_label), style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatLine(Icons.Default.LocalFireDepartment, stringResource(R.string.today_streak, Formats.number(context, state.streak)))
                StatLine(Icons.Default.Star, stringResource(R.string.today_points, Formats.number(context, state.points)))
                // The next time something rings: a due/upcoming dose, or a snoozed one coming back
                val next = state.rows.mapNotNull { row ->
                    when (row.state) {
                        DoseState.DUE, DoseState.UPCOMING -> LocalDate.ofEpochDay(row.day).atTime(timeOf(row.minuteOfDay))
                        DoseState.SNOOZED -> row.snoozedUntil?.toLocalDateTime() ?: LocalDate.ofEpochDay(row.day).atTime(timeOf(row.minuteOfDay))
                        else -> null
                    }
                }.minOrNull()
                if (next != null && state.isToday) {
                    StatLine(Icons.Default.Alarm, stringResource(R.string.today_next_dose, Formats.time(context, minuteOf(next.toLocalTime()))))
                } else if (state.total > 0 && state.taken == state.total) {
                    StatLine(Icons.Default.Check, stringResource(R.string.today_all_done))
                }
            }
        }
    }
}

@Composable
private fun StatLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DayPartHeader(part: DayPart) {
    Text(
        stringResource(
            when (part) {
                DayPart.MORNING -> R.string.part_morning
                DayPart.AFTERNOON -> R.string.part_afternoon
                DayPart.EVENING -> R.string.part_evening
                DayPart.NIGHT -> R.string.part_night
            }
        ),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun DoseCard(
    row: DoseRow,
    canAct: Boolean,
    canSnooze: Boolean,
    onTake: () -> Unit,
    onSkip: () -> Unit,
    onSnooze: () -> Unit,
    onUndo: () -> Unit,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    val answered = row.state == DoseState.TAKEN || row.state == DoseState.SKIPPED
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = if (row.ringing) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (answered) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (answered) 0.dp else 2.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onOpen)) {
                PillAvatar(row.name, row.color, size = 40)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        listOf(row.name, row.strength).filter { it.isNotBlank() }.joinToString(" "),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        buildList {
                            add(Formats.quantity(context, row.quantity, row.unit))
                            if (row.mealTiming != MealTiming.ANY) add(Formats.mealTiming(context, row.mealTiming))
                        }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (row.alertType == AlertType.ALARM) {
                            Icon(Icons.Default.Alarm, contentDescription = stringResource(R.string.cd_alarm), modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(Formats.time(context, row.minuteOfDay), style = MaterialTheme.typography.labelLarge, color = parseColor(row.color))
                    }
                }
                val snoozedTo = row.snoozedUntil?.takeIf { row.state == DoseState.SNOOZED }
                StatusChip(
                    row.state,
                    if (snoozedTo != null) stringResource(R.string.state_snoozed_until, Formats.time(context, minuteOf(snoozedTo.toLocalDateTime().toLocalTime())))
                    else stateLabel(row.state),
                )
            }
            if (canAct) {
                Spacer(Modifier.height(10.dp))
                if (answered) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val at = row.loggedAt?.toLocalDateTime()
                        Text(
                            if (row.state == DoseState.TAKEN && at != null) stringResource(R.string.today_taken_at, Formats.time(context, minuteOf(at.toLocalTime())))
                            else stringResource(R.string.state_skipped),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onUndo) { Text(stringResource(R.string.action_undo)) }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = onTake, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_taken))
                        }
                        if (canSnooze && row.state != DoseState.UPCOMING && row.state != DoseState.MISSED) {
                            OutlinedButton(onClick = onSnooze) { Text(stringResource(R.string.action_snooze_short)) }
                        }
                        TextButton(onClick = onSkip) { Text(stringResource(R.string.action_skip)) }
                    }
                }
            }
        }
    }
}
