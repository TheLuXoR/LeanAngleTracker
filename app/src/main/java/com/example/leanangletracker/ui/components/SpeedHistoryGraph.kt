package com.example.leanangletracker.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.leanangletracker.R
import com.example.leanangletracker.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun SpeedHistoryGraph(
    values: List<Float>,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    visibleRangePoints: Int? = null,
    isScrollable: Boolean = false,
    scrollSensitivity: Float = 0.2f,
    onScrollStarted: (() -> Unit)? = null,
    onScrollFinished: (() -> Unit)? = null,
    onSelectedIndexChange: ((Int) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val scrollOffset = remember { Animatable(selectedIndex?.toFloat() ?: 0f) }
    
    val minBound = 0f
    val maxBound = values.lastIndex.toFloat().coerceAtLeast(0f)
    val overscrollLimit = 2.5f

    val globalMax = remember(values) { values.maxOfOrNull { it } ?: 0f }
    val animatedAmplitude by animateFloatAsState(
        targetValue = maxOf(45f, globalMax * 1.1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "speed_amplitude"
    )

    LaunchedEffect(selectedIndex) {
        if (selectedIndex != null && abs(scrollOffset.value - selectedIndex) > 0.5f && !scrollOffset.isRunning) {
            scrollOffset.snapTo(selectedIndex.toFloat())
        }
    }

    val currentSpeed by remember(values, scrollOffset.value) {
        derivedStateOf {
            val clampedIdx = scrollOffset.value.coerceIn(minBound, maxBound)
            val idx = clampedIdx.toInt().coerceIn(0, values.lastIndex)
            val nextIdx = (idx + 1).coerceIn(0, values.lastIndex)
            val fraction = clampedIdx - idx
            if (idx == nextIdx) values[idx] else values[idx] * (1 - fraction) + values[nextIdx] * fraction
        }
    }

    val speedColor by remember(currentSpeed) {
        derivedStateOf { getSpeedColor(currentSpeed, globalMax) }
    }

    val graphPath = remember { Path() }
    val fillPath = remember { Path() }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val currentScrollVal = scrollOffset.value
            val displayStartIndex = remember(values.size, currentScrollVal, visibleRangePoints) {
                if (visibleRangePoints == null || values.size <= (visibleRangePoints ?: 0)) {
                    0
                } else {
                    val halfRange = (visibleRangePoints ?: 0) / 2
                    (currentScrollVal.roundToInt() - halfRange).coerceIn(0, (values.size - (visibleRangePoints ?: 0)).coerceAtLeast(0))
                }
            }

            val displayValues = remember(values, displayStartIndex, visibleRangePoints) {
                if (visibleRangePoints == null || values.size <= (visibleRangePoints ?: 0)) {
                    values
                } else {
                    val end = (displayStartIndex + (visibleRangePoints ?: 0)).coerceAtMost(values.size)
                    values.subList(displayStartIndex, end)
                }
            }

            val windowMaxSpeed = remember(displayValues) { displayValues.maxOrNull() ?: 0f }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.history_speed_title).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.weight(1f))
                if (windowMaxSpeed > 1f) {
                    Text(
                        stringResource(R.string.history_max_speed, windowMaxSpeed.roundToInt()),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
                Text(
                    "${currentSpeed.roundToInt()} KM/H",
                    color = speedColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .then(
                    if (isScrollable && onSelectedIndexChange != null && selectedIndex != null) {
                        Modifier.draggable(
                            orientation = Orientation.Horizontal,
                            onDragStarted = {
                                onScrollStarted?.invoke()
                            },
                            state = rememberDraggableState { delta ->
                                var effectiveDelta = -delta * scrollSensitivity
                                val current = scrollOffset.value
                                if (current < minBound && effectiveDelta < 0) {
                                    val resistance = (1f - (abs(minBound - current) / overscrollLimit)).coerceIn(0.1f, 1f)
                                    effectiveDelta *= resistance
                                } else if (current > maxBound && effectiveDelta > 0) {
                                    val resistance = (1f - (abs(current - maxBound) / overscrollLimit)).coerceIn(0.1f, 1f)
                                    effectiveDelta *= resistance
                                }
                                val newOffset = (current + effectiveDelta).coerceIn(minBound - overscrollLimit, maxBound + overscrollLimit)
                                scope.launch {
                                    scrollOffset.snapTo(newOffset)
                                    onSelectedIndexChange(newOffset.roundToInt().coerceIn(0, values.lastIndex))
                                }
                            },
                            onDragStopped = { velocity ->
                                onScrollFinished?.invoke()
                                scope.launch {
                                    if (scrollOffset.value < minBound || scrollOffset.value > maxBound) {
                                        scrollOffset.animateTo(
                                            targetValue = if (scrollOffset.value < minBound) minBound else maxBound,
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
                                        )
                                    } else {
                                        val decay = exponentialDecay<Float>(frictionMultiplier = 2f)
                                        scrollOffset.animateDecay(-velocity * scrollSensitivity * 1.25f, decay) {
                                            if (value !in minBound..maxBound) {
                                                this@launch.launch {
                                                    scrollOffset.animateTo(
                                                        targetValue = if (value < minBound) minBound else maxBound,
                                                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
                                                    )
                                                }
                                            }
                                            onSelectedIndexChange(scrollOffset.value.roundToInt().coerceIn(0, values.lastIndex))
                                        }
                                    }
                                }
                            }
                        )
                    } else Modifier
                )
                .drawWithCache {
                    val width = size.width
                    val height = size.height
                    
                    onDrawWithContent {
                        fun yFor(speed: Float): Float = height - (speed / animatedAmplitude) * height
                        val stepX = if (displayValues.size >= 2) width / (displayValues.size - 1) else 0f

                        // 1. Static/Reference lines
                        listOf(50f, 100f, 150f, 200f).forEach { speed ->
                            if (speed < animatedAmplitude) {
                                val y = yFor(speed)
                                drawLine(Color.White.copy(alpha = 0.05f), Offset(0f, y), Offset(width, y), 1f)
                            }
                        }
                        drawLine(Color.White.copy(alpha = 0.1f), Offset(0f, height), Offset(width, height), 2f)

                        // 2. Dynamic Graph
                        if (displayValues.size >= 2) {
                            graphPath.reset()
                            displayValues.forEachIndexed { index, value ->
                                val x = index * stepX
                                val y = yFor(value.coerceIn(0f, animatedAmplitude))
                                if (index == 0) graphPath.moveTo(x, y) else graphPath.lineTo(x, y)
                            }
                            
                            fillPath.reset()
                            fillPath.addPath(graphPath)
                            fillPath.lineTo(width, height)
                            fillPath.lineTo(0f, height)
                            fillPath.close()
                            
                            val speedGradient = Brush.verticalGradient(
                                colors = listOf(Color.Red, PrimaryOrange, AccentGreen),
                                startY = yFor(globalMax),
                                endY = yFor(0f)
                            )
                            
                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Red.copy(alpha = 0.25f), PrimaryOrange.copy(alpha = 0.15f), AccentGreen.copy(alpha = 0.05f), Color.Transparent),
                                    startY = yFor(globalMax),
                                    endY = height
                                )
                            )

                            drawPath(path = graphPath, brush = speedGradient, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

                            // 3. Cursor
                            val relativeCursorOffset = scrollOffset.value - displayStartIndex
                            if (relativeCursorOffset in -0.5f..(displayValues.size.toFloat() - 0.5f)) {
                                val selX = (relativeCursorOffset * stepX).coerceIn(0f, width)
                                val selY = yFor(currentSpeed.coerceIn(0f, animatedAmplitude))
                                drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(selX, selY))
                            }
                        }
                    }
                }
            )
        }
    }
}

private fun getSpeedColor(speed: Float, maxSpeed: Float): Color {
    val ratio = (speed / maxOf(1f, maxSpeed)).coerceIn(0f, 1f)
    return if (ratio < 0.5f) {
        val innerRatio = ratio * 2f
        lerp(AccentGreen, PrimaryOrange, innerRatio)
    } else {
        val innerRatio = (ratio - 0.5f) * 2f
        lerp(PrimaryOrange, Color.Red, innerRatio)
    }
}

private fun lerp(start: Color, stop: Color, fraction: Float): Color {
    return Color(
        red = start.red + (stop.red - start.red) * fraction,
        green = start.green + (stop.green - start.green) * fraction,
        blue = start.blue + (stop.blue - start.blue) * fraction,
        alpha = start.alpha + (stop.alpha - start.alpha) * fraction
    )
}
