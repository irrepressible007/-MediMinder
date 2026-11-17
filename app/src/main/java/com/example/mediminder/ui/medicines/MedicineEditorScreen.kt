package com.example.mediminder.ui.medicines

import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.mediminder.ui.components.FeedbackBus
import com.example.mediminder.ui.components.FeedbackKind
import com.example.mediminder.ui.components.IconBadge
import com.example.mediminder.ui.components.popOnEnter
import com.example.mediminder.ui.theme.Hue
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mediminder.R
import com.example.mediminder.ui.components.stepTransition
import com.example.mediminder.ui.components.Motion
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.AnimatedContent
import com.example.mediminder.ui.photos.MedicinePhoto
import com.example.mediminder.ui.components.MedicineImage
import com.example.mediminder.data.photos.MedicinePhotos
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.layout.aspectRatio
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import com.example.mediminder.data.catalogue.CatalogueMedicine
import com.example.mediminder.data.local.entity.AlertType
import com.example.mediminder.data.local.entity.DaysOfWeek
import com.example.mediminder.data.local.entity.MealTiming
import com.example.mediminder.domain.DosePattern
import com.example.mediminder.domain.SafetyWarning
import com.example.mediminder.ui.components.AppTopBar
import com.example.mediminder.ui.components.ChoiceChips
import com.example.mediminder.ui.components.ChoiceTile
import com.example.mediminder.ui.components.ExpandableSection
import com.example.mediminder.ui.components.MediCard
import com.example.mediminder.ui.components.NoticeCard
import com.example.mediminder.ui.components.PrimaryButton
import com.example.mediminder.ui.components.QuietButton
import com.example.mediminder.ui.components.SecondaryButton
import com.example.mediminder.ui.components.SectionTitle
import com.example.mediminder.ui.components.Stepper
import com.example.mediminder.ui.components.SwitchRow
import com.example.mediminder.ui.components.TimePill
import com.example.mediminder.ui.components.Tone
import com.example.mediminder.ui.components.ValueRow
import com.example.mediminder.ui.components.parseColor
import com.example.mediminder.ui.components.showDatePicker
import com.example.mediminder.util.Formats
import java.time.DayOfWeek
import java.time.format.TextStyle

private const val STEPS = 4

/** The four everyday prescription patterns, shown as big choices. */
private val MAIN_PATTERNS = listOf("1+0+0", "0+0+1", "1+0+1", "1+1+1")
private val OTHER_PATTERNS = DosePattern.PRESETS - MAIN_PATTERNS.toSet()

/**
 * Adding a medicine, one question per page:
 *  1. Which medicine?  2. When?  3. Check the times & how to remind  4. Optional extras.
 * Editing uses the same pages, with Save available on every one.
 */
@Composable
fun MedicineEditorScreen(
    medicationId: Long,
    scannedCatalogueId: Long?,
    capturedPhoto: String?,
    onScanConsumed: () -> Unit,
    onScan: () -> Unit,
    onTakePhoto: () -> Unit,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: MedicineEditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(medicationId) { viewModel.load(medicationId) }
    // Results handed back by the scanner or the photo camera
    LaunchedEffect(scannedCatalogueId, capturedPhoto) {
        when {
            scannedCatalogueId != null -> viewModel.pickScanned(scannedCatalogueId, capturedPhoto)
            capturedPhoto != null -> viewModel.onPhoto(capturedPhoto)
            else -> return@LaunchedEffect
        }
        onScanConsumed()
    }
    val context = LocalContext.current
    val s by viewModel.state.collectAsState()
    LaunchedEffect(s.savedId) {
        val id = s.savedId ?: return@LaunchedEffect
        val name = listOf(s.name, s.strength).filter { it.isNotBlank() }.joinToString(" ")
        if (s.isNew) FeedbackBus.show(FeedbackKind.ADDED, R.string.feedback_medicine_added, name)
        else FeedbackBus.show(FeedbackKind.SAVED, R.string.feedback_medicine_saved, name)
        FeedbackBus.highlight(id)
        onSaved(id)
    }
    var step by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = step > 0) { step-- }

    val canGoOn = when (step) {
        0 -> s.nameConfirmed || s.query.isNotBlank()
        2 -> s.mode == ScheduleMode.AS_NEEDED || s.times.isNotEmpty()
        else -> true
    }
    fun next() {
        if (step == 0 && !s.nameConfirmed) viewModel.useCustomName()
        if (step < STEPS - 1) step++ else viewModel.save()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                AppTopBar(
                    title = stringResource(if (s.isNew) R.string.editor_title_new else R.string.editor_title_edit),
                    subtitle = stringResource(R.string.wiz_step, Formats.number(context, step + 1), Formats.number(context, STEPS)),
                    onBack = onBack,
                    actions = {
                        if (!s.isNew) {
                            TextButton(onClick = viewModel::save, enabled = !s.saving, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.action_save), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    },
                )
                val progress by animateFloatAsState((step + 1) / STEPS.toFloat(), tween(Motion.LONG, easing = FastOutSlowInEasing), label = "steps")
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(8.dp).clip(CircleShape),
                    strokeCap = StrokeCap.Round,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        },
        bottomBar = {
            Column(
                Modifier.background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp, vertical = 12.dp).navigationBarsPadding().imePadding(),
            ) {
                s.error?.let {
                    Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (step > 0) SecondaryButton(stringResource(R.string.action_back), onClick = { step-- }, modifier = Modifier.weight(1f))
                    PrimaryButton(
                        text = stringResource(if (step < STEPS - 1) R.string.action_next else R.string.action_save_medicine),
                        onClick = ::next,
                        enabled = canGoOn && !s.saving && !s.loading,
                        modifier = Modifier.weight(1.6f),
                    )
                }
            }
        },
    ) { padding ->
        if (s.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        // Next slides the new page in from the right; Back from the left
        AnimatedContent(
            targetState = step,
            modifier = Modifier.fillMaxSize().padding(padding),
            transitionSpec = { stepTransition(forward = targetState > initialState) },
            label = "wizard",
        ) { page ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (page) {
                    0 -> NameStep(s, viewModel, onScan, onTakePhoto)
                    1 -> WhenStep(s, viewModel)
                    2 -> TimesStep(s, viewModel)
                    else -> FinishStep(s, viewModel)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun StepTitle(title: String, body: String? = null, icon: ImageVector, hue: Hue) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, hue, size = 52.dp, modifier = Modifier.popOnEnter(120))
            Spacer(Modifier.width(14.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        }
        body?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

// ── 1. Which medicine ───────────────────────────────────────────────────────

@Composable
private fun ColumnScope.NameStep(s: EditorState, vm: MedicineEditorViewModel, onScan: () -> Unit, onTakePhoto: () -> Unit) {
    val context = LocalContext.current
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::importPhoto) }
    if (!s.nameConfirmed) {
        StepTitle(stringResource(R.string.wiz_name_title), stringResource(R.string.wiz_name_body), Icons.Default.Medication, Hue.CORAL)
        OutlinedTextField(
            value = s.query,
            onValueChange = vm::onQuery,
            label = { Text(stringResource(R.string.editor_search_label)) },
            placeholder = { Text(stringResource(R.string.editor_search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(28.dp)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        if (s.query.trim().length < 2) {
            SecondaryButton(stringResource(R.string.wiz_scan), onClick = onScan, icon = Icons.Default.CameraAlt)
            Text(stringResource(R.string.editor_catalogue_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (s.searching && s.results.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(32.dp)) }
        }
        if (s.query.trim().length >= 2) {
            MediCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                s.results.forEachIndexed { i, medicine ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    CatalogueRow(medicine, onClick = { vm.pick(medicine) })
                }
                if (s.results.isNotEmpty()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = vm::useCustomName).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(if (s.results.isEmpty() && !s.searching) R.string.editor_not_found_use else R.string.editor_use_typed, s.query.trim()),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
        return
    }

    // Chosen — confirm it's the right one
    StepTitle(stringResource(R.string.wiz_confirm_title), icon = Icons.Default.CheckCircle, hue = Hue.GREEN)
    MediCard(containerColor = MaterialTheme.colorScheme.primaryContainer, borderColor = MaterialTheme.colorScheme.primary, borderWidth = 2.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MedicineImage(s.photo, s.name, s.color, size = 64)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(listOf(s.name, s.strength).filter { it.isNotBlank() }.joinToString(" "), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                s.genericName.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                s.catalogueItem?.let { c ->
                    Text(listOfNotNull(c.dosageForm, c.manufacturer).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    priceText(context, c)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                }
            }
        }
    }
    QuietButton(stringResource(R.string.wiz_choose_other), onClick = vm::changeMedicine, icon = Icons.Default.Search)
    WarningsCard(s.warnings)

    // A photo of the box or strip, shown in reminders and alarms
    SectionTitle(stringResource(R.string.photo_title))
    Text(stringResource(R.string.photo_why), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (MedicinePhotos.model(context, s.photo) != null) {
        MedicinePhoto(s.photo, stringResource(R.string.cd_medicine_photo, s.name), Modifier.fillMaxWidth().aspectRatio(4f / 3f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(stringResource(R.string.photo_retake), onClick = onTakePhoto, icon = Icons.Default.CameraAlt, modifier = Modifier.weight(1f))
            SecondaryButton(stringResource(R.string.photo_remove), onClick = { vm.onPhoto(null) }, icon = Icons.Default.Delete, contentColor = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
        }
    } else {
        SecondaryButton(stringResource(R.string.photo_take), onClick = onTakePhoto, icon = Icons.Default.CameraAlt)
        QuietButton(
            stringResource(R.string.photo_gallery),
            onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            icon = Icons.Default.PhotoLibrary,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }

    if (s.catalogueItem == null) {
        SectionTitle(stringResource(R.string.wiz_kind_title))
        ChoiceChips(Formats.UNITS, s.unit, { Formats.unitName(context, it, 1.0) }, vm::onUnit)
    }
    ExpandableSection(stringResource(R.string.wiz_details), initiallyExpanded = false) {
        OutlinedTextField(s.name, vm::onName, label = { Text(stringResource(R.string.editor_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(s.strength, vm::onStrength, label = { Text(stringResource(R.string.editor_strength)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(s.genericName, vm::onGeneric, label = { Text(stringResource(R.string.editor_generic)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (s.catalogueItem != null) {
            Text(stringResource(R.string.editor_unit), style = MaterialTheme.typography.titleSmall)
            ChoiceChips(Formats.UNITS, s.unit, { Formats.unitName(context, it, 1.0) }, vm::onUnit)
        }
    }
}

@Composable
private fun CatalogueRow(medicine: CatalogueMedicine, onClick: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(medicine.brandName, style = MaterialTheme.typography.titleLarge)
            medicine.strength?.let {
                Spacer(Modifier.width(8.dp))
                Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        Text(
            listOfNotNull(medicine.dosageForm, medicine.genericName).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            listOfNotNull(medicine.manufacturer, priceText(context, medicine)?.substringBefore(',')).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
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

@Composable
private fun WarningsCard(warnings: List<SafetyWarning>) {
    if (warnings.isEmpty()) return
    MediCard(containerColor = MaterialTheme.colorScheme.errorContainer, borderColor = MaterialTheme.colorScheme.error, borderWidth = 2.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.safety_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        }
        Spacer(Modifier.height(8.dp))
        warnings.forEach { w ->
            Text(
                "• " + when (w) {
                    is SafetyWarning.Allergy -> stringResource(R.string.safety_allergy, w.allergen)
                    is SafetyWarning.AllergyClass -> stringResource(R.string.safety_allergy_class, w.drugClass, w.allergen)
                    is SafetyWarning.DuplicateIngredient -> stringResource(R.string.safety_duplicate, w.otherMedicine, w.ingredient)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        Text(stringResource(R.string.safety_footer), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

// ── 2. When ─────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.WhenStep(s: EditorState, vm: MedicineEditorViewModel) {
    val context = LocalContext.current
    StepTitle(stringResource(R.string.wiz_when_title), stringResource(R.string.wiz_when_body), Icons.Default.CalendarMonth, Hue.SKY)
    val current = DosePattern.parse(s.pattern)?.text
    val isMain = s.mode == ScheduleMode.PATTERN && current in MAIN_PATTERNS
    var otherOpen by rememberSaveable { mutableStateOf(!isMain && s.mode != ScheduleMode.AS_NEEDED) }

    MAIN_PATTERNS.forEach { pattern ->
        ChoiceTile(
            title = patternName(context, pattern) ?: pattern,
            badge = Formats.digits(context, pattern),
            selected = s.mode == ScheduleMode.PATTERN && current == pattern && !otherOpen,
            onClick = { otherOpen = false; vm.onMode(ScheduleMode.PATTERN); vm.onPattern(pattern) },
        )
    }
    ChoiceTile(
        title = stringResource(R.string.wiz_needed),
        subtitle = stringResource(R.string.wiz_needed_sub),
        selected = s.mode == ScheduleMode.AS_NEEDED,
        onClick = { otherOpen = false; vm.onMode(ScheduleMode.AS_NEEDED) },
    )
    ChoiceTile(
        title = stringResource(R.string.wiz_other),
        subtitle = stringResource(R.string.wiz_other_sub),
        selected = otherOpen && s.mode != ScheduleMode.AS_NEEDED,
        onClick = {
            otherOpen = true
            if (s.mode == ScheduleMode.AS_NEEDED || isMain) vm.onMode(ScheduleMode.CUSTOM)
        },
    )
    if (otherOpen && s.mode != ScheduleMode.AS_NEEDED) {
        MediCard {
            Text(stringResource(R.string.wiz_other_pattern), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            ChoiceChips(
                OTHER_PATTERNS,
                if (s.mode == ScheduleMode.PATTERN) OTHER_PATTERNS.firstOrNull { DosePattern.parse(it)?.text == current } else null,
                { Formats.digits(context, it) },
                { vm.onMode(ScheduleMode.PATTERN); vm.onPattern(it) },
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = if (s.mode == ScheduleMode.PATTERN) s.pattern else "",
                onValueChange = { vm.onMode(ScheduleMode.PATTERN); vm.onPattern(it) },
                label = { Text(stringResource(R.string.editor_pattern_label)) },
                supportingText = { Text(stringResource(R.string.editor_pattern_support)) },
                isError = s.mode == ScheduleMode.PATTERN && DosePattern.parse(s.pattern) == null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            ChoiceTile(
                title = stringResource(R.string.wiz_own_times),
                selected = s.mode == ScheduleMode.CUSTOM,
                onClick = { vm.onMode(ScheduleMode.CUSTOM) },
                icon = Icons.Default.Alarm,
            )
        }
    }

    SectionTitle(stringResource(R.string.wiz_food_title))
    val mainFood = listOf(MealTiming.AFTER_MEAL, MealTiming.BEFORE_MEAL, MealTiming.ANY)
    mainFood.forEach { timing ->
        ChoiceTile(
            title = if (timing == MealTiming.ANY) stringResource(R.string.wiz_food_any) else Formats.mealTiming(context, timing),
            selected = s.mealTiming == timing,
            onClick = { vm.onMealTiming(timing) },
        )
    }
    val others = MealTiming.entries - mainFood.toSet()
    ExpandableSection(stringResource(R.string.wiz_food_more), initiallyExpanded = s.mealTiming in others) {
        others.forEach { timing ->
            ChoiceTile(title = Formats.mealTiming(context, timing), selected = s.mealTiming == timing, onClick = { vm.onMealTiming(timing) })
        }
    }
}

// ── 3. Times and how to remind ──────────────────────────────────────────────

@Composable
private fun ColumnScope.TimesStep(s: EditorState, vm: MedicineEditorViewModel) {
    val context = LocalContext.current
    if (s.mode == ScheduleMode.AS_NEEDED) {
        StepTitle(stringResource(R.string.wiz_max_title), stringResource(R.string.wiz_max_body), Icons.Default.Shield, Hue.AMBER)
        val max = s.maxDailyDoses.toIntOrNull() ?: 0
        MediCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.wiz_max_label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Stepper(
                    value = if (max == 0) stringResource(R.string.wiz_no_limit) else Formats.number(context, max),
                    onMinus = { vm.onMaxDaily((max - 1).coerceAtLeast(0).toString()) },
                    onPlus = { vm.onMaxDaily((max + 1).coerceAtMost(24).toString()) },
                    minusEnabled = max > 0,
                )
            }
        }
        return
    }

    StepTitle(stringResource(R.string.wiz_times_title), stringResource(R.string.wiz_times_body), Icons.Default.Schedule, Hue.SUNRISE)
    s.times.forEach { t ->
        MediCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimePill(t.minuteOfDay, onChange = { vm.onTimeChanged(t.key, it) }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { vm.removeTime(t.key) }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_remove), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.wiz_amount), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Stepper(
                    value = Formats.quantity(context, t.quantity, s.unit),
                    onMinus = { vm.onQuantityChanged(t.key, if (t.quantity <= 1.0) -0.25 else -0.5) },
                    onPlus = { vm.onQuantityChanged(t.key, if (t.quantity < 1.0) 0.25 else 0.5) },
                    minusEnabled = t.quantity > 0.25,
                )
            }
        }
    }
    SecondaryButton(stringResource(R.string.action_add_time), onClick = vm::addTime, icon = Icons.Default.Add)

    SectionTitle(stringResource(R.string.wiz_alert_title))
    listOf(AlertType.REMINDER, AlertType.ALARM).forEach { type ->
        ChoiceTile(
            title = stringResource(if (type == AlertType.ALARM) R.string.wiz_alert_alarm else R.string.wiz_alert_reminder),
            subtitle = stringResource(if (type == AlertType.ALARM) R.string.wiz_alert_alarm_sub else R.string.wiz_alert_reminder_sub),
            icon = if (type == AlertType.ALARM) Icons.Default.Alarm else Icons.Default.Notifications,
            selected = s.times.isNotEmpty() && s.times.all { it.alertType == type },
            onClick = { vm.onAlertTypeForAll(type) },
        )
    }
}

// ── 4. Optional extras ──────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.FinishStep(s: EditorState, vm: MedicineEditorViewModel) {
    val context = LocalContext.current
    StepTitle(stringResource(R.string.wiz_finish_title), stringResource(R.string.wiz_finish_body), Icons.Default.NotificationsActive, Hue.VIOLET)

    // How long
    SectionTitle(stringResource(R.string.wiz_how_long))
    ChoiceTile(
        title = stringResource(R.string.wiz_no_end),
        subtitle = stringResource(R.string.wiz_no_end_sub),
        selected = s.durationDays == null,
        onClick = { vm.onDuration(null) },
        icon = Icons.Default.EventRepeat,
    )
    ChoiceTile(
        title = stringResource(R.string.wiz_some_days),
        subtitle = stringResource(R.string.wiz_some_days_sub),
        selected = s.durationDays != null,
        onClick = { if (s.durationDays == null) vm.onDuration(7) },
    )
    s.durationDays?.let { days ->
        MediCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.wiz_days_label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Stepper(
                    value = context.resources.getQuantityString(R.plurals.days_count, days, days).let { Formats.digits(context, it) },
                    onMinus = { vm.onDuration((days - 1).coerceAtLeast(1)) },
                    onPlus = { vm.onDuration((days + 1).coerceAtMost(365)) },
                    minusEnabled = days > 1,
                )
            }
            ValueRow(stringResource(R.string.course_starts), Formats.date(context, s.startDate), onClick = { showDatePicker(context, s.startDate) { vm.onStartDate(it) } })
            Text(stringResource(R.string.course_ends, Formats.date(context, s.startDate.plusDays(days - 1L))), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    // Stock
    SectionTitle(stringResource(R.string.wiz_count_heading))
    MediCard {
        SwitchRow(
            title = stringResource(R.string.wiz_count_title),
            subtitle = stringResource(R.string.wiz_count_sub),
            checked = s.trackStock,
            onChange = vm::onTrackStock,
        )
        if (s.trackStock) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = s.stock,
                onValueChange = vm::onStock,
                label = { Text(stringResource(R.string.editor_stock_count, Formats.unitName(context, s.unit, 2.0))) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (s.mode == ScheduleMode.AS_NEEDED) R.string.editor_refill_when_units else R.string.editor_refill_when_days),
                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                )
                Stepper(
                    value = Formats.number(context, s.refillThresholdDays),
                    onMinus = { vm.onThreshold(s.refillThresholdDays - 1) },
                    onPlus = { vm.onThreshold(s.refillThresholdDays + 1) },
                    minusEnabled = s.refillThresholdDays > 1,
                )
            }
            SwitchRow(title = stringResource(R.string.editor_refill_reminders), checked = s.refillReminders, onChange = vm::onRefillReminders)
        }
    }

    // Everything else, folded away
    ExpandableSection(stringResource(R.string.wiz_more), subtitle = stringResource(R.string.wiz_more_sub)) {
        if (s.mode != ScheduleMode.AS_NEEDED) {
            Text(stringResource(R.string.editor_days), style = MaterialTheme.typography.titleSmall)
            val everyDay = s.daysMask == DaysOfWeek.EVERY_DAY
            ChoiceTile(title = stringResource(R.string.days_every_day), selected = everyDay, onClick = { vm.onDays(DaysOfWeek.EVERY_DAY) })
            ChoiceTile(
                title = stringResource(R.string.days_selected),
                selected = !everyDay,
                onClick = { if (everyDay) vm.onDays(DaysOfWeek.EVERY_DAY and DaysOfWeek.bit(DayOfWeek.FRIDAY).inv()) },
            )
            if (!everyDay) {
                // Bangladesh's week starts on Saturday
                val week = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    week.forEach { day ->
                        val on = DaysOfWeek.includes(s.daysMask, day)
                        DayToggle(day.getDisplayName(TextStyle.SHORT, Formats.locale(context)), on) { vm.onDays(s.daysMask xor DaysOfWeek.bit(day)) }
                    }
                }
            }
        }
        if (s.durationDays == null) {
            ValueRow(stringResource(R.string.course_starts), Formats.date(context, s.startDate), onClick = { showDatePicker(context, s.startDate) { vm.onStartDate(it) } })
        }
        Text(stringResource(R.string.wiz_colour), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EditorState.COLORS.forEach { hex ->
                val selected = s.color.equals(hex, ignoreCase = true)
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(parseColor(hex))
                        .border(if (selected) 4.dp else 0.dp, if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                        .clickable { vm.onColor(hex) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(Icons.Default.Done, contentDescription = null, tint = Color.White)
                }
            }
        }
        OutlinedTextField(
            value = s.notes,
            onValueChange = vm::onNotes,
            label = { Text(stringResource(R.string.editor_notes)) },
            placeholder = { Text(stringResource(R.string.editor_notes_hint)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
    }
    if (s.warnings.isNotEmpty()) {
        NoticeCard(stringResource(R.string.safety_title), Icons.Default.Warning, Tone.DANGER)
    }
}

@Composable
private fun DayToggle(label: String, on: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(if (on) scheme.primary else scheme.surface)
            .border(1.5.dp, if (on) scheme.primary else scheme.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) scheme.onPrimary else scheme.onSurface)
    }
}
