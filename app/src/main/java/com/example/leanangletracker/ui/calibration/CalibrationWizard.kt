package com.example.leanangletracker.ui.calibration

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.leanangletracker.CalibrationUiState
import com.example.leanangletracker.R
import com.example.leanangletracker.sensor.CalibrationTiltProgress
import com.example.leanangletracker.ui.animation.BikeLean
import com.example.leanangletracker.ui.animation.CalibrationBikeLeanAnimation
import com.example.leanangletracker.ui.theme.TextSecondary

private val CalibrationSuccessGreen = Color(0xFF22C55E)

@Composable
fun CalibrationWizardLandscape(
    state: CalibrationUiState,
    onCaptureUpright: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            HeaderWithSteps(state)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    CalibrationBikeLeanAnimation(
                        modifier = Modifier.fillMaxHeight(0.7f).aspectRatio(1f),
                        measuredAngleDeg = state.currentTiltDeg,
                        targetDirection = state.calibrationStep.takeIf {
                            !state.leanDetected && (it == BikeLean.LEFT || it == BikeLean.RIGHT)
                        }
                    )
                }

                Column(
                    modifier = Modifier.weight(1.5f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CalibrationStatusCard(state)
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        CalibrationProgressIndicator(state)
                    }

                    if (shouldShowActionButton(state)) {
                        Button(
                            onClick = onCaptureUpright,
                            enabled = state.currentProgress >= 1f && state.errorResId == null,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.currentProgress >= 1f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(text = getButtonText(state), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalibrationWizardPortrait(
    state: CalibrationUiState,
    onCaptureUpright: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
        ) {
            HeaderWithSteps(state)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.2f)
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CalibrationBikeLeanAnimation(
                    modifier = Modifier.fillMaxSize(0.7f),
                    measuredAngleDeg = state.currentTiltDeg,
                    targetDirection = state.calibrationStep.takeIf {
                        !state.leanDetected && (it == BikeLean.LEFT || it == BikeLean.RIGHT)
                    }
                )

                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    CalibrationProgressIndicator(state)
                }
            }

            CalibrationStatusCard(state)

            if (shouldShowActionButton(state)) {
                Button(
                    onClick = onCaptureUpright,
                    enabled = state.currentProgress >= 1f && state.errorResId == null,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(20.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Text(text = getButtonText(state), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun HeaderWithSteps(state: CalibrationUiState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.calibration_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        if (state.calibrationStep != BikeLean.DONE) {
            Text(
                text = stringResource(
                    R.string.calibration_step_indicator,
                    state.currentStepIndex,
                    state.totalSteps
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun shouldShowActionButton(state: CalibrationUiState): Boolean {
    return state.calibrationStep == BikeLean.UPRIGHT
}

@Composable
private fun getButtonText(state: CalibrationUiState): String {
    return when (state.calibrationStep) {
        BikeLean.UPRIGHT -> stringResource(R.string.calibration_action_fix_center)
        else -> ""
    }
}

@Composable
private fun CalibrationStatusCard(state: CalibrationUiState) {
    val backgroundColor by animateColorAsState(
        targetValue = when {
            state.errorResId != null -> Color(0xFF8B0000).copy(alpha = 0.2f)
            state.calibrationStep == BikeLean.DONE -> Color.Green.copy(alpha = 0.1f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        label = "statusColor"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        state.errorResId?.let { errorResId ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red)
                Text(
                    text = stringResource(errorResId),
                    color = Color.Red,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        if (state.calibrationStep == BikeLean.DONE) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Green, modifier = Modifier.padding(bottom = 4.dp))
        }

        val instructionText = when {
            state.leanDetected -> stringResource(R.string.calibration_instr_return_upright)
            state.calibrationStep == BikeLean.UPRIGHT -> stringResource(R.string.calibration_instr_upright)
            state.calibrationStep == BikeLean.LEFT -> stringResource(R.string.calibration_instr_left)
            state.calibrationStep == BikeLean.RIGHT -> stringResource(R.string.calibration_instr_right)
            else -> stringResource(R.string.calibration_instr_ready)
        }

        Text(
            text = instructionText,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = when {
                state.leanDetected -> stringResource(R.string.calibration_hint_return_upright)
                state.calibrationStep == BikeLean.UPRIGHT -> stringResource(R.string.calibration_hint_upright)
                state.calibrationStep == BikeLean.LEFT -> stringResource(R.string.calibration_hint_left)
                state.calibrationStep == BikeLean.RIGHT -> stringResource(R.string.calibration_hint_right)
                else -> stringResource(R.string.calibration_hint_ready)
            },
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CalibrationProgressIndicator(state: CalibrationUiState) {
    val isTiltStep = state.calibrationStep == BikeLean.LEFT ||
        state.calibrationStep == BikeLean.RIGHT
    val primaryColor = MaterialTheme.colorScheme.primary
    val mainFillColor by animateColorAsState(
        targetValue = if (!isTiltStep && state.currentProgress >= 1f) {
            CalibrationSuccessGreen
        } else {
            primaryColor
        },
        label = "calibrationProgressColor"
    )

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        CalibrationProgressBar(
            progress = state.currentProgress,
            primaryColor = mainFillColor,
            useTiltGradient = isTiltStep,
            showError = isTiltStep && state.errorResId != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
        )

        if (isTiltStep) {
            Spacer(modifier = Modifier.height(5.dp))
            CalibrationProgressBar(
                progress = state.maximumTiltProgress,
                primaryColor = primaryColor,
                useTiltGradient = true,
                subdued = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
            )
        }

        if (state.calibrationStep != BikeLean.DONE) {
            Text(
                text = when {
                    state.calibrationStep == BikeLean.UPRIGHT -> stringResource(
                        R.string.calibration_progress_label,
                        (state.currentProgress * 100).toInt()
                    )
                    state.leanDetected -> stringResource(R.string.calibration_status_return_upright)
                    else -> stringResource(R.string.calibration_status_auto_capture)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (state.leanDetected || state.currentProgress > 0.8f) Color.Green else TextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun CalibrationProgressBar(
    progress: Float,
    primaryColor: Color,
    useTiltGradient: Boolean,
    modifier: Modifier = Modifier,
    showError: Boolean = false,
    subdued: Boolean = false
) {
    val errorColor = MaterialTheme.colorScheme.error
    val fillAlpha = if (subdued) 0.35f else 1f
    val trackColor = Color.White.copy(alpha = if (subdued) 0.05f else 0.10f)

    Canvas(modifier = modifier) {
        val cornerRadius = CornerRadius(size.height / 2f)
        drawRoundRect(
            color = trackColor,
            size = size,
            cornerRadius = cornerRadius
        )

        val fillWidth = size.width * progress.coerceIn(0f, 1f)
        if (fillWidth <= 0f) return@Canvas

        val fillBrush = when {
            showError -> SolidColor(errorColor.copy(alpha = fillAlpha))
            useTiltGradient -> Brush.horizontalGradient(
                0f to primaryColor.copy(alpha = fillAlpha),
                CalibrationTiltProgress.MINIMUM_TILT_PROGRESS * 0.32f to
                    primaryColor.copy(alpha = fillAlpha),
                CalibrationTiltProgress.MINIMUM_TILT_PROGRESS * 0.56f to
                    lerp(primaryColor, CalibrationSuccessGreen, 0.20f).copy(alpha = fillAlpha),
                CalibrationTiltProgress.MINIMUM_TILT_PROGRESS * 0.76f to
                    lerp(primaryColor, CalibrationSuccessGreen, 0.50f).copy(alpha = fillAlpha),
                CalibrationTiltProgress.MINIMUM_TILT_PROGRESS * 0.92f to
                    lerp(primaryColor, CalibrationSuccessGreen, 0.78f).copy(alpha = fillAlpha),
                CalibrationTiltProgress.MINIMUM_TILT_PROGRESS to
                    CalibrationSuccessGreen.copy(alpha = fillAlpha),
                1f to CalibrationSuccessGreen.copy(alpha = fillAlpha),
                startX = 0f,
                endX = size.width
            )
            else -> SolidColor(primaryColor.copy(alpha = fillAlpha))
        }
        drawRoundRect(
            brush = fillBrush,
            size = Size(fillWidth, size.height),
            cornerRadius = cornerRadius
        )
    }
}
