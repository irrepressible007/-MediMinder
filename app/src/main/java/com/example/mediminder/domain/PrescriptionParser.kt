package com.example.mediminder.domain

import com.example.mediminder.data.local.entity.MealTiming

/**
 * Reads the dose instructions on a prescription line, as Bangladeshi doctors write them:
 * "1+0+1", "1-1-1", "½+0+½", the Latin shorthand (OD, BD, TDS, QDS, HS, SOS), before or after
 * food (including a.c. / p.c.), and how long ("7 days", "2 weeks", "1 month").
 *
 * Text comes from the camera, so the usual reading slips are allowed for: "l" or "I" for 1,
 * "o" or "O" for 0.
 */
object PrescriptionParser {
    data class Instructions(
        /** A pattern such as "1+0+1", or null. */
        val pattern: String? = null,
        val meal: MealTiming? = null,
        val days: Int? = null,
        /** SOS / PRN: only when needed. */
        val asNeeded: Boolean = false,
    ) {
        val isEmpty: Boolean get() = pattern == null && meal == null && days == null && !asNeeded

        /** This line's details, with [other]'s filling any gaps. */
        fun orElse(other: Instructions) = Instructions(
            pattern ?: other.pattern, meal ?: other.meal, days ?: other.days, asNeeded || other.asNeeded,
        )
    }

    private val PATTERN = Regex("""(?<![0-9A-Za-z])([0-9½¼lIoO]{1,2}(?:\s*[+\-–]\s*[0-9½¼lIoO]{1,2}){2,3})(?![0-9])""")
    private val SHORTHAND = mapOf(
        "od" to "1+0+0", "bd" to "1+0+1", "bid" to "1+0+1", "tds" to "1+1+1", "tid" to "1+1+1",
        "qds" to "1+1+1+1", "qid" to "1+1+1+1", "hs" to "0+0+1",
    )
    private val SHORTHAND_WORD = Regex("""(?i)\b(od|bd|bid|tds|tid|qds|qid|hs|sos|prn)\b""")
    private val BEFORE = Regex("""(?i)before\s+(meal|meals|food|eating)|empty\s+stomach|\ba\s?\.?\s?c\s?\.|\ba/c\b|\bac\b""")
    private val AFTER = Regex("""(?i)after\s+(meal|meals|food|eating)|\bp\s?\.?\s?c\s?\.|\bp/c\b|\bpc\b""")
    private val WITH = Regex("""(?i)with\s+(meal|meals|food)""")
    private val DAYS = Regex("""(?i)(\d{1,3})\s*(days?|d)\b""")
    private val WEEKS = Regex("""(?i)(\d{1,2})\s*(weeks?|wks?|wk)\b""")
    private val MONTHS = Regex("""(?i)(\d{1,2})\s*(months?|mon|mo)\b""")

    fun read(line: String): Instructions {
        val pattern = PATTERN.findAll(line).firstNotNullOfOrNull { normalise(it.value) }
        val word = SHORTHAND_WORD.find(line)?.value?.lowercase()
        val meal = when {
            BEFORE.containsMatchIn(line) -> MealTiming.BEFORE_MEAL
            AFTER.containsMatchIn(line) -> MealTiming.AFTER_MEAL
            WITH.containsMatchIn(line) -> MealTiming.WITH_MEAL
            word == "hs" -> MealTiming.BEDTIME
            else -> null
        }
        val days = DAYS.find(line)?.groupValues?.get(1)?.toIntOrNull()
            ?: WEEKS.find(line)?.groupValues?.get(1)?.toIntOrNull()?.times(7)
            ?: MONTHS.find(line)?.groupValues?.get(1)?.toIntOrNull()?.times(30)
        return Instructions(
            pattern = pattern ?: word?.let { SHORTHAND[it] },
            meal = meal,
            days = days?.takeIf { it in 1..365 },
            asNeeded = pattern == null && (word == "sos" || word == "prn"),
        )
    }

    /** "l-O-1" → "1+0+1", if it is a believable pattern (no part above 4: not a date or a phone number). */
    private fun normalise(raw: String): String? {
        val text = raw.replace(" ", "").map { c ->
            when (c) { 'l', 'I' -> '1'; 'o', 'O' -> '0'; '-', '–' -> '+'; else -> c }
        }.joinToString("")
        val parts = text.split('+')
        if (parts.any { p -> p.toDoubleOrNull()?.let { it > 4 } == true }) return null
        return DosePattern.parse(text)?.text
    }

    /** Dosage forms written before a medicine's name: "Tab. Napa", "Cap Seclo", "Syp. Ace". */
    val FORM_PREFIX = mapOf(
        "tab" to "tablet", "tabs" to "tablet", "cap" to "capsule", "caps" to "capsule",
        "syp" to "syrup", "syr" to "syrup", "susp" to "suspension", "inj" to "injection",
        "drop" to "drop", "drops" to "drop", "oint" to "ointment", "cream" to "cream", "gel" to "gel",
        "inh" to "inhaler", "sol" to "solution", "supp" to "suppositor", "sachet" to "sachet", "powder" to "powder",
    )

    /** The dosage form named at the start of a line, e.g. "tablet" for "Tab. Napa 500". */
    fun formOf(line: String): String? {
        val first = line.trim().trimStart('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.', ')', ' ', '-', '•')
            .split(Regex("[^A-Za-z]+")).firstOrNull { it.isNotEmpty() }?.lowercase() ?: return null
        return FORM_PREFIX[first]
    }

    /** Numbers followed by a unit ("500mg", "20 mg", "0.5%"), to pick the right strength. */
    fun strengths(line: String): Set<String> =
        Regex("""(?i)(\d+(?:\.\d+)?)\s*(mg|mcg|g|ml|%|iu|unit)""").findAll(line).map { m ->
            // "0.50" → "0.5", but "500" stays "500"
            m.groupValues[1].let { n -> if ('.' in n) n.trimEnd('0').trimEnd('.') else n }
        }.toSet()
}
