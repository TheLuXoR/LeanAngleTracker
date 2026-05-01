package com.example.leanangletracker.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.leanangletracker.R
import com.example.leanangletracker.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun LeanHistoryGraph(
    values: List<Float>,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    visibleRangePoints: Int? = null,
    isScrollable: Boolean = false,
    showCursorLine: Boolean = true,
    onSelectedIndexChange: ((Int) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val scrollOffset = remember { Animatable(selectedIndex?.toFloat() ?: 0f) }
    
    val minBound = 0f
    val maxBound = values.lastIndex.toFloat().coerceAtLeast(0f)
    val overscrollLimit = 2.5f

    var showScrollHint by remember { mutableStateOf(false) }

    val globalMax = remember(values) { values.maxOfOrNull { abs(it) } ?: 0f }
    val animatedAmplitude by animateFloatAsState(
        targetValue = maxOf(45f, globalMax),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "amplitude"
    )

    LaunchedEffect(selectedIndex) {
        if (selectedIndex != null && abs(scrollOffset.value - selectedIndex) > 0.5f && !scrollOffset.isRunning) {
            scrollOffset.snapTo(selectedIndex.toFloat())
        }
    }

    LaunchedEffect(isScrollable) {
        if (isScrollable) {
            delay(2000)
            showScrollHint = true
            delay(4000)
            showScrollHint = false
        }
    }

    // Interpolate current lean for the header
    val currentLean = remember(values, scrollOffset.value) {
        val clampedIdx = scrollOffset.value.coerceIn(minBound, maxBound)
        val idx = clampedIdx.toInt().coerceIn(0, values.lastIndex)
        val nextIdx = (idx + 1).coerceIn(0, values.lastIndex)
        val fraction = clampedIdx - idx
        if (idx == nextIdx) values[idx] else values[idx] * (1 - fraction) + values[nextIdx] * fraction
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box {
            Column(modifier = Modifier.padding(16.dp)) {
                val currentScrollVal = scrollOffset.value
                val displayStartIndex = remember(values.size, currentScrollVal, visibleRangePoints) {
                    if (visibleRangePoints == null || values.size <= visibleRangePoints) {
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.history_title).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${abs(currentLean).roundToInt()}° ${if (currentLean < 0) "LEFT" else if (currentLean > 0) "RIGHT" else ""}",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Canvas(modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .then(
                        if (isScrollable && onSelectedIndexChange != null && selectedIndex != null) {
                            Modifier.draggable(
                                orientation = Orientation.Horizontal,
                                state = rememberDraggableState { delta ->
                                    showScrollHint = false
                                    val sensitivity = 0.2f 
                                    var effectiveDelta = -delta * sensitivity

                                    val current = scrollOffset.value
                                    if (current < minBound && effectiveDelta < 0) {
                                        val resistance = (1f - (abs(minBound - current) / overscrollLimit)).coerceIn(0.1f, 1f)
                                        effectiveDelta *= resistance
                                    } else if (current > maxBound && effectiveDelta > 0) {
                                        val resistance = (1f - (abs(current - maxBound) / overscrollLimit)).coerceIn(0.1f, 1f)
                                        effectiveDelta *= resistance
                                    }

                                    val newOffset = (current + effectiveDelta)
                                        .coerceIn(minBound - overscrollLimit, maxBound + overscrollLimit)
                                    
                                    scope.launch {
                                        scrollOffset.snapTo(newOffset)
                                        onSelectedIndexChange(newOffset.roundToInt().coerceIn(0, values.lastIndex))
                                    }
                                },
                                onDragStopped = { velocity ->
                                    scope.launch {
                                        if (scrollOffset.value < minBound || scrollOffset.value > maxBound) {
                                            scrollOffset.animateTo(
                                                targetValue = if (scrollOffset.value < minBound) minBound else maxBound,
                                                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
                                            )
                                        } else {
                                            val decay = exponentialDecay<Float>(frictionMultiplier = 2f)
                                            scrollOffset.animateDecay(-velocity * 0.05f, decay) {
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
                ) {
                    val width = size.width
                    val height = size.height
                    val centerY = height / 2f

                    fun yFor(deg: Float): Float = centerY + (deg / animatedAmplitude) * (height * 0.45f)

                    val stepX = if (displayValues.size >= 2) width / (displayValues.size - 1) else 0f

                    // Overscroll feedback with deadzone
                    if (scrollOffset.value < minBound - 0.05f) {
                        val alpha = ((abs(scrollOffset.value - minBound) - 0.05f) / overscrollLimit).coerceIn(0f, 0.2f)
                        if (alpha > 0.01f) drawRect(color = Color.Red.copy(alpha = alpha), size = size)
                    } else if (scrollOffset.value > maxBound + 0.05f) {
                        val alpha = ((abs(scrollOffset.value - maxBound) - 0.05f) / overscrollLimit).coerceIn(0f, 0.2f)
                        if (alpha > 0.01f) drawRect(color = Color.Red.copy(alpha = alpha), size = size)
                    }

                    // Grid lines
                    drawLine(Color(0xCCFFFFFF).copy(0.2f), Offset(0f, centerY), Offset(width, centerY), 25f)
                    drawLine(Color(0xCCFFFFFF), Offset(0f, centerY), Offset(width, centerY), 1f)

                    if (displayValues.size >= 2) {
                        val path = Path()
                        displayValues.forEachIndexed { index, value ->
                            val x = index * stepX
                            val y = yFor(value.coerceIn(-animatedAmplitude, animatedAmplitude))
                            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(
                            path = path,
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Red, PrimaryOrange, AccentGreen, PrimaryOrange, Color.Red),
                                startY = 0f,
                                endY = height
                            ),
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )

                        val relativeCursorOffset = scrollOffset.value - displayStartIndex
                        if (relativeCursorOffset in -0.5f..(displayValues.size.toFloat() - 0.5f)) {
                            val selX = (relativeCursorOffset * stepX).coerceIn(0f, width)
                            val selY = yFor(currentLean.coerceIn(-animatedAmplitude, animatedAmplitude))

                            if (showCursorLine) {
                                drawLine(
                                    color = Color.White.copy(alpha = 0.4f),
                                    start = Offset(selX, 0f),
                                    end = Offset(selX, height),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                            drawCircle(
                                color = Color.White,
                                radius = 4.dp.toPx(),
                                center = Offset(selX, selY)
                            )
                        }
                    }
                }
            }

            if (showScrollHint) {
                val infiniteTransition = rememberInfiniteTransition(label = "hint")
                val offsetX by infiniteTransition.animateFloat(
                    initialValue = -20f,
                    targetValue = 20f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "hint_offset"
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.offset(x = offsetX.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.TouchApp, null, tint = Color.White, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Text(
                            text = "Scroll graph to review",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
