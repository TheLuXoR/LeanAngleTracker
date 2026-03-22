package com.example.leanangletracker.ui.calibration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.leanangletracker.CalibrationUiState
import com.example.leanangletracker.ui.animation.BikeLean
import com.example.leanangletracker.ui.animation.CalibrationBikeLeanAnimation
import com.example.leanangletracker.ui.theme.TextSecondary

@Composable
fun CalibrationWizardLandscape(
    state: CalibrationUiState,
    onCaptureUpright: () -> Unit,
    onContinueFallback: () -> Unit // Ignored in manual mode
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
            verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically)
        ) {
            // Header
            Text(
                text = "Kalibrierung",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Row {
                // Animation Area
                val animTarget = when (state.calibrationStep) {
                    BikeLean.LEFT -> BikeLean.LEFT
                    BikeLean.RIGHT -> BikeLean.RIGHT
                    else -> BikeLean.UPRIGHT
                }

                CalibrationBikeLeanAnimation(
                    modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                    bikeAnimationFrom = animTarget,
                    bikeAnimationTo = BikeLean.UPRIGHT
                )

                // Instructions
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val instructionText = when (state.calibrationStep) {
                        BikeLean.UPRIGHT -> "Stelle dein Motorrad möglichst aufrecht."
                        BikeLean.LEFT -> "Neige dein Motorrad nach Links und wieder zurück."
                        BikeLean.RIGHT -> "Neige dein Motorrad nach Rechts und wieder zurück."
                        else -> "Kalibrierung abgeschlossen."
                    }

                    Text(
                        text = instructionText,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Text(
                        text = "Bestätige jeden Schritt mit dem Button unten.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    // Progress Overlay (Shows the max reached amplitude in current direction)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        CalibrationProgressIndicator(
                            state
                        )
                    }
                    // Action Button (Manual step progression)
                    Box(modifier = Modifier.height(80.dp), contentAlignment = Alignment.Center) {
                        if (state.calibrationStep != BikeLean.DONE) {
                            Button(
                                onClick = onCaptureUpright,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                            ) {
                                val buttonText = when (state.calibrationStep) {
                                    BikeLean.UPRIGHT -> "Position bestätigen"
                                    BikeLean.LEFT -> "Links bestätigt"
                                    BikeLean.RIGHT -> "Rechts bestätigt & Fertig"
                                    else -> ""
                                }
                                Text(
                                    text = buttonText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
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
    onCaptureUpright: () -> Unit,
    onContinueFallback: () -> Unit // Ignored in manual mode
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
                val animTarget = when (state.calibrationStep) {
                    BikeLean.LEFT -> BikeLean.LEFT
                    BikeLean.RIGHT -> BikeLean.RIGHT
                    else -> BikeLean.UPRIGHT
                }

                CalibrationBikeLeanAnimation(
                    modifier = Modifier.fillMaxSize(0.8f),
                    bikeAnimationFrom = animTarget,
                    bikeAnimationTo = BikeLean.UPRIGHT
                )

                // Progress Overlay (Shows the max reached amplitude in current direction)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    CalibrationProgressIndicator(
                        state
                    )
                }
            }

            // Instructions
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val instructionText = when (state.calibrationStep) {
                    BikeLean.UPRIGHT -> "Stelle dein Motorrad möglichst aufrecht."
                    BikeLean.LEFT -> "Neige dein Motorrad nach Links und wieder zurück."
                    BikeLean.RIGHT -> "Neige dein Motorrad nach Rechts und wieder zurück."
                    else -> "Kalibrierung abgeschlossen."
                }

                Text(
                    text = instructionText,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "Bestätige jeden Schritt mit dem Button unten.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }

            // Action Button (Manual step progression)
            Box(modifier = Modifier.height(80.dp), contentAlignment = Alignment.Center) {
                if (state.calibrationStep != BikeLean.DONE) {
                    Button(
                        onClick = onCaptureUpright,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        val buttonText = when (state.calibrationStep) {
                            BikeLean.UPRIGHT -> "Position bestätigen"
                            BikeLean.LEFT -> "Links bestätigt"
                            BikeLean.RIGHT -> "Rechts bestätigt & Fertig"
                            else -> ""
                        }
                        Text(
                            text = buttonText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalibrationProgressIndicator(
    state: CalibrationUiState
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Half (Current step's detected peak)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(state.leftMax)
                    .fillMaxHeight()
                    .background(Color.Green.copy(alpha = .3f))
            )
            if (state.calibrationStep == BikeLean.LEFT) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(state.currentProgress)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }

        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.3f))
        )

        // Right Half
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(state.rightMax)
                    .fillMaxHeight()
                    .background(Color.Green.copy(.3f))
            )
            if (state.calibrationStep == BikeLean.RIGHT) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(state.currentProgress)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}
