package com.example.leanangletracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.leanangletracker.ui.theme.AccentGreen
import com.example.leanangletracker.ui.theme.GaugeBackground
import com.example.leanangletracker.ui.theme.GaugeNeedle
import com.example.leanangletracker.ui.theme.GaugeScale
import com.example.leanangletracker.ui.theme.PrimaryOrange
import com.example.leanangletracker.ui.theme.SecondaryBlue
import com.example.leanangletracker.ui.theme.TextPrimary
import com.example.leanangletracker.ui.theme.TextSecondary
import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin


@Preview(widthDp = 400, heightDp = 200)
@Composable
private fun TachoGaugePreview() {
    TachoGauge(
        currentDeg = 10f,
        maxLeftDeg = 20f,
        maxRightDeg = 21f,
        modifier = Modifier
    )
}

@Composable
public fun TachoGauge(
    currentDeg: Float,
    maxLeftDeg: Float,
    maxRightDeg: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box (Modifier.fillMaxSize()
            .padding(10.dp,20.dp)){
            Canvas(modifier = Modifier.fillMaxSize())
            {
                val center = Offset(size.width / 2f, size.height)
                val radius = min(size.width / 2f, size.height)
                val maxDisplay = 65f

                // Gauge Background
                drawArc(
                    color = GaugeBackground,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = androidx.compose.ui.geometry.Offset(
                        center.x - radius,
                        center.y - radius
                    ),
                    size = Size(radius * 2, radius * 2)
                )
                // Border
                drawArc(
                    color = Color.Blue.copy(0.3f),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = 4.dp.toPx())
                )

                // Scale marks
                for (mark in -50..50 step 10) {
                    val theta = Math.toRadians(mark.toDouble() - 90.0)
                    val isMajor = mark % 30 == 0
                    val outer = Offset(
                        x = center.x + (radius * 0.95f * cos(theta)).toFloat(),
                        y = center.y + (radius * 0.95f * sin(theta)).toFloat()
                    )
                    val innerFactor = if (isMajor) 0.82f else 0.88f
                    val inner = Offset(
                        x = center.x + (radius * innerFactor * cos(theta)).toFloat(),
                        y = center.y + (radius * innerFactor * sin(theta)).toFloat()
                    )
                    drawLine(
                        color = if (mark == 0) AccentGreen else GaugeScale.copy(alpha = 0.6f),
                        start = inner,
                        end = outer,
                        strokeWidth = if (isMajor) 4.dp.toPx() else 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                fun angleToTip(deg: Float, lengthFactor: Float): Offset {
                    val clamped = deg.coerceIn(-maxDisplay, maxDisplay)
                    val theta = Math.toRadians(clamped.toDouble() - 90.0)
                    return Offset(
                        x = center.x + (radius * lengthFactor * cos(theta)).toFloat(),
                        y = center.y + (radius * lengthFactor * sin(theta)).toFloat()
                    )
                }

                // Max indications
                drawLine(
                    SecondaryBlue.copy(alpha = 0.4f),
                    center,
                    angleToTip(maxLeftDeg, 0.9f),
                    4.dp.toPx(),
                    StrokeCap.Round
                )
                drawLine(
                    SecondaryBlue.copy(alpha = 0.4f),
                    center,
                    angleToTip(maxRightDeg, 0.9f),
                    4.dp.toPx(),
                    StrokeCap.Round
                )

                // Current needle
                drawLine(
                    brush = Brush.linearGradient(listOf(GaugeNeedle, PrimaryOrange)),
                    start = center,
                    end = angleToTip(currentDeg, 0.88f),
                    strokeWidth = 8.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Center hub
                drawCircle(color = Color.White, radius = 12.dp.toPx(), center = center)
                drawCircle(color = GaugeNeedle, radius = 6.dp.toPx(), center = center)
            }

            // Max Values
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(5.dp)
            ) {
                MaxValItem("MAX L", abs(maxLeftDeg))
                Spacer(Modifier.weight(1f))
                MaxValItem("MAX R", maxRightDeg)
            }
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter).offset(0.dp,12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = CircleShape
            ) {

                val df = DecimalFormat("0.0")
                Text(
                    modifier = Modifier.padding(4.dp),
                    text = df.format(Math.abs(currentDeg)),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            }
        }

    }
}

@Composable
fun MaxValItem(label: String, value: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)

        Text(
            text = "%.1f°".format(value),
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
    }
}