package com.example.mediminder.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

data class WheelItem(
    val id: Long,
    val medicationId: Long,
    val timeOfDayMillis: Long, // milliseconds since midnight
    val label: String,
    val color: Color
)

@Composable
fun TimelineWheel(
    items: List<WheelItem>,
    modifier: Modifier = Modifier,
    onItemClick: (WheelItem) -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val ringColor = MaterialTheme.colorScheme.surfaceVariant

        Canvas(modifier = Modifier.fillMaxSize().padding(48.dp)) {
            val radius = size.minDimension / 2
            drawCircle(
                color = ringColor,
                radius = radius,
                style = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Round)
            )
            // Could add segments for morning/afternoon here later
        }

        // Layout items around the ring
        WheelLayout(items = items, radiusDp = 130.dp) { item ->
            var isExpanded by remember { mutableStateOf(false) }
            val scale by animateFloatAsState(
                targetValue = if (isExpanded) 1.5f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "icon_scale"
            )
            
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.scale(scale)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(item.color)
                        .clickable { 
                            isExpanded = !isExpanded
                            onItemClick(item) 
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.label.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                Spacer(modifier = Modifier.padding(top = 4.dp))
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun WheelLayout(
    items: List<WheelItem>,
    radiusDp: Dp,
    modifier: Modifier = Modifier,
    content: @Composable (WheelItem) -> Unit
) {
    Layout(
        modifier = modifier.fillMaxSize(),
        content = {
            items.forEach { item ->
                content(item)
            }
        }
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            val centerX = constraints.maxWidth / 2
            val centerY = constraints.maxHeight / 2
            val radius = radiusDp.roundToPx()

            placeables.forEachIndexed { index, placeable ->
                val item = items[index]
                
                // Calculate angle based on time (24 hours = 360 degrees)
                val millisInDay = 24 * 60 * 60 * 1000f
                val fraction = item.timeOfDayMillis / millisInDay
                val angleRad = (fraction * 2 * Math.PI) - (Math.PI / 2)

                val x = centerX + (radius * cos(angleRad)).toInt() - placeable.width / 2
                val y = centerY + (radius * sin(angleRad)).toInt() - placeable.height / 2

                placeable.placeRelative(x = x, y = y)
            }
        }
    }
}
