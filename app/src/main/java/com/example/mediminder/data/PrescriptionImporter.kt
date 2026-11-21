package com.example.mediminder.data

import com.example.mediminder.data.catalogue.CatalogueMedicine
import com.example.mediminder.data.catalogue.CatalogueRepository
import com.example.mediminder.data.local.entity.MealTiming
import com.example.mediminder.data.local.entity.Medication
import com.example.mediminder.data.local.entity.Schedule
import com.example.mediminder.data.settings.SettingsRepository
import com.example.mediminder.domain.CareKind
import com.example.mediminder.domain.CareSites
import com.example.mediminder.domain.DosePattern
import com.example.mediminder.domain.MealSchedule
import com.example.mediminder.domain.PrescriptionParser
import com.example.mediminder.util.Formats
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** One medicine found on a prescription, with what was read about taking it. */
data class PrescriptionDraft(
    val medicine: CatalogueMedicine,
    val instructions: PrescriptionParser.Instructions,
    /** A dosage form was written before it ("Tab.") or a dose was found: very likely right. */
    val confident: Boolean,
    /** The text it was read from, to show. */
    val line: String,
) {
    /** Can be added straight away: it says how to take it. */
    val ready: Boolean get() = instructions.pattern != null || instructions.asNeeded
}

/**
 * Turns the text of a prescription photo into medicines: each brand found in the Bangladeshi
 * catalogue, with the pattern, food timing and course length written next to it (on the
 * same line or the lines after it).
 */
@Singleton
class PrescriptionImporter @Inject constructor(
    private val catalogue: CatalogueRepository,
    private val medicines: MedicineRepository,
    private val settings: SettingsRepository,
) {
    suspend fun read(text: String): List<PrescriptionDraft> {
        val lines = text.lines().map { it.trim() }.filter { it.length >= 2 }
        val drafts = mutableListOf<PrescriptionDraft>()
        var current: PrescriptionDraft? = null
        for (line in lines) {
            val found = find(line)
            val said = PrescriptionParser.read(line)
            val known = drafts.any { it.medicine.brandSearch == found?.brandSearch } || current?.medicine?.brandSearch == found?.brandSearch
            if (found != null && !known) {
                current?.let(drafts::add)
                current = PrescriptionDraft(found, said, confident = PrescriptionParser.formOf(line) != null || !said.isEmpty, line = line)
            } else if (current != null && !said.isEmpty) {
                // "1+0+1 after meal x 7 days" on the line under the name
                val merged = current.instructions.orElse(said)
                current = current.copy(instructions = merged, confident = true, line = current.line + " · " + line)
            }
        }
        current?.let(drafts::add)
        return drafts
    }

    /** The catalogue product a line names: the brand, then the strength and form it mentions. */
    private suspend fun find(line: String): CatalogueMedicine? {
        val best = catalogue.matchScannedText(line, limit = 6).firstOrNull() ?: return null
        val variants = catalogue.search(best.brandName, limit = 30).filter { it.brandSearch == best.brandSearch }.ifEmpty { listOf(best) }
        val strengths = PrescriptionParser.strengths(line)
        val form = PrescriptionParser.formOf(line)
        return variants.withIndex().maxByOrNull { (i, m) ->
            var s = -i   // the catalogue's own order puts the everyday product first
            if (strengths.any { n -> m.strength.orEmpty().contains(Regex("""(?<![\d.])${Regex.escape(n)}(?![\d])""")) }) s += 100
            if (form != null && m.dosageForm.orEmpty().lowercase().contains(form)) s += 50
            s
        }?.value
    }

    /** Saves the chosen drafts as medicines, with times from the meal times. Returns how many. */
    suspend fun add(drafts: List<PrescriptionDraft>, today: LocalDate = LocalDate.now()): Int {
        val s = settings.current
        var added = 0
        drafts.filter { it.ready }.forEachIndexed { i, d ->
            val m = d.medicine
            val insulin = m.genericName.orEmpty().contains("insulin", ignoreCase = true)
            val unit = if (insulin) "unit" else Formats.unitForForm(m.dosageForm)
            val kind = CareKind.forUnit(unit)
            val noFood = kind == CareKind.TOPICAL || kind == CareKind.DROPS || kind == CareKind.INHALED
            val meal = if (noFood) MealTiming.ANY else d.instructions.meal ?: MealTiming.AFTER_MEAL
            val pattern = d.instructions.pattern?.let(DosePattern::parse)
            val schedules = pattern?.let { MealSchedule.plan(it, meal, s.mealTimes, s.beforeMealMinutes, s.afterMealMinutes) }.orEmpty().map { p ->
                Schedule(medicationId = 0, minuteOfDay = p.minuteOfDay, quantity = p.quantity, alertType = s.defaultAlertType, slot = p.slot)
            }
            medicines.save(
                Medication(
                    name = m.brandName,
                    genericName = m.genericName,
                    strength = m.strength.orEmpty(),
                    form = m.dosageForm ?: "Tablet",
                    manufacturer = m.manufacturer,
                    catalogueId = m.id,
                    unit = unit,
                    kind = kind,
                    site = when {
                        kind == CareKind.DROPS -> CareSites.dropSite(CareSites.dropOrgan(m.dosageForm.orEmpty()), "both")
                        insulin -> CareSites.ROTATE
                        else -> null
                    },
                    shape = when (unit) { "capsule" -> "Capsule"; "ml", "drop", "spoon" -> "Liquid"; "tablet" -> "Round"; else -> "Other" },
                    color = COLORS[i % COLORS.size],
                    mealTiming = meal,
                    dosePattern = pattern?.text,
                    isAsNeeded = pattern == null && d.instructions.asNeeded,
                    startDate = today.toEpochDay(),
                    endDate = d.instructions.days?.let { today.plusDays(it - 1L).toEpochDay() },
                ),
                schedules,
            )
            added++
        }
        return added
    }

    private companion object {
        /** The wizard's colours, so each new medicine looks different. */
        val COLORS = listOf("#3B82F6", "#8B5CF6", "#F59E0B", "#EC4899", "#14B8A6", "#EF4444", "#64748B")
    }
}
