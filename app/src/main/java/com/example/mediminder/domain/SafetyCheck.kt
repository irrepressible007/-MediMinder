package com.example.mediminder.domain

import com.example.mediminder.data.catalogue.CatalogueText
import com.example.mediminder.data.local.entity.Medication

/**
 * Checks a medicine against the user's recorded allergies and the medicines they already take.
 *
 * Bangladesh sells many brands of the same generic (Napa, Ace and Renova are all
 * paracetamol), so taking two "different" medicines that are really the same drug is an
 * easy way to overdose. This is a second pair of eyes — not a drug-interaction database.
 */
sealed class SafetyWarning {
    /** The user lists this ingredient (or the brand) as an allergy. */
    data class Allergy(val allergen: String) : SafetyWarning()

    /** The medicine belongs to a drug class the user is allergic to. */
    data class AllergyClass(val drugClass: String, val allergen: String) : SafetyWarning()

    /** Same active ingredient as a medicine already being taken. */
    data class DuplicateIngredient(val ingredient: String, val otherMedicine: String) : SafetyWarning()
}

object SafetyCheck {
    private data class DrugClass(val label: String, val allergens: List<String>, val members: List<String>)

    private val CLASSES = listOf(
        DrugClass("penicillin", listOf("penicillin", "amoxicillin", "ampicillin"),
            listOf("penicillin", "amoxicillin", "ampicillin", "flucloxacillin", "cloxacillin", "piperacillin", "amoxiclav", "phenoxymethyl")),
        DrugClass("cephalosporin", listOf("cephalosporin", "cefixime", "cephalexin", "ceftriaxone", "cefuroxime"),
            listOf("cefixime", "cefuroxime", "ceftriaxone", "cephalexin", "cefalexin", "cefradine", "cephradine", "cefpodoxime", "cefaclor", "cefepime", "cefotaxime")),
        DrugClass("sulfa", listOf("sulfa", "sulpha", "sulfonamide", "sulphonamide"),
            listOf("sulfamethoxazole", "sulphamethoxazole", "cotrimoxazole", "sulfasalazine", "sulfadiazine")),
        DrugClass("NSAID", listOf("nsaid", "aspirin", "ibuprofen", "diclofenac", "naproxen"),
            listOf("aspirin", "ibuprofen", "naproxen", "diclofenac", "ketorolac", "indomethacin", "mefenamic", "aceclofenac", "etoricoxib", "ketoprofen", "piroxicam")),
        DrugClass("macrolide", listOf("macrolide", "erythromycin", "azithromycin"),
            listOf("azithromycin", "clarithromycin", "erythromycin", "roxithromycin")),
        DrugClass("fluoroquinolone", listOf("quinolone", "ciprofloxacin", "levofloxacin"),
            listOf("ciprofloxacin", "levofloxacin", "moxifloxacin", "ofloxacin", "norfloxacin")),
        DrugClass("opioid", listOf("opioid", "opiate", "codeine", "morphine", "tramadol"),
            listOf("codeine", "morphine", "tramadol", "oxycodone", "pethidine", "fentanyl", "tapentadol")),
    )

    private val NO_ALLERGY = setOf("none", "no", "nil", "na", "nkda", "noknownallergies", "noknowndrugallergies", "notapplicable", "নেই")

    /** "Penicillin, peanuts; latex" → ["penicillin", "peanuts", "latex"] */
    fun allergyTerms(allergies: String): List<String> =
        allergies.split(',', ';', '/', '\n', '|')
            .flatMap { it.split(" and ", " ও ") }
            .map { it.trim() }
            .filter { CatalogueText.searchKey(it).length >= 3 && CatalogueText.searchKey(it) !in NO_ALLERGY }

    fun check(
        name: String,
        genericName: String?,
        allergies: String,
        currentMedicines: List<Medication>,
        editingId: Long? = null,
    ): List<SafetyWarning> {
        val warnings = mutableListOf<SafetyWarning>()
        val drugText = CatalogueText.searchKey(name) + " " + CatalogueText.searchKey(genericName)
        val ingredients = CatalogueText.ingredients(genericName)

        // 1. Allergies
        val terms = allergyTerms(allergies)
        val direct = terms.firstOrNull { term ->
            val key = CatalogueText.searchKey(term)
            ingredients.any { it.contains(key) || key.contains(it) } || CatalogueText.searchKey(name) == key
        }
        if (direct != null) {
            warnings += SafetyWarning.Allergy(direct)
        } else {
            val allergyKeys = terms.map { CatalogueText.searchKey(it) }
            CLASSES.firstOrNull { cls ->
                cls.members.any { drugText.contains(it) } && allergyKeys.any { key -> cls.allergens.any { key.contains(it) } }
            }?.let { cls ->
                val allergen = terms.first { term -> cls.allergens.any { CatalogueText.searchKey(term).contains(it) } }
                warnings += SafetyWarning.AllergyClass(cls.label, allergen)
            }
        }

        // 2. Same active ingredient as something already being taken
        if (ingredients.isNotEmpty()) {
            for (other in currentMedicines) {
                if (other.archived || other.id == editingId) continue
                val shared = CatalogueText.ingredients(other.genericName).intersect(ingredients)
                if (shared.isNotEmpty()) {
                    val readable = genericName.orEmpty().split('+', ',').map { it.trim() }
                        .firstOrNull { CatalogueText.searchKey(it.substringBefore('(')) in shared } ?: shared.first()
                    warnings += SafetyWarning.DuplicateIngredient(readable, other.name)
                }
            }
        }
        return warnings
    }
}
