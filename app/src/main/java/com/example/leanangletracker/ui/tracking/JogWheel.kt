package com.example.leanangletracker.ui.tracking

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun JogWheel(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val scrollOffset = remember { Animatable(value.toFloat()) }

    // Sync external value changes to internal offset (e.g. when session changes)
    LaunchedEffect(value) {
        if (abs(scrollOffset.value - value) > 0.5f) {
            scrollOffset.snapTo(value.toFloat())
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    val sensitivity = 0.2f // Adjust for feel
                    val newOffset = (scrollOffset.value - delta * sensitivity)
                        .coerceIn(range.first.toFloat() - 0.5f, range.last.toFloat() + 0.5f)
                    
                    scope.launch {
                        scrollOffset.snapTo(newOffset)
                        onValueChange(newOffset.roundToInt().coerceIn(range))
                    }
                },
                onDragStopped = { velocity ->
                    val decay = exponentialDecay<Float>(frictionMultiplier = 2f)
                    scope.launch {
                        scrollOffset.animateDecay(-velocity * 0.05f, decay) {
                            if (scrollOffset.value !in range.first.toFloat()..range.last.toFloat()) {
                                // bounce back logic if needed, but coerceIn is simpler
                            }
                            onValueChange(scrollOffset.value.roundToInt().coerceIn(range))
                        }
                    }
                }
            )
    ) {
        val w = size.width
        val h = size.height
        val centerX = w / 2f
        val tickSpacing = 15.dp.toPx()
        
        // Draw background subtle track
        drawLine(
            color = onSurface.copy(alpha = 0.05f),
            start = Offset(0f, h / 2f),
            end = Offset(w, h / 2f),
            strokeWidth = 2.dp.toPx()
        )

        // Draw ticks
        val startTick = (scrollOffset.value - (w / 2f) / tickSpacing).toInt() - 1
        val endTick = (scrollOffset.value + (w / 2f) / tickSpacing).toInt() + 1
        
        for (i in startTick..endTick) {
            val x = centerX + (i - scrollOffset.value) * tickSpacing
            if (x < 0 || x > w) continue

            val distanceToCenter = abs(x - centerX)
            val normalizedDistance = (distanceToCenter / (w / 2f)).coerceIn(0f, 1f)
            val alpha = (1f - normalizedDistance) * 0.8f
            
            // End of track visual feedback
            val isOutOfRange = i !in range
            val tickColor = if (isOutOfRange) Color.Red.copy(alpha = alpha * 0.5f) else onSurface.copy(alpha = alpha)
            val tickHeight = if (i % 10 == 0) 30.dp.toPx() else if (i % 5 == 0) 20.dp.toPx() else 12.dp.toPx()
            val strokeWidth = if (i % 10 == 0) 3.dp.toPx() else 1.5.dp.toPx()

            drawLine(
                color = tickColor,
                start = Offset(x, (h - tickHeight) / 2f),
                end = Offset(x, (h + tickHeight) / 2f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }

        // Center Indicator
        val indicatorHeight = 40.dp.toPx()
        drawLine(
            brush = Brush.verticalGradient(listOf(primaryColor.copy(alpha = 0f), primaryColor, primaryColor.copy(alpha = 0f))),
            start = Offset(centerX, (h - indicatorHeight) / 2f),
            end = Offset(centerX, (h + indicatorHeight) / 2f),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}
