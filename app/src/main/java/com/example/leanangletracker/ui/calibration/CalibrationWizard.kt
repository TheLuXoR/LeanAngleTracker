package com.example.leanangletracker.ui.calibration

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.leanangletracker.CalibrationStep
import com.example.leanangletracker.R
import com.example.leanangletracker.UiState
import com.example.leanangletracker.ui.components.InfoChip
import com.example.leanangletracker.ui.components.PrimaryActionButton
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun CalibrationWizard(
    state: UiState,
    onCaptureUpright: () -> Unit,
    onStartLeftMeasurement: () -> Unit,
    onStartRightMeasurement: () -> Unit
) {
    var showInfo by remember { mutableStateOf(false) }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = { Button(onClick = { showInfo = false }) { Text(stringResource(R.string.dialog_ok)) } },
            title = { Text(stringResource(R.string.calibration_title)) },
            text = { Text(stringResource(R.string.calibration_info_text)) }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.calibration_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    InfoChip { showInfo = true }
                }

                BikeTiltAnimation(step = state.calibrationStep, modifier = Modifier.fillMaxWidth().height(130.dp))

                Text(text = stringResource(state.instructionsResId), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

                CalibrationAmplitudeBars(
                    leftAmp = state.leftCalibrationAmplitudeDeg,
                    rightAmp = state.rightCalibrationAmplitudeDeg,
                    currentAmp = state.currentStepAmplitudeDeg
                )

                Text(text = stringResource(R.string.warning_only_zero_position), color = Color(0xFFC05757), fontSize = 15.sp)
                state.qualityHintResId?.let { hintResId ->
                    Text(text = stringResource(hintResId), color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
                }

                when (state.calibrationStep) {
                    CalibrationStep.UPRIGHT -> PrimaryActionButton(stringResource(R.string.action_capture_upright), onCaptureUpright)
                    CalibrationStep.LEFT_READY -> PrimaryActionButton(stringResource(R.string.action_start_left_measurement), onStartLeftMeasurement)
                    CalibrationStep.RIGHT_READY -> PrimaryActionButton(stringResource(R.string.action_start_right_measurement), onStartRightMeasurement)
                    CalibrationStep.LEFT_MEASURING,
                    CalibrationStep.RIGHT_MEASURING -> Text(stringResource(R.string.measurement_running), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    CalibrationStep.READY -> Unit
                }
            }
        }
    }
}

@Composable
private fun BikeTiltAnimation(step: CalibrationStep, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "bike-tilt")
    val phase by infinite.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1700, easing = LinearEasing), RepeatMode.Reverse),
        label = "phase"
    )

    val tilt = when (step) {
        CalibrationStep.LEFT_READY,
        CalibrationStep.LEFT_MEASURING -> -22f

        CalibrationStep.RIGHT_READY,
        CalibrationStep.RIGHT_MEASURING -> 22f
        else -> 0f
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val baseY = h * 0.78f
        drawLine(Color(0xFF8392A5), Offset(0f, baseY), Offset(w, baseY), strokeWidth = 6f)

        val dynamicTilt = tilt * (0.7f + 0.3f * abs(phase))
        val radians = Math.toRadians(dynamicTilt.toDouble())
        val cx = w * 0.5f
        val cy = h * 0.56f
        val len = 84f
        val dx = (len * sin(radians)).toFloat()
        val dy = (len * cos(radians)).toFloat()

        drawLine(Color(0xFF2F3E52), Offset(cx - dx, cy + dy * 0.35f), Offset(cx + dx, cy - dy), strokeWidth = 10f, cap = StrokeCap.Round)
        drawCircle(Color(0xFF2F3E52), radius = 16f, center = Offset(cx - dx * 0.85f, cy + dy * 0.30f), style = Stroke(5f))
        drawCircle(Color(0xFF2F3E52), radius = 16f, center = Offset(cx + dx * 0.85f, cy - dy * 0.85f), style = Stroke(5f))
    }
}

@Composable
private fun CalibrationAmplitudeBars(leftAmp: Float, rightAmp: Float, currentAmp: Float) {
    val maxAmp = maxOf(20f, leftAmp, rightAmp, currentAmp)

    fun barWidth(value: Float): Float = (value / maxAmp).coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AmpRow(label = stringResource(R.string.label_left_short), value = leftAmp, widthFraction = barWidth(leftAmp), color = Color(0xFF4EA3FF))
        AmpRow(label = stringResource(R.string.label_right_short), value = rightAmp, widthFraction = barWidth(rightAmp), color = Color(0xFF4EA3FF))
        AmpRow(label = stringResource(R.string.label_now_short), value = currentAmp, widthFraction = barWidth(currentAmp), color = Color(0xFFFF7B7B))
    }
}

@Composable
private fun AmpRow(label: String, value: Float, widthFraction: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(36.dp), fontWeight = FontWeight.Bold)
        Box(modifier = Modifier.weight(1f).height(16.dp).background(Color(0xFFE6ECF3), RoundedCornerShape(6.dp))) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(widthFraction)
                    .height(16.dp)
                    .background(color, RoundedCornerShape(6.dp))
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.value_degrees_one_decimal, value), fontSize = 13.sp)
    }
}
