package com.example.mediminder.data.catalogue

import javax.inject.Inject
import javax.inject.Singleton

/** Search over the bundled medicine catalogue (~55,000 Bangladeshi products, fully offline). */
@Singleton
class CatalogueRepository @Inject constructor(
    private val dao: CatalogueDao,
) {
    /**
     * Ranked search by brand or generic name:
     * exact brand → brand prefix → generic prefix → anywhere in brand/generic.
     */
    suspend fun search(query: String, limit: Int = 30): List<CatalogueMedicine> {
        val key = CatalogueText.searchKey(query)
        if (key.length < 2) return emptyList()
        val end = key + "{"

        val results = LinkedHashMap<Long, CatalogueMedicine>()
        dao.brandPrefix(key, end, limit).forEach { results[it.id] = it }
        if (results.size < limit) dao.genericPrefix(key, end, limit).forEach { results.putIfAbsent(it.id, it) }
        if (results.size < limit / 2 && key.length >= 3) dao.contains(key, limit).forEach { results.putIfAbsent(it.id, it) }

        return results.values
            .sortedWith(
                compareBy<CatalogueMedicine> {
                    when {
                        it.brandSearch == key -> 0
                        it.brandSearch.startsWith(key) -> 1
                        it.genericSearch.startsWith(key) -> 2
                        else -> 3
                    }
                }.thenBy { it.brandSearch.length }
                    .thenBy { it.brandSearch }
                    // Within a brand: what people take at home first (Napa tablet before Napa IV infusion)
                    .thenBy { formRank(it.dosageForm) }
                    .thenBy { strengthNumber(it.strength) }
            )
            .take(limit)
    }

    /** Tablets & capsules first, then liquids and drops, then creams, injections last. */
    private fun formRank(form: String?): Int {
        val f = form.orEmpty().lowercase()
        return when {
            "tablet" in f || "capsule" in f -> 0
            "syrup" in f || "suspension" in f || "solution" in f || "drop" in f -> 1
            "inhal" in f || "sachet" in f || "powder" in f -> 2
            "cream" in f || "ointment" in f || "gel" in f -> 3
            "injection" in f || "infusion" in f || "vial" in f -> 5
            else -> 4
        }
    }

    /** "500 mg" → 500.0, so 120 mg sorts before 500 mg (not alphabetically). */
    private fun strengthNumber(strength: String?): Double =
        Regex("""[\d.]+""").find(strength.orEmpty())?.value?.toDoubleOrNull() ?: Double.MAX_VALUE

    suspend fun byId(id: Long): CatalogueMedicine? = dao.byId(id)

    /**
     * Finds catalogue brands mentioned in text read off a medicine box by the camera.
     * Words and pairs of adjacent words are matched exactly against brand names.
     */
    suspend fun matchScannedText(text: String, limit: Int = 12): List<CatalogueMedicine> {
        val words = text.split(Regex("[^A-Za-z0-9]+")).filter { it.length >= 3 }
        if (words.isEmpty()) return emptyList()

        // Candidate brand keys: single words and adjacent pairs ("NAPA EXTRA" → napaextra),
        // remembering where in the text each first appeared.
        data class Candidate(val position: Int, val twoWords: Boolean)
        val candidates = LinkedHashMap<String, Candidate>()
        words.forEachIndexed { i, word ->
            candidates.putIfAbsent(CatalogueText.searchKey(word), Candidate(i, twoWords = false))
            if (i + 1 < words.size) candidates.putIfAbsent(CatalogueText.searchKey(word + words[i + 1]), Candidate(i, twoWords = true))
        }
        val keys = candidates.keys.filter { it.length >= 3 }.take(400)
        if (keys.isEmpty()) return emptyList()

        val found = dao.byBrandKeys(keys, 300)
        // The words that are generic names on this box ("Paracetamol 500 mg") are not the brand
        val genericKeys = found.flatMap { CatalogueText.ingredients(it.genericName) }.toSet()
        fun score(m: CatalogueMedicine): Int {
            val c = candidates[m.brandSearch] ?: return Int.MIN_VALUE
            var s = 0
            if (c.twoWords) s += 20                      // a two-word brand is very specific
            if (m.brandSearch in genericKeys || m.brandSearch == m.genericSearch) s -= 50
            s -= c.position                              // brand names are printed first / largest
            return s
        }
        return found
            .sortedWith(compareByDescending<CatalogueMedicine> { score(it) }.thenByDescending { it.brandSearch.length }.thenBy { it.strength.orEmpty() })
            .take(limit)
    }

    /**
     * Other brands of the same medicine (same generic and strength) — handy when the usual
     * brand is out of stock at the pharmacy, or to find a cheaper one.
     */
    suspend fun alternatives(medicine: CatalogueMedicine, limit: Int = 20): List<CatalogueMedicine> =
        alternatives(medicine.genericName, medicine.strength, medicine.id, limit)

    suspend fun alternatives(genericName: String?, strength: String?, excludeId: Long = -1, limit: Int = 20): List<CatalogueMedicine> {
        val genericKey = CatalogueText.searchKey(genericName)
        if (genericKey.length < 3) return emptyList()
        val strengthKey = CatalogueText.strengthKey(strength)
        return dao.sameGeneric(genericKey, excludeId, 300)
            .filter { strengthKey.isEmpty() || CatalogueText.strengthKey(it.strength) == strengthKey }
            .distinctBy { Triple(it.brandSearch, CatalogueText.strengthKey(it.strength), it.manufacturer) }
            .take(limit)
    }
}
