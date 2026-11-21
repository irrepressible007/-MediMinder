package com.example.mediminder.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.mediminder.R
import com.example.mediminder.data.local.entity.DoseLog
import com.example.mediminder.data.local.entity.MealTiming
import com.example.mediminder.data.local.entity.MedicationWithSchedules
import com.example.mediminder.data.local.entity.Reading
import com.example.mediminder.data.settings.AppSettings
import com.example.mediminder.domain.Adherence
import com.example.mediminder.domain.CareKind
import com.example.mediminder.domain.DayAdherence
import com.example.mediminder.domain.MeasureType
import com.example.mediminder.domain.MedicationAdherence
import com.example.mediminder.domain.ReadingLevel
import com.example.mediminder.domain.Readings
import com.example.mediminder.domain.SugarTiming
import com.example.mediminder.domain.toLocalDateTime
import com.example.mediminder.util.CareText
import com.example.mediminder.util.Formats
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/** One medicine on the report: what, how it is taken, and how often it was. */
data class ReportMedicine(val name: String, val how: String, val rate: Int?, val taken: Int, val expected: Int)

/** Everything the doctor's report shows, worked out before any drawing. */
data class DoctorReportData(
    val person: String,
    val age: Int?,
    val bloodGroup: String,
    val allergies: String,
    val conditions: String,
    val from: LocalDate,
    val to: LocalDate,
    val rate: Int?,
    val taken: Int,
    val missed: Int,
    val skipped: Int,
    val days: List<DayAdherence>,
    val medicines: List<ReportMedicine>,
    /** Readings in the period by kind, newest first. */
    val readings: Map<MeasureType, List<Reading>>,
    val habits: List<MedicationAdherence>,
)

/**
 * A one-or-more page PDF for a doctor's visit: the person, their medicines and how regularly
 * they were taken, health readings with charts, and habits. Drawn with Android's own PDF
 * support, in the app's language.
 */
object DoctorReport {

    fun build(
        ctx: Context,
        settings: AppSettings,
        meds: List<MedicationWithSchedules>,
        logs: List<DoseLog>,
        readings: List<Reading>,
        days: Int,
        today: LocalDate = LocalDate.now(),
        now: LocalDateTime = LocalDateTime.now(),
    ): DoctorReportData {
        val from = today.minusDays(days - 1L)
        val medicines = meds.filter { it.medication.kind.isMedicine }
        val summary = Adherence.summarise(medicines, logs, from, today, now)
        val habits = Adherence.summarise(meds.filter { it.medication.kind == CareKind.HABIT }, logs, from, today, now)
        val byId = summary.perMedication.associateBy { it.medicationId }
        val p = settings.profile
        return DoctorReportData(
            person = settings.userName.ifBlank { ctx.getString(R.string.pdf_person) },
            age = p.birthYear?.let { today.year - it },
            bloodGroup = p.bloodGroup,
            allergies = p.allergies,
            conditions = p.conditions,
            from = from,
            to = today,
            rate = summary.rate,
            taken = summary.taken,
            missed = summary.missed,
            skipped = summary.skipped,
            days = summary.days,
            medicines = medicines.filter { !it.medication.archived }.map { item ->
                val m = item.medication
                val a = byId[m.id]
                ReportMedicine(
                    name = listOf(m.name, m.strength).filter { it.isNotBlank() }.joinToString(" "),
                    how = howText(ctx, item),
                    rate = a?.rate,
                    taken = a?.taken ?: 0,
                    expected = a?.expected ?: 0,
                )
            },
            readings = MeasureType.entries.associateWith { t ->
                readings.filter { it.type == t.name && !it.takenAt.toLocalDateTime().toLocalDate().isBefore(from) }.sortedByDescending { it.takenAt }
            }.filterValues { it.isNotEmpty() },
            habits = habits.perMedication.filter { it.expected > 0 },
        )
    }

    /** "1+0+1 · Take 1 tablet · after food · 8:15 AM, 9:15 PM", or "When needed". */
    private fun howText(ctx: Context, item: MedicationWithSchedules): String {
        val m = item.medication
        if (m.isAsNeeded) return ctx.getString(R.string.pdf_as_needed)
        val schedules = item.schedules.filter { it.enabled }.sortedBy { it.minuteOfDay }
        return listOfNotNull(
            m.dosePattern?.let { Formats.digits(ctx, it) },
            schedules.firstOrNull()?.let { CareText.line(ctx, m.kind, it.quantity, m.unit, m.site?.takeIf { s -> s != "rotate" }) },
            m.mealTiming.takeIf { it != MealTiming.ANY }?.let { Formats.mealTiming(ctx, it) },
            schedules.takeIf { it.isNotEmpty() }?.joinToString(", ") { Formats.time(ctx, it.minuteOfDay) },
        ).joinToString(" · ")
    }

    /** Where pages are drawn: PDF pages on the phone, pictures in tests. */
    interface PageSink {
        fun start(number: Int, width: Int, height: Int): Canvas
        fun finish()
    }

    /** Writes the report to [file] as a PDF. */
    fun write(ctx: Context, data: DoctorReportData, file: File) {
        val doc = PdfDocument()
        try {
            var page: PdfDocument.Page? = null
            draw(ctx, data, object : PageSink {
                override fun start(number: Int, width: Int, height: Int): Canvas =
                    doc.startPage(PdfDocument.PageInfo.Builder(width, height, number).create()).also { page = it }.canvas
                override fun finish() { page?.let(doc::finishPage); page = null }
            })
            file.parentFile?.mkdirs()
            file.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    /** Draws every page of the report into [sink]. */
    fun draw(ctx: Context, data: DoctorReportData, sink: PageSink) {
        Pages(ctx, sink).apply { draw(data) }.finish()
    }

    /** A4 at 72 points an inch. */
    const val PAGE_WIDTH = 595
    const val PAGE_HEIGHT = 842

    private class Pages(private val ctx: Context, private val sink: PageSink) {
        private val width = PAGE_WIDTH
        private val height = PAGE_HEIGHT
        private val margin = 40f
        private val content = width - 2 * margin
        private var open = false
        private lateinit var canvas: Canvas
        private var y = margin
        private var number = 0

        private val ink = Color.rgb(26, 28, 36)
        private val sub = Color.rgb(85, 90, 102)
        private val brand = Color.rgb(39, 84, 197)
        private val green = Color.rgb(21, 128, 61)
        private val red = Color.rgb(185, 28, 28)
        private val amber = Color.rgb(180, 83, 9)
        private val rule = Color.rgb(220, 222, 228)

        fun draw(d: DoctorReportData) {
            newPage()
            text(ctx.getString(R.string.pdf_title), 22f, bold = true, color = brand)
            text(ctx.getString(R.string.pdf_for, d.person, Formats.date(ctx, d.from), Formats.date(ctx, d.to)), 13f, color = sub)
            val facts = listOfNotNull(
                d.age?.let { ctx.getString(R.string.pdf_age, Formats.number(ctx, it)) },
                d.bloodGroup.takeIf { it.isNotBlank() }?.let { ctx.getString(R.string.pdf_blood, it) },
            ).joinToString(" · ")
            if (facts.isNotBlank()) text(facts, 12f)
            if (d.allergies.isNotBlank()) text(ctx.getString(R.string.pdf_allergies, d.allergies), 12f, color = red, bold = true)
            if (d.conditions.isNotBlank()) text(ctx.getString(R.string.pdf_conditions, d.conditions), 12f)
            gap(10f)

            heading(ctx.getString(R.string.pdf_adherence))
            text(
                ctx.getString(
                    R.string.pdf_adherence_line,
                    d.rate?.let { Formats.number(ctx, it) + "%" } ?: "—",
                    Formats.number(ctx, d.taken), Formats.number(ctx, d.missed), Formats.number(ctx, d.skipped),
                ),
                12f,
            )
            if (d.days.any { it.expected > 0 }) bars(d.days)
            gap(8f)

            heading(ctx.getString(R.string.pdf_medicines))
            if (d.medicines.isEmpty()) text(ctx.getString(R.string.pdf_none), 12f, color = sub)
            else {
                row(listOf(ctx.getString(R.string.pdf_col_medicine), ctx.getString(R.string.pdf_col_how), ctx.getString(R.string.pdf_col_taken)), bold = true)
                d.medicines.forEach { m ->
                    row(listOf(m.name, m.how, m.rate?.let { Formats.number(ctx, it) + "% (" + Formats.number(ctx, m.taken) + "/" + Formats.number(ctx, m.expected) + ")" } ?: "—"))
                }
            }
            gap(8f)

            if (d.readings.isNotEmpty()) {
                heading(ctx.getString(R.string.pdf_readings))
                d.readings.forEach { (type, list) -> readingBlock(type, list) }
            }

            if (d.habits.isNotEmpty()) {
                heading(ctx.getString(R.string.health_habits))
                d.habits.forEach { h ->
                    text("• ${h.name}: " + (h.rate?.let { Formats.number(ctx, it) + "%" } ?: "—") + " (" + Formats.number(ctx, h.taken) + "/" + Formats.number(ctx, h.expected) + ")", 12f)
                }
            }
        }

        fun finish() { if (open) sink.finish(); open = false }

        private fun newPage() {
            finish()
            number++
            canvas = sink.start(number, width, height)
            open = true
            y = margin
            // footer on every page
            val footer = layout(ctx.getString(R.string.pdf_footer, Formats.number(ctx, number)), 9f, sub, content)
            canvas.save()
            canvas.translate(margin, height - margin + 6f - footer.height)
            footer.draw(canvas)
            canvas.restore()
        }

        private fun room(space: Float) { if (y + space > height - margin - 28f) newPage() }
        private fun gap(h: Float) { y += h }

        private fun paint(size: Float, color: Int, bold: Boolean) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        private fun layout(s: String, size: Float, color: Int, w: Float, bold: Boolean = false): StaticLayout =
            StaticLayout.Builder.obtain(s, 0, s.length, paint(size, color, bold), w.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2f, 1f)
                .build()

        private fun text(s: String, size: Float, bold: Boolean = false, color: Int = ink) {
            val l = layout(s, size, color, content, bold)
            room(l.height.toFloat())
            canvas.save(); canvas.translate(margin, y); l.draw(canvas); canvas.restore()
            y += l.height + 3f
        }

        private fun heading(s: String) {
            // never leave a heading alone at the bottom of a page
            room(90f)
            gap(4f)
            text(s, 15f, bold = true, color = brand)
            canvas.drawLine(margin, y, width - margin, y, Paint().apply { color = rule; strokeWidth = 1f })
            gap(6f)
        }

        /** A table row: medicine, how, taken. Cells wrap; the row is as tall as its tallest cell. */
        private fun row(cells: List<String>, bold: Boolean = false) {
            val widths = listOf(content * 0.30f, content * 0.50f, content * 0.20f)
            val layouts = cells.mapIndexed { i, s -> layout(s, 11f, if (bold) sub else ink, widths[i] - 8f, bold) }
            val h = layouts.maxOf { it.height }.toFloat() + 8f
            room(h)
            var x = margin
            layouts.forEachIndexed { i, l ->
                canvas.save(); canvas.translate(x, y + 4f); l.draw(canvas); canvas.restore()
                x += widths[i]
            }
            y += h
            canvas.drawLine(margin, y, width - margin, y, Paint().apply { color = rule; strokeWidth = 0.6f })
        }

        /** One bar per day: green for taken, red for missed. */
        private fun bars(days: List<DayAdherence>) {
            val h = 46f
            room(h + 8f)
            val n = days.size.coerceAtLeast(1)
            val gapX = if (n > 40) 1f else 2.5f
            val w = (content - gapX * (n - 1)) / n
            val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(236, 237, 241) }
            val taken = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green }
            val missed = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = red }
            days.forEachIndexed { i, d ->
                val x = margin + i * (w + gapX)
                canvas.drawRect(x, y, x + w, y + h, track)
                if (d.expected > 0) {
                    val th = h * d.taken / d.expected
                    val mh = h * d.missed / d.expected
                    canvas.drawRect(x, y + h - th, x + w, y + h, taken)
                    if (mh > 0) canvas.drawRect(x, y, x + w, y + mh, missed)
                }
            }
            y += h + 8f
        }

        /** Latest, average, lowest and highest; a small chart; the last few readings. */
        private fun readingBlock(type: MeasureType, list: List<Reading>) {
            room(60f)
            text(CareText.measureName(ctx, type), 13f, bold = true)
            val avg1 = list.map { it.value }.average()
            val avg2 = list.mapNotNull { it.value2 }.takeIf { it.isNotEmpty() }?.average()
            fun round(v: Double) = if (type == MeasureType.BLOOD_PRESSURE || type == MeasureType.OXYGEN) Math.round(v).toDouble() else Math.round(v * 10) / 10.0
            val low = list.minBy { it.value }
            val high = list.maxBy { it.value }
            val count = Formats.digits(ctx, ctx.resources.getQuantityString(R.plurals.readings_count, list.size, list.size))
            text(
                if (list.size == 1) ctx.getString(R.string.pdf_reading_single, CareText.value(ctx, type, list.first().value, list.first().value2), count)
                else ctx.getString(
                    R.string.pdf_reading_summary,
                    CareText.value(ctx, type, list.first().value, list.first().value2),
                    CareText.value(ctx, type, round(avg1), avg2?.let(::round)),
                    CareText.value(ctx, type, low.value, low.value2),
                    CareText.value(ctx, type, high.value, high.value2),
                    count,
                ),
                11f, color = sub,
            )
            if (list.size >= 2) chart(type, list.sortedBy { it.takenAt }.takeLast(30))
            list.take(8).forEach { r ->
                val level = Readings.level(type, r.value, r.value2, SugarTiming.entries.firstOrNull { it.name == r.timing })
                val word = CareText.levelName(ctx, level)
                val at = r.takenAt.toLocalDateTime()
                val line = Formats.shortDate(ctx, at.toLocalDate()) + ", " + Formats.time(ctx, at.hour * 60 + at.minute) + " — " +
                    CareText.value(ctx, r) + (word?.let { " ($it)" } ?: "")
                text(line, 11f, color = levelColor(level))
            }
            gap(6f)
        }

        private fun levelColor(level: ReadingLevel) = when (level) {
            ReadingLevel.NORMAL, ReadingLevel.NONE -> ink
            ReadingLevel.A_BIT_HIGH, ReadingLevel.A_BIT_LOW -> amber
            else -> red
        }

        private fun chart(type: MeasureType, points: List<Reading>) {
            val h = 90f
            room(h + 10f)
            val values = points.map { it.value } + points.mapNotNull { it.value2 }
            var lo = values.min()
            var hi = values.max()
            if (hi - lo < 4) { lo -= 2; hi += 2 }
            val pad = (hi - lo) * 0.1
            lo -= pad; hi += pad
            val left = margin + 34f
            val right = width - margin
            fun px(i: Int) = if (points.size == 1) (left + right) / 2 else left + (right - left) * i / (points.size - 1)
            fun py(v: Double) = (y + h - (v - lo) / (hi - lo) * h).toFloat()
            val grid = Paint().apply { color = rule; strokeWidth = 0.6f }
            val label = paint(8f, sub, false)
            listOf(lo, (lo + hi) / 2, hi).forEach { v ->
                canvas.drawLine(left, py(v), right, py(v), grid)
                canvas.drawText(CareText.decimal(ctx, Math.round(v * 10) / 10.0), margin, py(v) + 3f, label)
            }
            fun series(vs: List<Double?>, color: Int) {
                val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = 1.6f; style = Paint.Style.STROKE }
                val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
                val path = Path()
                var started = false
                vs.forEachIndexed { i, v ->
                    if (v == null) return@forEachIndexed
                    if (!started) { path.moveTo(px(i), py(v)); started = true } else path.lineTo(px(i), py(v))
                    canvas.drawCircle(px(i), py(v), 2.2f, dot)
                }
                canvas.drawPath(path, line)
            }
            series(points.map { it.value }, Color.rgb(225, 29, 72))
            if (type == MeasureType.BLOOD_PRESSURE) series(points.map { it.value2 }, Color.rgb(2, 132, 199))
            y += h + 10f
        }
    }
}
