package de.hasselmeyer.leanangle.ui.calibration

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.hasselmeyer.leanangle.CalibrationUiState
import de.hasselmeyer.leanangle.R
import de.hasselmeyer.leanangle.sensor.CalibrationTiltProgress
import de.hasselmeyer.leanangle.ui.animation.BikeLean
import de.hasselmeyer.leanangle.ui.animation.CalibrationBikeLeanAnimation
import de.hasselmeyer.leanangle.ui.theme.LeanAngleTrackerTheme
import de.hasselmeyer.leanangle.ui.theme.TextSecondary

private val CalibrationSuccessGreen = Color(0xFF22C55E)

@Composable
fun CalibrationWizardLandscape(
    state: CalibrationUiState,
    offerAppTour: Boolean,
    onStartUprightMeasurement: () -> Unit,
    onFinishCalibration: (startAppTour: Boolean) -> Unit
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

            if (state.calibrationStep == BikeLean.DONE) {
                CalibrationCompletionStep(
                    offerAppTour = offerAppTour,
                    onFinishCalibration = onFinishCalibration,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        CalibrationBikeLeanAnimation(
                            modifier = Modifier.fillMaxHeight(0.7f).aspectRatio(1f),
                            measuredAngleDeg = state.currentTiltDeg,
                            targetDirection = state.calibrationStep.takeIf {
                                !state.leanDetected && (it == BikeLean.LEFT || it == BikeLean.RIGHT)
                            },
                            showReturnUpright = state.leanDetected
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1.5f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CalibrationStatusCard(state)
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CalibrationProgressIndicator(state)
                        }
                        if (state.calibrationStep == BikeLean.UPRIGHT) {
                            UprightMeasurementButton(
                                measurementStarted = state.uprightMeasurementStarted,
                                onStartMeasurement = onStartUprightMeasurement
                            )
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
    offerAppTour: Boolean,
    onStartUprightMeasurement: () -> Unit,
    onFinishCalibration: (startAppTour: Boolean) -> Unit
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

            if (state.calibrationStep == BikeLean.DONE) {
                CalibrationCompletionStep(
                    offerAppTour = offerAppTour,
                    onFinishCalibration = onFinishCalibration,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            } else {
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
                        },
                        showReturnUpright = state.leanDetected
                    )

                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        CalibrationProgressIndicator(state)
                    }
                }

                CalibrationStatusCard(state)

                if (state.calibrationStep == BikeLean.UPRIGHT) {
                    UprightMeasurementButton(
                        measurementStarted = state.uprightMeasurementStarted,
                        onStartMeasurement = onStartUprightMeasurement
                    )
                }
            }
        }
    }
}

@Composable
private fun UprightMeasurementButton(
    measurementStarted: Boolean,
    onStartMeasurement: () -> Unit
) {
    Button(
        onClick = onStartMeasurement,
        enabled = !measurementStarted,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
    ) {
        Text(
            text = stringResource(
                if (measurementStarted) {
                    R.string.calibration_action_measurement_running
                } else {
                    R.string.calibration_action_start_measurement
                }
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold
        )
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
        Text(
            text = stringResource(
                R.string.calibration_step_indicator,
                state.currentStepIndex,
                state.totalSteps
            ),
            style = MaterialTheme.typography.labelMedium,
            color = if (state.calibrationStep == BikeLean.DONE) {
                CalibrationSuccessGreen
            } else {
                MaterialTheme.colorScheme.primary
            },
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CalibrationCompletionStep(
    offerAppTour: Boolean,
    onFinishCalibration: (startAppTour: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.testTag("calibrationCompletionStep"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)
    ) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(CalibrationSuccessGreen.copy(alpha = 0.16f))
                .border(2.dp, CalibrationSuccessGreen.copy(alpha = 0.75f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = CalibrationSuccessGreen,
                modifier = Modifier.size(68.dp)
            )
        }
        Text(
            text = stringResource(R.string.calibration_success_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(
                if (offerAppTour) {
                    R.string.calibration_success_body_tour
                } else {
                    R.string.calibration_success_body_ready
                }
            ),
            modifier = Modifier.widthIn(max = 520.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = { onFinishCalibration(offerAppTour) },
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp).height(60.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CalibrationSuccessGreen)
        ) {
            Text(
                text = stringResource(
                    if (offerAppTour) {
                        R.string.calibration_action_start_tour
                    } else {
                        R.string.calibration_action_continue
                    }
                ),
                fontWeight = FontWeight.ExtraBold
            )
        }
        if (offerAppTour) {
            TextButton(onClick = { onFinishCalibration(false) }) {
                Text(stringResource(R.string.calibration_action_skip_tour))
            }
        }
    }
}

@Composable
private fun CalibrationStatusCard(state: CalibrationUiState) {
    val backgroundColor by animateColorAsState(
        targetValue = when {
            state.errorResId != null -> Color(0xFF8B0000).copy(alpha = 0.2f)
            state.leanDetected -> CalibrationSuccessGreen.copy(alpha = 0.20f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        label = "statusColor"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (state.leanDetected) {
                    Modifier.testTag("calibrationReturnUprightFeedback")
                } else {
                    Modifier
                }
            )
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .then(
                if (state.leanDetected) {
                    Modifier.border(
                        width = 2.dp,
                        color = CalibrationSuccessGreen.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(20.dp)
                    )
                } else {
                    Modifier
                }
            )
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

        if (state.leanDetected) {
            Icon(
                Icons.Default.ArrowUpward,
                contentDescription = null,
                tint = CalibrationSuccessGreen,
                modifier = Modifier.size(36.dp)
            )
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
            fontWeight = if (state.leanDetected) FontWeight.ExtraBold else FontWeight.Bold,
            color = if (state.leanDetected) CalibrationSuccessGreen else Color.Unspecified
        )

        Text(
            text = when {
                state.leanDetected -> stringResource(R.string.calibration_hint_return_upright)
                state.calibrationStep == BikeLean.UPRIGHT &&
                    !state.uprightMeasurementStarted ->
                    stringResource(R.string.calibration_hint_upright_ready)
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

        Text(
            text = when {
                state.calibrationStep == BikeLean.UPRIGHT &&
                    !state.uprightMeasurementStarted ->
                    stringResource(R.string.calibration_status_ready_to_measure)
                state.calibrationStep == BikeLean.UPRIGHT -> stringResource(
                    R.string.calibration_progress_label,
                    (state.currentProgress * 100).toInt()
                )
                state.leanDetected -> stringResource(R.string.calibration_status_return_upright)
                else -> stringResource(R.string.calibration_status_auto_capture)
            },
            style = if (state.leanDetected) {
                MaterialTheme.typography.labelLarge
            } else {
                MaterialTheme.typography.labelSmall
            },
            fontWeight = if (state.leanDetected) FontWeight.ExtraBold else FontWeight.Normal,
            color = if (state.leanDetected || state.currentProgress > 0.8f) {
                CalibrationSuccessGreen
            } else {
                TextSecondary
            },
            modifier = Modifier.padding(top = if (state.leanDetected) 8.dp else 4.dp)
        )
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

@Preview(name = "Kalibrierung – Messung starten", showBackground = true, heightDp = 780)
@Composable
private fun CalibrationStartMeasurementPreview() {
    LeanAngleTrackerTheme {
        CalibrationWizardPortrait(
            state = CalibrationUiState(),
            offerAppTour = true,
            onStartUprightMeasurement = {},
            onFinishCalibration = {}
        )
    }
}

@Preview(name = "Kalibrierung – wieder aufrichten", showBackground = true, heightDp = 780)
@Composable
private fun CalibrationReturnPreview() {
    LeanAngleTrackerTheme {
        CalibrationWizardPortrait(
            state = CalibrationUiState(
                calibrationStep = BikeLean.LEFT,
                currentProgress = 0.5f,
                maximumTiltProgress = 0.78f,
                leanDetected = true,
                currentTiltDeg = -11f,
                currentStepIndex = 2
            ),
            offerAppTour = true,
            onStartUprightMeasurement = {},
            onFinishCalibration = {}
        )
    }
}

@Preview(name = "Kalibrierung – erfolgreich", showBackground = true, heightDp = 780)
@Composable
private fun CalibrationCompletionPreview() {
    LeanAngleTrackerTheme {
        CalibrationWizardPortrait(
            state = CalibrationUiState(
                calibrationStep = BikeLean.DONE,
                isCalibrated = true,
                completionPending = true,
                currentProgress = 1f,
                currentStepIndex = 4
            ),
            offerAppTour = true,
            onStartUprightMeasurement = {},
            onFinishCalibration = {}
        )
    }
}
