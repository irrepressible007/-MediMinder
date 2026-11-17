package com.example.mediminder.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A dose on the day dial. */
data class WheelItem(
    val key: String,
    val minuteOfDay: Int,
    val color: Color,
)

/**
 * The day as a 24-hour dial (midnight at the top). Each dose is a dot at its time, coloured
 * by status; the arc shows how much of the day has passed. [center] goes in the middle.
 */
@Composable
fun TimelineWheel(
    items: List<WheelItem>,
    nowMinute: Int?,
    modifier: Modifier = Modifier,
    ringWidth: Dp = 14.dp,
    center: @Composable () -> Unit,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val progress = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val tick = MaterialTheme.colorScheme.outline
    val dotOutline = MaterialTheme.colorScheme.surface
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val stroke = ringWidth.toPx()
            val radius = size.minDimension / 2 - stroke
            val c = Offset(size.width / 2, size.height / 2)
            drawCircle(track, radius, c, style = Stroke(stroke))
            if (nowMinute != null) {
                drawArc(
                    progress, startAngle = -90f, sweepAngle = nowMinute / 1440f * 360f, useCenter = false,
                    topLeft = Offset(c.x - radius, c.y - radius), size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            // Ticks every 3 hours
            for (h in 0 until 24 step 3) {
                val a = h / 24.0 * 2 * PI - PI / 2
                val inner = radius - stroke * 1.4f
                val outer = radius - stroke * 0.9f
                drawLine(tick, Offset(c.x + inner * cos(a).toFloat(), c.y + inner * sin(a).toFloat()), Offset(c.x + outer * cos(a).toFloat(), c.y + outer * sin(a).toFloat()), strokeWidth = 2f)
            }
            items.forEach { item ->
                val a = item.minuteOfDay / 1440.0 * 2 * PI - PI / 2
                val p = Offset(c.x + radius * cos(a).toFloat(), c.y + radius * sin(a).toFloat())
                drawCircle(dotOutline, stroke * 0.95f, p)
                drawCircle(item.color, stroke * 0.7f, p)
            }
            if (nowMinute != null) {
                val a = nowMinute / 1440.0 * 2 * PI - PI / 2
                val p = Offset(c.x + radius * cos(a).toFloat(), c.y + radius * sin(a).toFloat())
                drawCircle(Color.White, stroke * 0.35f, p)
            }
        }
        center()
    }
}
