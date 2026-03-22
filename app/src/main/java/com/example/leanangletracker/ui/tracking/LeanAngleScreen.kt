package com.example.leanangletracker.ui.tracking

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.leanangletracker.CalibrationUiState
import com.example.leanangletracker.R
import com.example.leanangletracker.RideSession
import com.example.leanangletracker.TrackingUiState
import com.example.leanangletracker.ui.calibration.CalibrationWizard
import com.example.leanangletracker.ui.theme.*
import java.util.Locale

@Composable
internal fun LeanAngleScreen(
    trackingState: TrackingUiState,
    calibrationState: CalibrationUiState,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onStartTracking: () -> Unit,
    onFinishRide: () -> Unit,
    onTogglePause: () -> Unit = {},
    offerExtend: RideSession? = null,
    onConfirmExtend: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    onCaptureUpright: () -> Unit,
    onContinueCalibrationFallback: () -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val view = LocalView.current

    // Keep screen on while tracking is active
    DisposableEffect(trackingState.trackingStarted) {
        if (trackingState.trackingStarted) {
            view.keepScreenOn = true
        }
        onDispose {
            view.keepScreenOn = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        if (!calibrationState.isCalibrated) {
            CalibrationWizard(
                state = calibrationState,
                onCaptureUpright = onCaptureUpright,
                onContinueFallback = onContinueCalibrationFallback
            )
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.screen_title_lean_angle),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (trackingState.trackingStarted && trackingState.currentLatitude == null) {
                        RainbowSearchGpsText()
                    } else if (trackingState.gpsActive) {
                        Text(
                            text = if (trackingState.isPaused) "PAUSED" else "GPS ACTIVE",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (trackingState.isPaused) Color.Yellow else AccentGreen
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (trackingState.gpsTrackingEnabled && !trackingState.trackingStarted) {
                        FilledTonalIconButton(
                            onClick = onStartTracking,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(
                                Icons.Default.FiberManualRecord,
                                contentDescription = "Start Recording",
                                tint = ErrorRed,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    if (trackingState.trackingStarted) {
                        // Pause / Resume
                        IconButton(
                            onClick = onTogglePause,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            Icon(
                                if (trackingState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (trackingState.isPaused) "Resume" else "Pause",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Stop
                        Box(contentAlignment = Alignment.Center) {
                            if (!trackingState.isPaused) {
                                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                val alpha by infiniteTransition.animateFloat(
                                    initialValue = 0.4f,
                                    targetValue = 1f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1000, easing = FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "alpha"
                                )

                                CircularProgressIndicator(
                                    modifier = Modifier.size(42.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 3.dp
                                )
                                IconButton(
                                    onClick = onFinishRide,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(ErrorRed.copy(alpha = 0.15f * alpha))
                                ) {
                                    Icon(
                                        Icons.Default.Stop,
                                        contentDescription = "Stop Recording",
                                        tint = ErrorRed.copy(alpha = alpha),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = onFinishRide,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(ErrorRed.copy(alpha = 0.1f))
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = "Stop Recording", tint = ErrorRed)
                                }
                            }
                        }
                    } else {
                        IconButton(
                            onClick = onOpenHistory,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.List,
                                contentDescription = "History",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (isLandscape) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TachoGauge(
                            currentDeg = trackingState.leanAngleDeg,
                            maxLeftDeg = trackingState.maxLeftDeg,
                            maxRightDeg = trackingState.maxRightDeg,
                            modifier = Modifier
                                .weight(1.2f)
                                .fillMaxHeight()
                        )
                        LeanHistoryGraph(
                            values = trackingState.leanHistoryDeg,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                    AnimatedVisibility(
                        visible = trackingState.trackingStarted,
                        enter = fadeIn()
                    ) {
                        TrackingStatsCard(
                            trackingState,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else {
                TachoGauge(
                    currentDeg = trackingState.leanAngleDeg,
                    maxLeftDeg = trackingState.maxLeftDeg,
                    maxRightDeg = trackingState.maxRightDeg,
                    modifier = Modifier
                        .fillMaxWidth()
                )
                LeanHistoryGraph(
                    values = trackingState.leanHistoryDeg,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                AnimatedVisibility(
                    visible = trackingState.trackingStarted,
                    enter = expandVertically()
                ) {
                    TrackingStatsCard(
                        trackingState,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (offerExtend != null) {
        AlertDialog(
            onDismissRequest = { onConfirmExtend(false) },
            title = { Text("Ride fortsetzen?") },
            text = { Text("Du befindest dich in der Nähe des Endpunkts deiner letzten Fahrt von heute. Möchtest du diese Fahrt fortsetzen oder eine neue starten?") },
            confirmButton = {
                Button(onClick = { onConfirmExtend(true) }) {
                    Text("Fortsetzen")
                }
            },
            dismissButton = {
                TextButton(onClick = { onConfirmExtend(false) }) {
                    Text("Neu starten")
                }
            }
        )
    }
}

@Composable
private fun RainbowSearchGpsText() {
    val text = "SEARCHING GPS..."
    val infiniteTransition = rememberInfiniteTransition(label = "rainbow")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { index, char ->
            val color = Color.hsv((phase + index * 15f) % 360f, 0.7f, 0.9f)
            Text(
                text = char.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun TrackingStatsCard(
    trackingState: TrackingUiState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row (
            modifier = Modifier.fillMaxWidth().padding(12.dp).basicMarquee(),
        ) {
            val locationText =
                if (trackingState.currentLatitude != null && trackingState.currentLongitude != null) {
                    String.format(
                        Locale.US,
                        "%.6f, %.6f",
                        trackingState.currentLatitude,
                        trackingState.currentLongitude
                    )
                } else {
                    "n/a"
                }
            Text(modifier = Modifier.padding(16.dp, 0.dp),text = "speed: ${"%.1f".format(Locale.US, trackingState.speedKmh)} km/h")
            Text(modifier = Modifier.padding(16.dp, 0.dp),text = "location: $locationText")
            Text(modifier = Modifier.padding(16.dp, 0.dp),text = "elapsedTime: ${formatElapsedTime(trackingState.elapsedTimeMs)}")
            Text(modifier = Modifier.padding(16.dp, 0.dp),text = "avg spd: ${"%.1f".format(Locale.US, trackingState.averageSpeedKmh)} km/h")
            Text(modifier = Modifier.padding(16.dp, 0.dp),text = "trackLenght: ${"%.2f".format(Locale.US, trackingState.trackLengthKm)} km")
            Text(modifier = Modifier.padding(16.dp, 0.dp),text = "avg leanAngle: ${"%.1f".format(Locale.US, trackingState.averageLeanAngleDeg)}°")
        }
    }
}

private fun formatElapsedTime(elapsedMs: Long): String {
    val totalSeconds = (elapsedMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

@Composable
private fun TachoGauge(
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
        Column {
            Box(
                modifier = Modifier
                    .aspectRatio(2.0f),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize(0.85f)) {
                    val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height)
                    val radius = kotlin.math.min(size.width / 2f, size.height)
                    val maxDisplay = 65f

                    // Gauge Background
                    drawArc(
                        color = GaugeBackground,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = true,
                        topLeft = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                    )
                    // Border
                    drawArc(
                        color = Color.Blue.copy(0.3f),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
                    )

                    // Scale marks
                    for (mark in -60..60 step 10) {
                        val theta = Math.toRadians(mark.toDouble() - 90.0)
                        val isMajor = mark % 30 == 0
                        val outer = androidx.compose.ui.geometry.Offset(
                            x = center.x + (radius * 0.95f * kotlin.math.cos(theta)).toFloat(),
                            y = center.y + (radius * 0.95f * kotlin.math.sin(theta)).toFloat()
                        )
                        val innerFactor = if (isMajor) 0.82f else 0.88f
                        val inner = androidx.compose.ui.geometry.Offset(
                            x = center.x + (radius * innerFactor * kotlin.math.cos(theta)).toFloat(),
                            y = center.y + (radius * innerFactor * kotlin.math.sin(theta)).toFloat()
                        )
                        drawLine(
                            color = if (mark == 0) AccentGreen else GaugeScale.copy(alpha = 0.6f),
                            start = inner,
                            end = outer,
                            strokeWidth = if (isMajor) 4.dp.toPx() else 2.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    }

                    fun angleToTip(deg: Float, lengthFactor: Float): androidx.compose.ui.geometry.Offset {
                        val clamped = deg.coerceIn(-maxDisplay, maxDisplay)
                        val theta = Math.toRadians(clamped.toDouble() - 90.0)
                        return androidx.compose.ui.geometry.Offset(
                            x = center.x + (radius * lengthFactor * kotlin.math.cos(theta)).toFloat(),
                            y = center.y + (radius * lengthFactor * kotlin.math.sin(theta)).toFloat()
                        )
                    }

                    // Max indications
                    drawLine(
                        SecondaryBlue.copy(alpha = 0.4f),
                        center,
                        angleToTip(maxLeftDeg, 0.9f),
                        4.dp.toPx(),
                        androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    drawLine(
                        SecondaryBlue.copy(alpha = 0.4f),
                        center,
                        angleToTip(maxRightDeg, 0.9f),
                        4.dp.toPx(),
                        androidx.compose.ui.graphics.StrokeCap.Round
                    )

                    // Current needle
                    drawLine(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(GaugeNeedle, PrimaryOrange)),
                        start = center,
                        end = angleToTip(currentDeg, 0.88f),
                        strokeWidth = 8.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )

                    // Center hub
                    drawCircle(color = Color.White, radius = 12.dp.toPx(), center = center)
                    drawCircle(color = GaugeNeedle, radius = 6.dp.toPx(), center = center)
                }

                // Max Values
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(24.dp)
                ) {
                    MaxValItem("MAX L", kotlin.math.abs(maxLeftDeg))
                    Spacer(Modifier.weight(1f))
                    MaxValItem("MAX R", maxRightDeg)
                }
            }
        }
    }
}

@Composable
private fun MaxValItem(label: String, value: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Text(
            text = "${value.toInt()}°",
            style = MaterialTheme.typography.titleLarge,
            color = SecondaryBlue
        )
    }
}
