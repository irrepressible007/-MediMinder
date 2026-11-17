package com.example.mediminder.ui.medicines

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mediminder.R
import com.example.mediminder.data.catalogue.CatalogueMedicine
import com.example.mediminder.data.local.entity.AlertType
import com.example.mediminder.data.local.entity.DaysOfWeek
import com.example.mediminder.data.local.entity.MealTiming
import com.example.mediminder.domain.DosePattern
import com.example.mediminder.domain.SafetyWarning
import com.example.mediminder.ui.components.ChoiceChips
import com.example.mediminder.ui.components.SectionTitle
import com.example.mediminder.ui.components.Stepper
import com.example.mediminder.ui.components.TimePill
import com.example.mediminder.ui.components.parseColor
import com.example.mediminder.ui.components.showDatePicker
import com.example.mediminder.util.Formats
import java.time.DayOfWeek
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicineEditorScreen(
    medicationId: Long,
    scannedCatalogueId: Long?,
    onScanConsumed: () -> Unit,
    onScan: () -> Unit,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: MedicineEditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(medicationId) { viewModel.load(medicationId) }
    LaunchedEffect(scannedCatalogueId) {
        if (scannedCatalogueId != null) {
            viewModel.pickById(scannedCatalogueId)
            onScanConsumed()
        }
    }
    val s by viewModel.state.collectAsState()
    LaunchedEffect(s.savedId) { s.savedId?.let(onSaved) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (medicationId == 0L) R.string.editor_title_new else R.string.editor_title_edit), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back)) } },
            )
        },
        bottomBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp).imePadding()) {
                s.error?.let {
                    Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                }
                Button(onClick = viewModel::save, enabled = !s.saving && !s.loading, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(stringResource(R.string.action_save_medicine), style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) { padding ->
        if (s.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MedicinePicker(s, viewModel, onScan)
            if (s.nameConfirmed) {
                WarningsCard(s.warnings)
                ScheduleSection(s, viewModel)
                CourseSection(s, viewModel)
                StockSection(s, viewModel)
                LooksSection(s, viewModel)
                OutlinedTextField(
                    value = s.notes,
                    onValueChange = viewModel::onNotes,
                    label = { Text(stringResource(R.string.editor_notes)) },
                    placeholder = { Text(stringResource(R.string.editor_notes_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── 1. Which medicine ───────────────────────────────────────────────────────

@Composable
private fun MedicinePicker(s: EditorState, vm: MedicineEditorViewModel, onScan: () -> Unit) {
    val context = LocalContext.current
    if (!s.nameConfirmed) {
        OutlinedTextField(
            value = s.query,
            onValueChange = vm::onQuery,
            label = { Text(stringResource(R.string.editor_search_label)) },
            placeholder = { Text(stringResource(R.string.editor_search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = { IconButton(onClick = onScan) { Icon(Icons.Default.CameraAlt, contentDescription = stringResource(R.string.cd_scan)) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.editor_catalogue_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (s.searching && s.results.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
        }
        if (s.query.trim().length >= 2) {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                s.results.forEachIndexed { i, medicine ->
                    if (i > 0) HorizontalDivider()
                    CatalogueRow(medicine, onClick = { vm.pick(medicine) })
                }
                if (s.results.isNotEmpty()) HorizontalDivider()
                // Not in the list? Use the typed name.
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = vm::useCustomName).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(if (s.results.isEmpty() && !s.searching) R.string.editor_not_found_use else R.string.editor_use_typed, s.query.trim()),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        return
    }

    // Chosen: show it, let the user correct the details
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(if (s.catalogueItem != null) R.string.editor_from_catalogue else R.string.editor_custom_medicine), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    s.catalogueItem?.let { c ->
                        Text(listOfNotNull(c.dosageForm, c.manufacturer).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        priceText(context, c)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                TextButton(onClick = vm::changeMedicine) { Text(stringResource(R.string.action_change)) }
            }
            OutlinedTextField(s.name, vm::onName, label = { Text(stringResource(R.string.editor_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(s.strength, vm::onStrength, label = { Text(stringResource(R.string.editor_strength)) }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(s.genericName, vm::onGeneric, label = { Text(stringResource(R.string.editor_generic)) }, singleLine = true, modifier = Modifier.weight(1.4f))
            }
            Text(stringResource(R.string.editor_unit), style = MaterialTheme.typography.labelLarge)
            ChoiceChips(Formats.UNITS, s.unit, { Formats.unitName(context, it, 1.0) }, vm::onUnit)
        }
    }
}

@Composable
private fun CatalogueRow(medicine: CatalogueMedicine, onClick: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(medicine.brandName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            medicine.strength?.let {
                Spacer(Modifier.width(6.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.weight(1f))
            priceText(context, medicine)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(
            listOfNotNull(medicine.dosageForm, medicine.genericName, medicine.manufacturer).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

fun priceText(context: android.content.Context, medicine: CatalogueMedicine): String? = when {
    medicine.unitPrice != null -> context.getString(R.string.price_per_unit, Formats.taka(context, medicine.unitPrice))
    medicine.packInfo != null -> Formats.digits(context, medicine.packInfo)
    else -> null
}

// ── 2. Safety ───────────────────────────────────────────────────────────────

@Composable
private fun WarningsCard(warnings: List<SafetyWarning>) {
    if (warnings.isEmpty()) return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.safety_title), fontWeight = FontWeight.Bold)
            }
            warnings.forEach { w ->
                Text(
                    "• " + when (w) {
                        is SafetyWarning.Allergy -> stringResource(R.string.safety_allergy, w.allergen)
                        is SafetyWarning.AllergyClass -> stringResource(R.string.safety_allergy_class, w.drugClass, w.allergen)
                        is SafetyWarning.DuplicateIngredient -> stringResource(R.string.safety_duplicate, w.otherMedicine, w.ingredient)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(stringResource(R.string.safety_footer), style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ── 3. When to take it ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduleSection(s: EditorState, vm: MedicineEditorViewModel) {
    val context = LocalContext.current
    SectionTitle(stringResource(R.string.editor_section_schedule))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        val modes = ScheduleMode.entries
        modes.forEachIndexed { i, mode ->
            SegmentedButton(
                selected = s.mode == mode,
                onClick = { vm.onMode(mode) },
                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
            ) {
                Text(stringResource(when (mode) {
                    ScheduleMode.PATTERN -> R.string.mode_pattern
                    ScheduleMode.CUSTOM -> R.string.mode_custom
                    ScheduleMode.AS_NEEDED -> R.string.mode_as_needed
                }), maxLines = 1)
            }
        }
    }

    if (s.mode == ScheduleMode.AS_NEEDED) {
        Text(stringResource(R.string.editor_as_needed_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = s.maxDailyDoses,
            onValueChange = vm::onMaxDaily,
            label = { Text(stringResource(R.string.editor_max_daily)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    if (s.mode == ScheduleMode.PATTERN) {
        Text(stringResource(R.string.editor_pattern_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DosePattern.PRESETS.forEach { preset ->
                FilterChip(
                    selected = DosePattern.parse(s.pattern)?.text == DosePattern.parse(preset)?.text,
                    onClick = { vm.onPattern(preset) },
                    label = { Text(Formats.digits(context, preset), fontWeight = FontWeight.Bold) },
                )
            }
        }
        OutlinedTextField(
            value = s.pattern,
            onValueChange = vm::onPattern,
            label = { Text(stringResource(R.string.editor_pattern_label)) },
            supportingText = { Text(stringResource(R.string.editor_pattern_support)) },
            isError = DosePattern.parse(s.pattern) == null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Text(stringResource(R.string.editor_meal_timing), style = MaterialTheme.typography.labelLarge)
    ChoiceChips(MealTiming.entries, s.mealTiming, { Formats.mealTiming(context, it) }, vm::onMealTiming)

    // The dose times
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp)) {
            s.times.forEach { t ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TimePill(t.minuteOfDay, onChange = { vm.onTimeChanged(t.key, it) })
                    Spacer(Modifier.weight(1f))
                    Stepper(
                        value = Formats.quantity(context, t.quantity, s.unit),
                        onMinus = { vm.onQuantityChanged(t.key, if (t.quantity <= 1.0) -0.25 else -0.5) },
                        onPlus = { vm.onQuantityChanged(t.key, if (t.quantity < 1.0) 0.25 else 0.5) },
                        minusEnabled = t.quantity > 0.25,
                    )
                    IconButton(onClick = { vm.toggleTimeAlert(t.key) }) {
                        Icon(
                            if (t.alertType == AlertType.ALARM) Icons.Default.Alarm else Icons.Default.Notifications,
                            contentDescription = stringResource(if (t.alertType == AlertType.ALARM) R.string.alert_alarm else R.string.alert_reminder),
                            tint = if (t.alertType == AlertType.ALARM) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (s.mode == ScheduleMode.CUSTOM) {
                        IconButton(onClick = { vm.removeTime(t.key) }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_remove)) }
                    }
                }
            }
            TextButton(onClick = vm::addTime, modifier = Modifier.padding(horizontal = 4.dp)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.action_add_time))
            }
        }
    }

    // Reminder or alarm for every time
    Text(stringResource(R.string.editor_alert_style), style = MaterialTheme.typography.labelLarge)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf(AlertType.REMINDER, AlertType.ALARM).forEachIndexed { i, type ->
            val allThisType = s.times.isNotEmpty() && s.times.all { it.alertType == type }
            SegmentedButton(
                selected = allThisType,
                onClick = { vm.onAlertTypeForAll(type) },
                shape = SegmentedButtonDefaults.itemShape(i, 2),
                icon = { Icon(if (type == AlertType.ALARM) Icons.Default.Alarm else Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp)) },
            ) { Text(stringResource(if (type == AlertType.ALARM) R.string.alert_alarm else R.string.alert_reminder)) }
        }
    }
    Text(
        stringResource(if (s.times.any { it.alertType == AlertType.ALARM }) R.string.editor_alarm_explain else R.string.editor_reminder_explain),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // Days of the week (Bangladesh week starts on Saturday)
    Text(stringResource(R.string.editor_days), style = MaterialTheme.typography.labelLarge)
    val everyDay = s.daysMask == DaysOfWeek.EVERY_DAY
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = everyDay, onClick = { vm.onDays(DaysOfWeek.EVERY_DAY) }, label = { Text(stringResource(R.string.days_every_day)) })
        FilterChip(selected = !everyDay, onClick = { if (everyDay) vm.onDays(DaysOfWeek.EVERY_DAY and DaysOfWeek.bit(DayOfWeek.FRIDAY).inv()) }, label = { Text(stringResource(R.string.days_selected)) })
    }
    if (!everyDay) {
        val week = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            week.forEach { day ->
                val on = DaysOfWeek.includes(s.daysMask, day)
                FilterChip(
                    selected = on,
                    onClick = { vm.onDays(s.daysMask xor DaysOfWeek.bit(day)) },
                    label = { Text(day.getDisplayName(TextStyle.SHORT, Formats.locale(context))) },
                )
            }
        }
    }
}

// ── 4. Course length ────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseSection(s: EditorState, vm: MedicineEditorViewModel) {
    val context = LocalContext.current
    SectionTitle(stringResource(R.string.editor_section_course))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = s.durationDays == null, onClick = { vm.onDuration(null) }, label = { Text(stringResource(R.string.course_ongoing)) })
        EditorState.DURATIONS.forEach { days ->
            FilterChip(
                selected = s.durationDays == days,
                onClick = { vm.onDuration(days) },
                label = { Text(context.resources.getQuantityString(R.plurals.days_count, days, days)) },
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.course_starts), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = { showDatePicker(context, s.startDate) { vm.onStartDate(it) } }) { Text(Formats.date(context, s.startDate)) }
    }
    s.durationDays?.let { days ->
        Text(
            stringResource(R.string.course_ends, Formats.date(context, s.startDate.plusDays(days - 1L))),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── 5. Stock ────────────────────────────────────────────────────────────────

@Composable
private fun StockSection(s: EditorState, vm: MedicineEditorViewModel) {
    val context = LocalContext.current
    SectionTitle(stringResource(R.string.editor_section_stock)) {
        Switch(checked = s.trackStock, onCheckedChange = vm::onTrackStock)
    }
    if (!s.trackStock) {
        Text(stringResource(R.string.editor_stock_off_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    OutlinedTextField(
        value = s.stock,
        onValueChange = vm::onStock,
        label = { Text(stringResource(R.string.editor_stock_count, Formats.unitName(context, s.unit, 2.0))) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(if (s.mode == ScheduleMode.AS_NEEDED) R.string.editor_refill_when_units else R.string.editor_refill_when_days),
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
        )
        Stepper(
            value = Formats.number(context, s.refillThresholdDays),
            onMinus = { vm.onThreshold(s.refillThresholdDays - 1) },
            onPlus = { vm.onThreshold(s.refillThresholdDays + 1) },
            minusEnabled = s.refillThresholdDays > 1,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.editor_refill_reminders), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = s.refillReminders, onCheckedChange = vm::onRefillReminders)
    }
}

// ── 6. Looks ────────────────────────────────────────────────────────────────

@Composable
private fun LooksSection(s: EditorState, vm: MedicineEditorViewModel) {
    SectionTitle(stringResource(R.string.editor_section_looks))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        EditorState.COLORS.forEach { hex ->
            val selected = s.color.equals(hex, ignoreCase = true)
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(parseColor(hex))
                    .border(if (selected) 3.dp else 0.dp, if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                    .clickable { vm.onColor(hex) }
            )
        }
    }
    ChoiceChips(EditorState.SHAPES, s.shape, { shapeLabel(it) }, vm::onShape)
}

@Composable
private fun shapeLabel(shape: String): String = stringResource(
    when (shape) {
        "Oval" -> R.string.shape_oval
        "Capsule" -> R.string.shape_capsule
        "Liquid" -> R.string.shape_liquid
        "Other" -> R.string.shape_other
        else -> R.string.shape_round
    }
)
