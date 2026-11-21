package com.example.mediminder.ui.today

import com.example.mediminder.domain.CareKind
import com.example.mediminder.util.CareText
import androidx.compose.material.icons.filled.Autorenew
import com.example.mediminder.reminders.CheckResult
import com.example.mediminder.reminders.SetupNudge
import com.example.mediminder.ui.components.FeedbackBus
import com.example.mediminder.ui.components.FeedbackKind
import com.example.mediminder.ui.components.SetupCard
import com.example.mediminder.ui.components.rememberSetupFixer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.rememberCoroutineScope
import com.example.mediminder.ui.components.AnimatedArt
import java.time.Duration
import java.time.LocalDateTime
import kotlinx.coroutines.launch
import com.example.mediminder.ui.components.Art
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.mediminder.R
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Remove
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.mediminder.ui.components.BurstEffect
import com.example.mediminder.ui.components.IconBadge
import com.example.mediminder.ui.components.ProgressRing
import com.example.mediminder.ui.components.SkyHeader
import com.example.mediminder.ui.components.SkyScene
import com.example.mediminder.ui.components.popIn
import com.example.mediminder.ui.components.pressScale
import com.example.mediminder.ui.components.skyContentColor
import com.example.mediminder.ui.components.skyContentSoftColor
import com.example.mediminder.ui.theme.Hue
import com.example.mediminder.ui.components.rememberPulse
import com.example.mediminder.ui.components.rememberEntranceState
import com.example.mediminder.ui.components.entrance
import com.example.mediminder.ui.components.RollingContent
import com.example.mediminder.ui.components.Motion
import com.example.mediminder.ui.components.CelebrationBurst
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import com.example.mediminder.ui.photos.MedicinePhoto
import com.example.mediminder.ui.components.MedicineImage
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
    onRecord: (scheduleId: Long, day: Long) -> Unit = { _, _ -> },
    onOpenCare: (kind: CareKind, id: Long) -> Unit = { _, _ -> },
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val ui by viewModel.state.collectAsState()
    // Phone settings that stop reminders: a friendly card until fixed ("later" hides it for a day)
    var setupProblems by remember { mutableStateOf(emptyList<CheckResult>()) }
    var setupLater by remember { mutableStateOf(false) }
    var hadSetupProblems by remember { mutableStateOf(false) }
    fun recheckSetup() {
        setupProblems = Reliability.criticalProblems(context)
        setupLater = SetupNudge.isSnoozed(context)
        if (setupProblems.isEmpty() && hadSetupProblems) FeedbackBus.show(FeedbackKind.SAVED, R.string.setup_all_set)
        hadSetupProblems = setupProblems.isNotEmpty()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { recheckSetup() }
    val setupFixer = rememberSetupFixer { recheckSetup() }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val undoLabel = stringResource(R.string.action_undo)
    // A slip of the thumb is easy to put right: every Taken / Skip offers Undo for a few seconds
    fun offerUndo(row: DoseRow, taken: Boolean) {
        val name = listOf(CareText.name(context, row.kind, row.subtype, row.name), row.strength).filter { it.isNotBlank() }.joinToString(" ")
        val message = resources.getString(
            when {
                !taken -> R.string.today_undo_skipped
                CareText.isOral(row.kind) -> R.string.today_undo_taken
                else -> R.string.today_undo_done
            },
            name,
        )
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                viewModel.undo(row)
            }
        }
    }
    // A health check is answered by writing the reading down; everything else is one tap
    fun answer(row: DoseRow) {
        if (row.kind == CareKind.MEASURE) onRecord(row.scheduleId, row.day)
        else { viewModel.take(row); offerUndo(row, taken = true) }
    }
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
            onTake = { answer(live); sheetFor = null },
            onSnooze = { viewModel.snooze(live); sheetFor = null },
            onSkip = { viewModel.skip(live); offerUndo(live, taken = false); sheetFor = null },
            onUndo = { viewModel.undo(live); sheetFor = null },
            onDetails = {
                sheetFor = null
                if (live.kind.isMedicine) onOpenMedicine(live.medicationId) else onOpenCare(live.kind, live.medicationId)
            },
        )
    }

    // Moving to another day lets that day's doses arrive afresh
    val entrance = rememberEntranceState(state.date)
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Header(state, onPrev = viewModel::previousDay, onNext = viewModel::nextDay, onToday = viewModel::goToToday) }

            // shown even before any medicine is added, so the first one works properly
            if (setupProblems.isNotEmpty() && !setupLater) item(key = "setup") {
                SetupCard(
                    problems = setupProblems,
                    onFix = setupFixer,
                    onLater = { SetupNudge.snooze(context); setupLater = true },
                    onDetails = onOpenReliability,
                    modifier = Modifier.animateItem(),
                )
            }
            if (!state.hasMedicines) {
                item {
                    EmptyState(
                        icon = Icons.Default.Medication,
                        title = stringResource(R.string.today_empty_title),
                        body = stringResource(R.string.today_empty_body),
                        hue = Hue.CORAL,
                        art = Art.PILLS,
                    ) { PrimaryButton(stringResource(R.string.action_add_medicine), onAddMedicine, icon = Icons.Default.Add) }
                }
                return@LazyColumn
            }


            if (state.rows.isNotEmpty()) item { SummaryCard(state, Modifier.entrance(entrance, 0)) }

            if (state.lowStock > 0) item {
                NoticeCard(
                    text = stringResource(R.string.today_low_stock_banner, Formats.number(context, state.lowStock)),
                    icon = Icons.Default.ShoppingCart,
                    tone = Tone.WARNING,
                    onClick = onOpenRefills,
                )
            }
            if (state.isToday) items(state.replace, key = { "replace-${it.id}" }) { m ->
                NoticeCard(
                    text = stringResource(
                        R.string.today_replace_banner,
                        m.name,
                        Formats.number(context, (LocalDate.now().toEpochDay() - (m.openedAt ?: 0L)).toInt()),
                    ),
                    icon = Icons.Default.Autorenew,
                    tone = Tone.WARNING,
                    onClick = { onOpenMedicine(m.id) },
                    modifier = Modifier.animateItem(),
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
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedArt(Art.TEA, Hue.SUN, size = 156.dp)
                        Text(
                            stringResource(R.string.today_nothing_scheduled),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            var shown = 1
            state.rows.groupBy { dayPartOf(it.minuteOfDay) }.toSortedMap().forEach { (part, rows) ->
                item(key = "part-$part") { DayPartHeader(part) }
                val first = shown   // stagger by position, worked out up front
                shown += rows.size
                itemsIndexed(rows, key = { _, it -> "${it.scheduleId}:${it.day}" }) { i, row ->
                    DoseCard(
                        row = row,
                        actionable = state.isToday && row.state in ACTIONABLE,
                        onTake = { answer(row) },
                        onOptions = { sheetFor = row },
                        modifier = Modifier.animateItem().entrance(entrance, first + i),
                    )
                }
            }

            if (state.asNeeded.isNotEmpty() && state.isToday) {
                item { SectionHeading(Icons.Default.MedicalServices, stringResource(R.string.today_as_needed), Hue.TEAL) }
                items(state.asNeeded, key = { "prn-${it.id}" }) { med ->
                    MediCard(onClick = { onOpenMedicine(med.id) }, modifier = Modifier.animateItem()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MedicineImage(med.photoUri, med.name, med.color, size = 56)
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
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter)) { data -> UndoBar(data) }
    }
}

private val ACTIONABLE = setOf(DoseState.DUE, DoseState.SNOOZED, DoseState.MISSED)

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
    val scene = SkyScene.at(hour)
    val ink = skyContentColor(scene)
    Column(Modifier.padding(top = 8.dp)) {
        SkyHeader(scene) {
            Text(
                if (state.userName.isBlank()) greeting else "$greeting, ${state.userName}",
                style = MaterialTheme.typography.headlineMedium,
                color = ink,
                modifier = Modifier.fillMaxWidth(0.68f),
            )
            Spacer(Modifier.height(4.dp))
            AnimatedContent(
                targetState = state.date,
                modifier = Modifier.fillMaxWidth(0.68f),
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(Motion.MEDIUM)) { dir * it / 3 } + fadeIn(tween(Motion.MEDIUM)))
                        .togetherWith(slideOutHorizontally(tween(Motion.SHORT)) { -dir * it / 3 } + fadeOut(tween(Motion.SHORT)))
                },
                label = "date",
            ) { day ->
                val text = Formats.digits(context, day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Formats.locale(context))))
                Text(
                    if (day == LocalDate.now()) stringResource(R.string.today_label, text) else text,
                    style = MaterialTheme.typography.titleMedium,
                    color = skyContentSoftColor(scene),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SkyButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.cd_previous_day), ink, onPrev)
                Spacer(Modifier.width(10.dp))
                SkyButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.cd_next_day), ink, onNext)
            }
        }
        AnimatedVisibility(
            !state.isToday,
            enter = expandVertically(tween(Motion.MEDIUM)) + fadeIn(tween(Motion.MEDIUM)),
            exit = shrinkVertically(tween(Motion.MEDIUM)) + fadeOut(tween(Motion.SHORT)),
        ) {
            SecondaryButton(stringResource(R.string.action_back_to_today), onClick = onToday, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

/** A round, see-through button that sits on the painted sky. */
@Composable
private fun SkyButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .size(52.dp)
            .pressScale(interaction, 0.9f)
            .clip(CircleShape)
            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.38f)),
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(30.dp))
    }
}

/** "2 of 3 taken" with a ring that fills, and what comes next. */
@Composable
private fun SummaryCard(state: TodayUi, modifier: Modifier = Modifier) {
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

    // Confetti only when the day gets finished while watching — not every time Today opens
    var wasDone by remember { mutableStateOf(allDone) }
    var celebrate by remember { mutableStateOf(false) }
    LaunchedEffect(allDone, state.isToday) {
        if (allDone && !wasDone && state.isToday) celebrate = true
        wasDone = allDone
    }
    val container by animateColorAsState(if (allDone) status.takenContainer else MaterialTheme.colorScheme.surface, tween(Motion.LONG), label = "summaryBg")
    val onColor by animateColorAsState(if (allDone) status.onTakenContainer else MaterialTheme.colorScheme.onSurface, tween(Motion.LONG), label = "summaryFg")
    val border by animateColorAsState(if (allDone) status.taken.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant, tween(Motion.LONG), label = "summaryEdge")
    val progress by animateFloatAsState(
        if (state.total == 0) 0f else state.taken / state.total.toFloat(),
        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "progress",
    )

    Box(modifier) {
        MediCard(containerColor = container, borderColor = border) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    RollingContent(state.taken to state.total) { (taken, total) ->
                        Text(
                            stringResource(
                                when {
                                    state.rows.all { CareText.isOral(it.kind) } -> if (state.isToday) R.string.today_summary else R.string.day_summary
                                    else -> if (state.isToday) R.string.today_summary_done else R.string.day_summary_done
                                },
                                Formats.number(context, taken), Formats.number(context, total),
                            ),
                            style = MaterialTheme.typography.headlineSmall,
                            color = onColor,
                        )
                    }
                    if (state.isToday && (allDone || next != null)) {
                        Spacer(Modifier.height(10.dp))
                        val line = if (allDone) stringResource(R.string.today_all_done)
                        else next!!.second.let { row ->
                            stringResource(
                                if (CareText.isOral(row.kind)) R.string.today_next_named else R.string.today_next_any,
                                CareText.name(context, row.kind, row.subtype, row.name),
                                Formats.time(context, minuteOf(next.first.toLocalTime())),
                            )
                        }
                        RollingContent(allDone to line) { (done, text) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (done) Icons.Default.CheckCircle else Icons.Default.Alarm,
                                    contentDescription = null,
                                    tint = if (done) status.taken else MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = onColor)
                            }
                        }
                        // "in 2 h 10 min" — refreshed with Today's clock
                        val minutes = if (allDone || next == null) 0L else Duration.between(LocalDateTime.now(), next.first).toMinutes()
                        AnimatedVisibility(minutes > 0, enter = fadeIn(tween(Motion.MEDIUM)) + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                            Text(
                                untilText(context, minutes),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(start = 32.dp, top = 2.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                // The ring fills as doses are taken; a finished day earns a swinging medal
                AnimatedContent(allDone, transitionSpec = { popIn() togetherWith fadeOut(tween(Motion.SHORT)) }, label = "ringOrMedal") { done ->
                    if (done) {
                        AnimatedArt(Art.MEDAL, Hue.SUN, size = 96.dp)
                    } else {
                        ProgressRing(progress = progress, color = status.taken, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, size = 78.dp) {
                            Icon(Icons.Default.Medication, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                        }
                    }
                }
            }
        }
        if (celebrate) {
            CelebrationBurst(
                colors = listOf(Hue.SUNRISE, Hue.SUN, Hue.SUNSET, Hue.SKY, Hue.GREEN, Hue.VIOLET).map { it.fg(false) },
                modifier = Modifier.matchParentSize(),
                onFinished = { celebrate = false },
            )
        }
    }
}

@Composable
private fun SectionHeading(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, hue: Hue) {
    Row(Modifier.padding(top = 10.dp, start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, hue, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun DayPartHeader(part: DayPart) {
    val (icon, label, hue) = when (part) {
        DayPart.MORNING -> Triple(Icons.Default.WbTwilight, R.string.part_morning, Hue.SUNRISE)
        DayPart.AFTERNOON -> Triple(Icons.Default.WbSunny, R.string.part_afternoon, Hue.SUN)
        DayPart.EVENING -> Triple(Icons.Default.WbTwilight, R.string.part_evening, Hue.SUNSET)
        DayPart.NIGHT -> Triple(Icons.Default.Bedtime, R.string.part_night, Hue.NIGHT)
    }
    SectionHeading(icon, stringResource(label), hue)
}

@Composable
private fun doseDetail(row: DoseRow): String {
    val context = LocalContext.current
    return buildList {
        add(CareText.line(context, row.kind, row.quantity, row.unit, row.site))
        if (row.mealTiming != MealTiming.ANY && row.kind.isMedicine) add(Formats.mealTiming(context, row.mealTiming))
    }.joinToString(" · ")
}

/** "Napa 500 mg", or a health check or habit in the app's language. */
@Composable
private fun rowName(row: DoseRow): String {
    val context = LocalContext.current
    return listOf(CareText.name(context, row.kind, row.subtype, row.name), row.strength).filter { it.isNotBlank() }.joinToString(" ")
}

@Composable
private fun DoseCard(row: DoseRow, actionable: Boolean, onTake: () -> Unit, onOptions: () -> Unit, modifier: Modifier = Modifier) {
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
            stringResource(
                if (CareText.isOral(row.kind)) R.string.today_taken_at else R.string.today_done_at,
                Formats.time(context, minuteOf(row.loggedAt.toLocalDateTime().toLocalTime())),
            )
        row.state == DoseState.TAKEN && !CareText.isOral(row.kind) -> stringResource(R.string.state_done)
        row.state == DoseState.SNOOZED && row.snoozedUntil != null ->
            stringResource(R.string.state_snoozed_until, Formats.time(context, minuteOf(row.snoozedUntil.toLocalDateTime().toLocalTime())))
        else -> stateLabel(row.state)
    }

    // A dose that is due right now gently breathes so it is noticed
    val pulse = rememberPulse(enabled = row.state == DoseState.DUE || row.ringing)

    // Taken: the picture bounces, a ring bursts out and the phone gives a little buzz
    val haptic = LocalHapticFeedback.current
    var seen by remember(row.scheduleId, row.day) { mutableStateOf(row.state) }
    var burst by remember { mutableStateOf(false) }
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(row.state) {
        val before = seen
        seen = row.state
        if (row.state == DoseState.TAKEN && before != DoseState.TAKEN) {
            burst = true
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            bounce.snapTo(0.82f)
            bounce.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
        }
    }
    val dim by animateFloatAsState(if (row.state == DoseState.SKIPPED) 0.7f else 1f, tween(Motion.LONG), label = "skipDim")
    val container by animateColorAsState(
        if (answered) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface, tween(Motion.LONG), label = "doseBg",
    )
    val border by animateColorAsState(accent ?: MaterialTheme.colorScheme.outlineVariant, tween(Motion.MEDIUM), label = "doseBorder")
    MediCard(
        onClick = onOptions,
        modifier = modifier.graphicsLayer { alpha = dim },
        containerColor = container,
        borderColor = if (accent != null) border.copy(alpha = 0.45f + 0.55f * pulse) else border,
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
                color = if (answered) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            StatusChip(row.state, chipLabel)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                MedicineImage(
                    row.photo, row.name, row.color, size = 64,
                    modifier = Modifier.graphicsLayer { scaleX = bounce.value; scaleY = bounce.value },
                    kind = row.kind, subtype = row.subtype,
                )
                if (burst) BurstEffect(status.taken, Modifier.matchParentSize(), onFinished = { burst = false })
                DoneMark(answered, skipped = row.state == DoseState.SKIPPED, Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 6.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(rowName(row), style = MaterialTheme.typography.titleLarge)
                Text(doseDetail(row), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Folds away once answered, while the badge turns to "Taken at …"
        AnimatedVisibility(
            visible = actionable,
            enter = expandVertically(tween(Motion.MEDIUM)) + fadeIn(tween(Motion.MEDIUM)),
            exit = shrinkVertically(tween(Motion.LONG, easing = FastOutSlowInEasing)) + fadeOut(tween(Motion.SHORT)),
        ) {
            Column {
                Spacer(Modifier.height(16.dp))
                TakenButton(onClick = onTake, text = CareText.answer(context, row.kind))
                QuietButton(
                    stringResource(R.string.dose_snooze_or_skip),
                    onClick = onOptions,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

/** "in 45 min", "in 2 h", "in 2 h 10 min" */
private fun untilText(context: android.content.Context, minutes: Long): String {
    val h = (minutes / 60).toInt()
    val m = (minutes % 60).toInt()
    return when {
        h == 0 -> context.getString(R.string.today_in_minutes, Formats.number(context, m))
        m == 0 -> context.getString(R.string.today_in_hours, Formats.number(context, h))
        else -> context.getString(R.string.today_in_hours_minutes, Formats.number(context, h), Formats.number(context, m))
    }
}

/** The Undo message: large, with a big Undo button, easy to catch in time. */
@Composable
private fun UndoBar(data: SnackbarData) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        shadowElevation = 8.dp,
        modifier = Modifier.padding(16.dp).fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.inversePrimary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(data.visuals.message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.weight(1f))
            data.visuals.actionLabel?.let { label ->
                TextButton(onClick = { data.performAction() }, modifier = Modifier.heightIn(min = 52.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, tint = MaterialTheme.colorScheme.inversePrimary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.inversePrimary)
                }
            }
        }
    }
}

/** A tick (taken) or a dash (skipped) that pops onto the corner of the medicine's picture. */
@Composable
private fun DoneMark(visible: Boolean, skipped: Boolean, modifier: Modifier) {
    val status = LocalStatusColors.current
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = popIn(),
        exit = scaleOut(tween(Motion.SHORT), targetScale = 0.4f) + fadeOut(tween(Motion.SHORT)),
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(2.dp)
                .clip(CircleShape).background(if (skipped) status.quiet else status.taken),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (skipped) Icons.Default.Remove else Icons.Default.Check,
                contentDescription = null,
                tint = if (skipped) MaterialTheme.colorScheme.surface else status.onTaken,
                modifier = Modifier.size(18.dp),
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
            MedicinePhoto(row.photo, stringResource(R.string.cd_medicine_photo, row.name), Modifier.fillMaxWidth().height(180.dp))
            Text(rowName(row), style = MaterialTheme.typography.headlineSmall)
            Text(
                Formats.time(context, row.minuteOfDay) + " · " + doseDetail(row),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            if (canAct && !answered) {
                TakenButton(
                    onClick = onTake,
                    text = when {
                        CareText.isOral(row.kind) -> stringResource(R.string.dose_sheet_take)
                        row.kind == CareKind.HABIT -> stringResource(R.string.dose_sheet_done)
                        else -> CareText.answer(context, row.kind)
                    },
                )
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
