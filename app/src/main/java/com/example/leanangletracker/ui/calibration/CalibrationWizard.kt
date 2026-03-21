package com.example.leanangletracker.ui.calibration

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.leanangletracker.CalibrationUiState
import com.example.leanangletracker.R
import com.example.leanangletracker.ui.animation.BikeLean
import com.example.leanangletracker.ui.animation.CalibrationBikeLeanAnimation
import com.example.leanangletracker.ui.theme.*

@Composable
internal fun CalibrationWizard(
    state: CalibrationUiState,
    onCaptureUpright: () -> Unit,
    onContinueFallback: () -> Unit
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
            verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically)
        ) {
            // Header
            Text(
                text = "Kalibrierung",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Animation Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                val isReturningUpright = state.tiltRecognitionProgress >= 1f
                
                CalibrationBikeLeanAnimation(
                    modifier = Modifier.fillMaxSize(0.8f),
                    bikeAnimationFrom = if (isReturningUpright) BikeLean.LEFT else BikeLean.UPRIGHT,
                    bikeAnimationTo = if (isReturningUpright) BikeLean.UPRIGHT else state.calibrationStep
                )
                
                // Progress Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    CalibrationProgressIndicator(
                        tiltProgress = state.tiltRecognitionProgress,
                        uprightProgress = state.uprightRecognitionProgress
                    )
                }
            }

            // Instructions
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val instructionText = if (state.tiltRecognitionProgress >= 1f) {
                    "Sehr gut! Jetzt richte das Motorrad wieder auf."
                } else {
                    stringResource(state.instructionsResId)
                }

                Text(
                    text = instructionText,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Text(
                    text = "Halte das Smartphone stabil in der Halterung.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }

            // Action Button
            Box(modifier = Modifier.height(80.dp), contentAlignment = Alignment.Center) {
                if (state.calibrationStep == BikeLean.UPRIGHT && !state.isCalibrated) {
                    Button(
                        onClick = onCaptureUpright,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.action_confirm_bike_upright),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (state.calibrationStep != BikeLean.DONE) {
                    // During active measurement, show a fallback/skip button if it takes too long
                    TextButton(onClick = onContinueFallback) {
                        Text(
                            text = "Schritt überspringen",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalibrationProgressIndicator(
    tiltProgress: Float,
    uprightProgress: Float
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f)),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val targetColor = if (tiltProgress >= 1f) AccentGreen else MaterialTheme.colorScheme.primary
        val animatedColor by animateColorAsState(targetColor, label = "progress_color")
        
        // Tilt Phase
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(animatedColor.copy(alpha = if (tiltProgress >= 1f) 1f else 0.4f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(tiltProgress)
                    .fillMaxHeight()
                    .background(animatedColor)
            )
        }
        
        // Upright Phase
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(AccentGreen.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(uprightProgress)
                    .fillMaxHeight()
                    .background(AccentGreen)
            )
        }
    }
}
