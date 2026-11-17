package com.example.mediminder.ui.medicines

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediminder.R
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.mediminder.data.photos.MedicinePhotos
import android.net.Uri
import android.content.Context
import com.example.mediminder.data.MedicineRepository
import com.example.mediminder.data.catalogue.CatalogueMedicine
import com.example.mediminder.data.catalogue.CatalogueRepository
import com.example.mediminder.data.local.entity.AlertType
import com.example.mediminder.data.local.entity.DaysOfWeek
import com.example.mediminder.data.local.entity.DoseSlot
import com.example.mediminder.data.local.entity.MealTiming
import com.example.mediminder.data.local.entity.Medication
import com.example.mediminder.data.local.entity.Schedule
import com.example.mediminder.data.settings.SettingsRepository
import com.example.mediminder.domain.DosePattern
import com.example.mediminder.domain.MealSchedule
import com.example.mediminder.domain.SafetyCheck
import com.example.mediminder.domain.SafetyWarning
import com.example.mediminder.util.Formats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class ScheduleMode { PATTERN, CUSTOM, AS_NEEDED }

/** A dose time being edited. [key] is only for list identity in the UI. */
data class DoseTimeDraft(
    val key: Long,
    val scheduleId: Long = 0,
    val minuteOfDay: Int,
    val quantity: Double = 1.0,
    val alertType: AlertType,
    val slot: DoseSlot? = null,
)

data class EditorState(
    val loading: Boolean = true,
    val id: Long = 0,
    val original: Medication? = null,

    // Which medicine
    val query: String = "",
    val results: List<CatalogueMedicine> = emptyList(),
    val searching: Boolean = false,
    val nameConfirmed: Boolean = false,
    val catalogueItem: CatalogueMedicine? = null,
    val name: String = "",
    val genericName: String = "",
    val strength: String = "",
    val form: String = "Tablet",
    val manufacturer: String = "",
    val unit: String = "tablet",

    // How to take it
    val mode: ScheduleMode = ScheduleMode.PATTERN,
    val pattern: String = "1+0+1",
    val mealTiming: MealTiming = MealTiming.AFTER_MEAL,
    val times: List<DoseTimeDraft> = emptyList(),
    val daysMask: Int = DaysOfWeek.EVERY_DAY,
    val alertType: AlertType = AlertType.REMINDER,
    val maxDailyDoses: String = "",

    // Course
    val startDate: LocalDate = LocalDate.now(),
    /** null = ongoing */
    val durationDays: Int? = null,

    // Stock
    val trackStock: Boolean = false,
    val stock: String = "",
    val refillThresholdDays: Int = 5,
    val refillReminders: Boolean = true,

    // Looks & notes
    /** Photo of the box or strip (see MedicinePhotos), or null. */
    val photo: String? = null,
    val color: String = COLORS.first(),
    val shape: String = "Round",
    val notes: String = "",

    val warnings: List<SafetyWarning> = emptyList(),
    @StringRes val error: Int? = null,
    val saving: Boolean = false,
    val savedId: Long? = null,
) {
    val isNew: Boolean get() = id == 0L

    companion object {
        /** Pill colours, to tell medicines apart. Blue first; green is kept for "taken". */
        val COLORS = listOf("#3B82F6", "#8B5CF6", "#F59E0B", "#EC4899", "#14B8A6", "#EF4444", "#64748B", "#10B981")
        val SHAPES = listOf("Round", "Oval", "Capsule", "Liquid", "Other")
        val DURATIONS = listOf(3, 5, 7, 10, 14, 30, 60, 90)
    }
}

@OptIn(FlowPreview::class)
@HiltViewModel
class MedicineEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MedicineRepository,
    private val catalogue: CatalogueRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState(alertType = settings.current.defaultAlertType))
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private var nextKey = 1L
    private var loadedFor: Long? = null

    init {
        // Live search as the user types (debounced)
        viewModelScope.launch {
            _state.map { it.query to it.nameConfirmed }
                .distinctUntilChanged()
                .debounce(200)
                .collect { (query, confirmed) ->
                    if (confirmed || query.trim().length < 2) {
                        _state.update { it.copy(results = emptyList(), searching = false) }
                    } else {
                        _state.update { it.copy(searching = true) }
                        val results = catalogue.search(query)
                        _state.update { if (it.query == query) it.copy(results = results, searching = false) else it }
                    }
                }
        }
    }

    fun load(medicationId: Long) {
        if (loadedFor == medicationId) return
        loadedFor = medicationId
        viewModelScope.launch {
            if (medicationId == 0L) {
                val count = repository.observeAll().first().size
                _state.update {
                    it.copy(
                        loading = false,
                        times = planPattern(it.pattern, it.mealTiming, it.alertType, emptyList()),
                        color = EditorState.COLORS[count % EditorState.COLORS.size],
                    )
                }
                return@launch
            }
            val item = repository.get(medicationId) ?: run { _state.update { it.copy(loading = false) }; return@launch }
            val m = item.medication
            val catalogueItem = m.catalogueId?.let { catalogue.byId(it) }
            val schedules = item.schedules.sortedBy { it.minuteOfDay }
            val mode = when {
                m.isAsNeeded -> ScheduleMode.AS_NEEDED
                m.dosePattern != null && schedules.isNotEmpty() && schedules.all { it.slot != null } -> ScheduleMode.PATTERN
                else -> ScheduleMode.CUSTOM
            }
            _state.update {
                it.copy(
                    loading = false,
                    id = m.id,
                    original = m,
                    query = m.name,
                    nameConfirmed = true,
                    catalogueItem = catalogueItem,
                    name = m.name,
                    genericName = m.genericName.orEmpty(),
                    strength = m.strength,
                    form = m.form,
                    manufacturer = m.manufacturer.orEmpty(),
                    unit = m.unit,
                    mode = mode,
                    pattern = m.dosePattern ?: it.pattern,
                    mealTiming = m.mealTiming,
                    times = schedules.map { s -> DoseTimeDraft(nextKey++, s.id, s.minuteOfDay, s.quantity, s.alertType, s.slot) },
                    daysMask = schedules.firstOrNull()?.daysMask ?: DaysOfWeek.EVERY_DAY,
                    alertType = schedules.firstOrNull()?.alertType ?: it.alertType,
                    maxDailyDoses = m.maxDailyDoses?.toString().orEmpty(),
                    startDate = m.startDate?.let(LocalDate::ofEpochDay) ?: LocalDate.now(),
                    durationDays = if (m.endDate != null) ((m.endDate - (m.startDate ?: m.endDate)) + 1).toInt() else null,
                    trackStock = m.trackStock,
                    stock = when {
                        !m.trackStock -> ""
                        m.stock % 1.0 == 0.0 -> m.stock.toInt().toString()
                        else -> m.stock.toString()
                    },
                    refillThresholdDays = m.refillThresholdDays,
                    refillReminders = m.refillReminders,
                    color = m.color,
                    photo = m.photoUri,
                    shape = m.shape,
                    notes = m.notes.orEmpty(),
                )
            }
            refreshWarnings()
        }
    }

    // ── Choosing the medicine ───────────────────────────────────────────────

    fun onQuery(text: String) = _state.update { it.copy(query = text, nameConfirmed = false, error = null) }

    fun pick(medicine: CatalogueMedicine) {
        val unit = Formats.unitForForm(medicine.dosageForm)
        _state.update {
            it.copy(
                query = medicine.brandName,
                nameConfirmed = true,
                catalogueItem = medicine,
                name = medicine.brandName,
                genericName = medicine.genericName.orEmpty(),
                strength = medicine.strength.orEmpty(),
                form = medicine.dosageForm ?: it.form,
                manufacturer = medicine.manufacturer.orEmpty(),
                unit = unit,
                shape = when (unit) { "capsule" -> "Capsule"; "ml", "drop", "spoon" -> "Liquid"; "tablet" -> "Round"; else -> "Other" },
                results = emptyList(),
                error = null,
            )
        }
        refreshWarnings()
    }

    /** From the scanner: the medicine it read, and the photo of the box it took. */
    fun pickScanned(catalogueId: Long, photo: String?) = viewModelScope.launch {
        catalogue.byId(catalogueId)?.let(::pick)
        if (photo != null) onPhoto(photo)
    }

    // ── Photo ───────────────────────────────────────────────────────────────

    fun onPhoto(ref: String?) {
        val s = _state.value
        // A photo taken earlier in this session and now replaced will never be used
        if (s.photo != null && s.photo != s.original?.photoUri && s.photo != ref) MedicinePhotos.delete(context, s.photo)
        _state.update { it.copy(photo = ref) }
    }

    fun importPhoto(uri: Uri) = viewModelScope.launch {
        MedicinePhotos.import(context, uri)?.let(::onPhoto)
    }

    /** Use exactly what the user typed (medicine not in the list). */
    fun useCustomName() {
        val typed = _state.value.query.trim()
        if (typed.isEmpty()) return
        _state.update { it.copy(name = typed, nameConfirmed = true, catalogueItem = null, results = emptyList(), error = null) }
        refreshWarnings()
    }

    fun changeMedicine() = _state.update { it.copy(nameConfirmed = false, results = emptyList()) }

    fun onName(v: String) { _state.update { it.copy(name = v) }; refreshWarnings() }
    fun onGeneric(v: String) { _state.update { it.copy(genericName = v) }; refreshWarnings() }
    fun onStrength(v: String) = _state.update { it.copy(strength = v) }
    fun onUnit(v: String) = _state.update { it.copy(unit = v) }

    // ── Schedule ────────────────────────────────────────────────────────────

    fun onMode(mode: ScheduleMode) = _state.update { s ->
        when (mode) {
            ScheduleMode.PATTERN -> s.copy(mode = mode, times = planPattern(s.pattern, s.mealTiming, s.alertType, s.times))
            ScheduleMode.CUSTOM -> s.copy(mode = mode, times = s.times.ifEmpty { listOf(DoseTimeDraft(nextKey++, minuteOfDay = 8 * 60, alertType = s.alertType)) })
            ScheduleMode.AS_NEEDED -> s.copy(mode = mode)
        }
    }

    fun onPattern(text: String) = _state.update { s ->
        val parsed = DosePattern.parse(text)
        if (parsed == null) s.copy(pattern = text)
        else s.copy(pattern = text, times = planPattern(text, s.mealTiming, s.alertType, s.times))
    }

    fun onMealTiming(timing: MealTiming) = _state.update { s ->
        if (s.mode == ScheduleMode.PATTERN) s.copy(mealTiming = timing, times = planPattern(s.pattern, timing, s.alertType, s.times))
        else s.copy(mealTiming = timing)
    }

    fun onTimeChanged(key: Long, minute: Int) = _state.update { s ->
        // A hand-picked time no longer follows the meal-time pattern
        s.copy(times = s.times.map { if (it.key == key) it.copy(minuteOfDay = minute, slot = null) else it }.sortedBy { it.minuteOfDay })
    }

    fun onQuantityChanged(key: Long, delta: Double) = _state.update { s ->
        s.copy(times = s.times.map { if (it.key == key) it.copy(quantity = (it.quantity + delta).coerceIn(0.25, 50.0)) else it })
    }

    fun toggleTimeAlert(key: Long) = _state.update { s ->
        s.copy(times = s.times.map { if (it.key == key) it.copy(alertType = if (it.alertType == AlertType.ALARM) AlertType.REMINDER else AlertType.ALARM) else it })
    }

    fun onAlertTypeForAll(type: AlertType) = _state.update { s -> s.copy(alertType = type, times = s.times.map { it.copy(alertType = type) }) }

    fun addTime() = _state.update { s ->
        val last = s.times.maxOfOrNull { it.minuteOfDay }
        val minute = if (last == null) 8 * 60 else (last + 4 * 60).coerceAtMost(23 * 60)
        s.copy(mode = if (s.mode == ScheduleMode.PATTERN) ScheduleMode.CUSTOM else s.mode,
            times = (s.times + DoseTimeDraft(nextKey++, minuteOfDay = minute, alertType = s.alertType)).sortedBy { it.minuteOfDay })
    }

    fun removeTime(key: Long) = _state.update { s ->
        s.copy(mode = if (s.mode == ScheduleMode.PATTERN) ScheduleMode.CUSTOM else s.mode, times = s.times.filter { it.key != key })
    }

    fun onDays(mask: Int) = _state.update { it.copy(daysMask = mask) }
    fun onMaxDaily(v: String) = _state.update { it.copy(maxDailyDoses = v.filter(Char::isDigit).take(2)) }

    // ── Course, stock, looks ────────────────────────────────────────────────

    fun onDuration(days: Int?) = _state.update { it.copy(durationDays = days) }
    fun onStartDate(date: LocalDate) = _state.update { it.copy(startDate = date) }
    fun onTrackStock(v: Boolean) = _state.update { it.copy(trackStock = v) }
    fun onStock(v: String) = _state.update { it.copy(stock = v.filter { c -> c.isDigit() || c == '.' }.take(7), trackStock = true) }
    fun onThreshold(days: Int) = _state.update { it.copy(refillThresholdDays = days.coerceIn(1, 30)) }
    fun onRefillReminders(v: Boolean) = _state.update { it.copy(refillReminders = v) }
    fun onColor(v: String) = _state.update { it.copy(color = v) }
    fun onShape(v: String) = _state.update { it.copy(shape = v) }
    fun onNotes(v: String) = _state.update { it.copy(notes = v) }

    private fun planPattern(pattern: String, timing: MealTiming, alertType: AlertType, existing: List<DoseTimeDraft>): List<DoseTimeDraft> {
        val parsed = DosePattern.parse(pattern) ?: return existing
        val s = settings.current
        // Re-use the ids of slots that already existed, so their history stays attached
        val bySlot = existing.filter { it.slot != null }.associateBy { it.slot }
        return MealSchedule.plan(parsed, timing, s.mealTimes, s.beforeMealMinutes, s.afterMealMinutes).map { planned ->
            val previous = bySlot[planned.slot]
            DoseTimeDraft(
                key = previous?.key ?: nextKey++,
                scheduleId = previous?.scheduleId ?: 0,
                minuteOfDay = planned.minuteOfDay,
                quantity = planned.quantity,
                alertType = previous?.alertType ?: alertType,
                slot = planned.slot,
            )
        }
    }

    private fun refreshWarnings() = viewModelScope.launch {
        val s = _state.value
        val current = repository.observeAll().first().map { it.medication }
        val warnings = SafetyCheck.check(
            name = s.name.ifBlank { s.query },
            genericName = s.genericName.ifBlank { null },
            allergies = settings.current.profile.allergies,
            currentMedicines = current,
            editingId = s.id.takeIf { it != 0L },
        )
        _state.update { it.copy(warnings = warnings) }
    }

    // ── Save ────────────────────────────────────────────────────────────────

    fun save() {
        val s = _state.value
        val name = s.name.ifBlank { if (!s.nameConfirmed) s.query else "" }.trim()
        when {
            name.isEmpty() -> return _state.update { it.copy(error = R.string.editor_error_name) }
            s.mode == ScheduleMode.PATTERN && DosePattern.parse(s.pattern) == null -> return _state.update { it.copy(error = R.string.editor_error_pattern) }
            s.mode != ScheduleMode.AS_NEEDED && s.times.isEmpty() -> return _state.update { it.copy(error = R.string.editor_error_times) }
            s.mode != ScheduleMode.AS_NEEDED && s.daysMask == 0 -> return _state.update { it.copy(error = R.string.editor_error_days) }
        }
        val stock = s.stock.toDoubleOrNull()
        val original = s.original
        val asNeeded = s.mode == ScheduleMode.AS_NEEDED
        val medication = (original ?: Medication(name = name)).copy(
            name = name,
            genericName = s.genericName.trim().ifBlank { null },
            strength = s.strength.trim(),
            form = s.form,
            manufacturer = s.manufacturer.trim().ifBlank { null },
            catalogueId = s.catalogueItem?.id,
            photoUri = s.photo,
            shape = s.shape,
            color = s.color,
            mealTiming = s.mealTiming,
            dosePattern = if (s.mode == ScheduleMode.PATTERN) DosePattern.parse(s.pattern)?.text else null,
            notes = s.notes.trim().ifBlank { null },
            isAsNeeded = asNeeded,
            maxDailyDoses = if (asNeeded) s.maxDailyDoses.toIntOrNull()?.takeIf { it > 0 } else null,
            trackStock = s.trackStock && stock != null,
            stock = if (s.trackStock && stock != null) stock else original?.stock ?: 0.0,
            unit = s.unit,
            refillThresholdDays = s.refillThresholdDays,
            refillReminders = s.refillReminders,
            lowStockAlertDay = if (stock != null && stock != original?.stock) null else original?.lowStockAlertDay,
            startDate = s.startDate.toEpochDay(),
            endDate = s.durationDays?.let { s.startDate.plusDays(it - 1L).toEpochDay() },
        )
        val schedules = if (asNeeded) emptyList() else s.times.map { t ->
            Schedule(
                id = t.scheduleId,
                medicationId = s.id,
                minuteOfDay = t.minuteOfDay,
                daysMask = s.daysMask,
                quantity = t.quantity,
                alertType = t.alertType,
                enabled = true,
                slot = if (s.mode == ScheduleMode.PATTERN) t.slot else null,
            )
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val id = repository.save(medication, schedules)
            // The old photo was replaced or removed
            original?.photoUri?.takeIf { it != s.photo }?.let { MedicinePhotos.delete(context, it) }
            _state.update { it.copy(saving = false, savedId = id) }
        }
    }
}
