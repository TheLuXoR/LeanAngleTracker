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
import androidx.compose.ui.draw.alpha
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
    visibleRangePoints: Int? = null, // If null, show full track. If set, center on selectedIndex
    isScrollable: Boolean = false,
    showCursorLine: Boolean = true,
    onSelectedIndexChange: ((Int) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    // Internal animatable to hold the scroll position, initialized to the current selection
    val scrollOffset = remember { Animatable(selectedIndex?.toFloat() ?: 0f) }
    
    val minBound = 0f
    val maxBound = values.lastIndex.toFloat().coerceAtLeast(0f)
    val overscrollLimit = 2.5f // Matching JogWheel behavior

    var showScrollHint by remember { mutableStateOf(false) }

    // Use a stable amplitude to prevent vertical "flickering" when peaks enter/leave the window.
    // We increase the minimum to 45 degrees so typical street lean doesn't hit the red gradient zones too early.
    val targetMax = remember(values) { values.maxOfOrNull { abs(it) } ?: 0f }
    val animatedAmplitude by animateFloatAsState(
        targetValue = maxOf(45f, targetMax),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "amplitude"
    )

    // Sync internal offset when external selectedIndex changes (e.g. from Map or external update)
    LaunchedEffect(selectedIndex) {
        if (selectedIndex != null && abs(scrollOffset.value - selectedIndex) > 0.5f && !scrollOffset.isRunning) {
            scrollOffset.snapTo(selectedIndex.toFloat())
        }
    }

    // Show hint if scrollable and not recently interacted with
    LaunchedEffect(isScrollable) {
        if (isScrollable) {
            delay(2000)
            showScrollHint = true
            delay(4000)
            showScrollHint = false
        }
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box {
            Column(modifier = Modifier.padding(16.dp)) {
                // Determine the window of points to show based on the animated scrollOffset
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

                // Current peak indicators for text display
                val lowerBound = displayValues.maxOrNull()?.coerceAtLeast(0f) ?: 0f
                val upperBound = displayValues.minOrNull()?.coerceAtMost(-0f) ?: -0f

                Row {
                    Text(
                        stringResource(R.string.history_title).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        stringResource(R.string.history_max_left, -upperBound),
                        modifier = Modifier.padding(end = 8.dp, top = 2.dp),
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
                                    showScrollHint = false // Hide hint on interaction
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

                    // Grid Lines (Current peaks in window)
                    displayValues.minOrNull()?.let { minVal ->
                        val minIndex = displayValues.indexOf(minVal)
                        val startX = minIndex * stepX
                        drawLine(
                            color = PrimaryOrange,
                            start = Offset(startX, yFor(upperBound)),
                            end = Offset(width, yFor(upperBound)),
                            strokeWidth = 2f,
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                        )
                    }

                    // Overscroll visual feedback (Red tint at edges like JogWheel)
                    // Increased deadzone and alpha gating to prevent "flickering" reds on edge snaps
                    if (scrollOffset.value < minBound - 0.05f) {
                        val alpha = ((abs(scrollOffset.value - minBound) - 0.05f) / overscrollLimit).coerceIn(0f, 0.2f)
                        if (alpha > 0.01f) drawRect(color = Color.Red.copy(alpha = alpha), size = size)
                    } else if (scrollOffset.value > maxBound + 0.05f) {
                        val alpha = ((abs(scrollOffset.value - maxBound) - 0.05f) / overscrollLimit).coerceIn(0f, 0.2f)
                        if (alpha > 0.01f) drawRect(color = Color.Red.copy(alpha = alpha), size = size)
                    }

                    // Zero line
                    drawLine(Color(0xCCFFFFFF).copy(0.2f), Offset(0f, centerY), Offset(width, centerY), 25f)
                    drawLine(Color(0xCCFFFFFF), Offset(0f, centerY), Offset(width, centerY), 1f)

                    displayValues.maxOrNull()?.let { maxVal ->
                        val maxIndex = displayValues.indexOf(maxVal)
                        val startX = maxIndex * stepX
                        drawLine(
                            color = PrimaryOrange,
                            start = Offset(startX, yFor(lowerBound)),
                            end = Offset(width, yFor(lowerBound)),
                            strokeWidth = 2f,
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                        )
                    }

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

                        // Cursor Position Calculation
                        // To avoid horizontal "flickering" during live updates, we prefer using the prop selectedIndex
                        // for X position when not actively scrolling/dragging.
                        val isAtEnd = selectedIndex == values.lastIndex
                        val isNotScrolling = !scrollOffset.isRunning && !isScrollable
                        
                        val cursorOffset = if (isNotScrolling && isAtEnd) {
                             values.lastIndex.toFloat()
                        } else {
                            scrollOffset.value
                        }

                        val relativeCursorOffset = cursorOffset - displayStartIndex
                        
                        if (relativeCursorOffset in -0.5f..(displayValues.size.toFloat() - 0.5f)) {
                            val selX = (relativeCursorOffset * stepX).coerceIn(0f, width)
                            
                            // Interpolate Y for smooth cursor movement
                            val clampedScrollVal = cursorOffset.coerceIn(0f, values.lastIndex.toFloat())
                            val currentIdx = clampedScrollVal.toInt().coerceIn(0, values.lastIndex)
                            val nextIdx = (currentIdx + 1).coerceIn(0, values.lastIndex)
                            val fraction = clampedScrollVal - currentIdx
                            
                            val interpolatedValue = if (currentIdx == nextIdx) {
                                values[currentIdx]
                            } else {
                                values[currentIdx] * (1 - fraction) + values[nextIdx] * fraction
                            }
                            
                            val selY = yFor(interpolatedValue)

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
                Text(
                    stringResource(R.string.history_max_right, lowerBound),
                    modifier = Modifier.padding(end = 8.dp).align(Alignment.End),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Scroll Hint Overlay
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
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
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
