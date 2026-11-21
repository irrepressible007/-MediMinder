package com.example.mediminder.ui.health

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.mediminder.data.local.entity.Reading
import com.example.mediminder.domain.MeasureType
import com.example.mediminder.ui.theme.Hue
import com.example.mediminder.ui.theme.color
import com.example.mediminder.ui.theme.soft
import com.example.mediminder.util.CareText
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** The usual adult range, shaded behind the line so a high or low reading stands out. */
internal fun usualRange(type: MeasureType): ClosedFloatingPointRange<Double>? = when (type) {
    MeasureType.BLOOD_PRESSURE -> 90.0..130.0
    MeasureType.BLOOD_SUGAR -> 3.9..7.8
    MeasureType.TEMPERATURE -> 95.0..99.5
    MeasureType.OXYGEN -> 95.0..100.0
    MeasureType.WEIGHT -> null
}

/** The smallest span the chart shows, so a steady line is not stretched into big zig-zags. */
private fun minSpan(type: MeasureType) = when (type) {
    MeasureType.BLOOD_PRESSURE -> 40.0
    MeasureType.BLOOD_SUGAR -> 4.0
    MeasureType.WEIGHT -> 4.0
    MeasureType.TEMPERATURE -> 4.0
    MeasureType.OXYGEN -> 8.0
}

/**
 * Readings over time, oldest on the left. Blood pressure draws two lines (top and bottom
 * numbers). [readings] may come newest first; only the last [limit] are drawn.
 */
@Composable
fun ReadingChart(
    type: MeasureType,
    readings: List<Reading>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    limit: Int = 30,
    axis: Boolean = true,
) {
    val context = LocalContext.current
    val points = remember(readings, limit) { readings.sortedBy { it.takenAt }.takeLast(limit) }
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val band = Hue.GREEN.soft
    val main = Hue.ROSE.color
    val second = Hue.SKY.color
    val surface = MaterialTheme.colorScheme.surface
    val description = points.lastOrNull()?.let { CareText.value(context, it) }.orEmpty()

    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        if (points.isEmpty()) return@Canvas
        val values = points.map { it.value } + points.mapNotNull { if (type == MeasureType.BLOOD_PRESSURE) it.value2 else null }
        var lo = values.min()
        var hi = values.max()
        usualRange(type)?.let { r -> if (type != MeasureType.BLOOD_PRESSURE) { lo = min(lo, r.start); hi = max(hi, r.endInclusive) } }
        if (hi - lo < minSpan(type)) {
            val mid = (hi + lo) / 2
            lo = mid - minSpan(type) / 2
            hi = mid + minSpan(type) / 2
        }
        val step = niceStep((hi - lo) / 3)
        lo = floor(lo / step) * step
        hi = ceil(hi / step) * step

        val left = if (axis) 40.dp.toPx() else 4.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        fun y(v: Double) = (bottom - (v - lo) / (hi - lo) * (bottom - top)).toFloat()
        fun x(i: Int) = if (points.size == 1) (left + right) / 2 else left + (right - left) * i / (points.size - 1)

        // the usual range, then light grid lines with their numbers
        usualRange(type)?.let { r ->
            val y1 = y(min(r.endInclusive, hi)).coerceIn(top, bottom)
            val y2 = y(max(r.start, lo)).coerceIn(top, bottom)
            if (y2 > y1) drawRect(band, topLeft = Offset(left, y1), size = Size(right - left, y2 - y1))
        }
        var tick = lo
        while (tick <= hi + step / 2) {
            val ty = y(tick)
            drawLine(grid, Offset(left, ty), Offset(right, ty), strokeWidth = 1.dp.toPx())
            if (axis) {
                val text = measurer.measure(CareText.decimal(context, tick), labelStyle)
                drawText(text, topLeft = Offset(0f, ty - text.size.height / 2f))
            }
            tick += step
        }

        fun line(series: List<Double?>, color: Color) {
            val path = Path()
            var started = false
            series.forEachIndexed { i, v ->
                if (v == null) return@forEachIndexed
                if (!started) { path.moveTo(x(i), y(v)); started = true } else path.lineTo(x(i), y(v))
            }
            drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            series.forEachIndexed { i, v ->
                if (v == null) return@forEachIndexed
                val last = i == series.lastIndex
                drawCircle(surface, radius = (if (last) 6.dp else 4.5.dp).toPx(), center = Offset(x(i), y(v)))
                drawCircle(color, radius = (if (last) 4.5.dp else 3.dp).toPx(), center = Offset(x(i), y(v)))
            }
        }
        line(points.map { it.value }, main)
        if (type == MeasureType.BLOOD_PRESSURE) line(points.map { it.value2 }, second)
    }
}

/** 1, 2, 5, 10, 20, 50… whichever is closest above [raw]. */
private fun niceStep(raw: Double): Double {
    if (raw <= 0) return 1.0
    var magnitude = 1.0
    while (magnitude * 10 <= raw) magnitude *= 10
    while (magnitude > raw) magnitude /= 10
    return listOf(1.0, 2.0, 5.0, 10.0).map { it * magnitude }.first { it >= raw }
}
