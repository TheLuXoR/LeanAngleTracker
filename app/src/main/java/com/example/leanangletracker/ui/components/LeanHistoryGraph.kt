package com.example.leanangletracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.motionEventSpy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.leanangletracker.R
import com.example.leanangletracker.ui.theme.*
import kotlin.math.abs

@Composable
internal fun LeanHistoryGraph(
    values: List<Float>,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    visibleRangePoints: Int? = null // If null, show full track. If set, center on selectedIndex
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Determine the subset of points to display
            val displayValues = remember(values, selectedIndex, visibleRangePoints) {
                if (visibleRangePoints == null || selectedIndex == null || values.size <= visibleRangePoints) {
                    values
                } else {
                    val halfRange = visibleRangePoints / 2
                    val start = (selectedIndex - halfRange).coerceIn(0, (values.size - visibleRangePoints).coerceAtLeast(0))
                    val end = (start + visibleRangePoints).coerceAtMost(values.size)
                    values.subList(start, end)
                }
            }

            val lowerBound = displayValues.maxOrNull()?.coerceAtLeast(0f) ?: 0f
            val upperBound = displayValues.minOrNull()?.coerceAtMost(-0f) ?: -0f
            val amplitude = maxOf(20f, abs(upperBound), abs(lowerBound))

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
                .weight(1f)) {
                val width = size.width
                val height = size.height
                val centerY = height / 2f

                fun yFor(deg: Float): Float = centerY + (deg / amplitude) * (height * 0.45f)

                val stepX = if (displayValues.size >= 2) width / (displayValues.size - 1) else 0f

                // Grid Lines
                // Upper bound line: from right border back to the point of highest amplitude (min value)
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

                drawLine(Color(0xCCFFFFFF).copy(0.2f), Offset(0f, centerY), Offset(width, centerY), 25f)
                drawLine(Color(0xCCFFFFFF), Offset(0f, centerY), Offset(width, centerY), 1f)

                // Lower bound line: from right border back to the point of highest amplitude (max value)
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
                        val y = yFor(value.coerceIn(-amplitude, amplitude))
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        brush = Brush.verticalGradient(listOf(Color.Red,PrimaryOrange,AccentGreen ,PrimaryOrange,Color.Red)),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // The selected index relative to the displayed subset
                    val relativeSelectedIndex = if (visibleRangePoints != null && selectedIndex != null) {
                        val halfRange = visibleRangePoints / 2
                        val start = (selectedIndex - halfRange).coerceIn(0, (values.size - visibleRangePoints).coerceAtLeast(0))
                        selectedIndex - start
                    } else {
                        selectedIndex
                    }

                    if (relativeSelectedIndex != null && relativeSelectedIndex in displayValues.indices) {
                        val selX = relativeSelectedIndex * stepX
                        val selY = yFor(displayValues[relativeSelectedIndex])

                        drawLine(color = Color.White.copy(alpha = 0.4f), start = Offset(selX, 0f), end = Offset(selX, height), strokeWidth = 1.dp.toPx())
                        drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(selX, selY))
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
    }
}
