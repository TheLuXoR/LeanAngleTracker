package com.example.leanangletracker.ui.tracking

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.leanangletracker.MainViewModel
import com.example.leanangletracker.R
import com.example.leanangletracker.RideSession
import com.example.leanangletracker.TrackingUiState
import com.example.leanangletracker.ui.components.GpsStatsDashboard
import com.example.leanangletracker.ui.components.buttons.HistoryButton
import com.example.leanangletracker.ui.components.LeanHistoryGraph
import com.example.leanangletracker.ui.components.TachoGauge
import com.example.leanangletracker.ui.components.admob.AdMobBanner
import com.example.leanangletracker.ui.components.buttons.PauseButton
import com.example.leanangletracker.ui.components.buttons.RecordButton
import com.example.leanangletracker.ui.theme.AccentGreen
import kotlin.math.abs

@Composable
internal fun LeanAngleScreen(
    trackingState: TrackingUiState,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onStartTracking: () -> Unit,
    onFinishRide: () -> Unit,
    onStartCalibration: () -> Unit = {},
    onTogglePause: () -> Unit = {},
    offerExtend: RideSession? = null,
    onConfirmExtend: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
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

    // Use movableContentOf to keep the AdMobBanner instance (and its state) alive across layout changes
    val movableBanner = remember {
        movableContentOf {
            AdMobBanner(modifier = Modifier.fillMaxWidth())
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
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
                        var text = ""
                        if (trackingState.isPaused){
                            if(trackingState.autoPauseEnabled && abs(trackingState.leanAngleDeg) >= MainViewModel.AUTO_PAUSE_LEAN_THRESHOLD){
                                text = "Rotation too far — paused."
                            } else {
                                text = "PAUSED"
                            }
                        } else {
                            text = "GPS ACTIVE"
                        }

                        Text(
                            text =  text,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (trackingState.isPaused) Color.Yellow else AccentGreen
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    PauseButton(onClick = onTogglePause,
                        isPaused = trackingState.isPaused,
                        isVisible = trackingState.gpsTrackingEnabled && trackingState.trackingStarted && trackingState.currentLatitude != null,
                        enabled = !trackingState.isUpsideDown && (!trackingState.autoPauseEnabled || abs(trackingState.leanAngleDeg) < MainViewModel.AUTO_PAUSE_LEAN_THRESHOLD)
                    )

                    RecordButton(
                        onRecord =  onStartTracking,
                        onStopRecord = onFinishRide,
                        isPaused = trackingState.isPaused,
                        isRecording = trackingState.trackingStarted,
                        isWaitingForGps = trackingState.trackingStarted && trackingState.currentLatitude == null
                    )

                    HistoryButton(
                        onOpenHistory = onOpenHistory,
                        enabled = !trackingState.trackingStarted
                    )
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

            AnimatedVisibility(
                visible = trackingState.isUpsideDown || trackingState.showHighRotationWarning,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = if (trackingState.isUpsideDown) 
                                stringResource(R.string.tracking_warning_upside_down) 
                            else 
                                stringResource(R.string.tracking_warning_high_rotation),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        
                        TextButton(
                            onClick = onStartCalibration,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Text(stringResource(R.string.action_recalibrate))
                        }
                    }
                }
            }

            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left Column: Gauge and Ads
                    Column(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TachoGauge(
                            modifier = Modifier.weight(1f),
                            currentDeg = trackingState.leanAngleDeg,
                            maxLeftDeg = trackingState.maxLeftDeg,
                            maxRightDeg = trackingState.maxRightDeg
                        )
                        
                        movableBanner()
                    }

                    // Right Column: Graph and GPS Stats
                    Column (
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ){
                        LeanHistoryGraph(
                            modifier = Modifier.weight(1f),
                            values = trackingState.leanHistoryDeg,
                            showCursorLine = false,
                            selectedIndex = trackingState.leanHistoryDeg.lastIndex.coerceAtLeast(0)
                        )

                        AnimatedVisibility(
                            visible = trackingState.currentLatitude != null,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            GpsStatsDashboard(
                                speedKmh = trackingState.speedKmh,
                                distanceKm = trackingState.trackLengthKm,
                                elapsedTimeMs = trackingState.elapsedTimeMs,
                                isLandscape = true
                            )
                        }
                    }
                }
            } else {
                // Portrait Layout
                TachoGauge(
                    currentDeg = trackingState.leanAngleDeg,
                    maxLeftDeg = trackingState.maxLeftDeg,
                    maxRightDeg = trackingState.maxRightDeg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f)
                )

                AnimatedVisibility(
                    visible = trackingState.currentLatitude != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    GpsStatsDashboard(
                        speedKmh = trackingState.speedKmh,
                        distanceKm = trackingState.trackLengthKm,
                        elapsedTimeMs = trackingState.elapsedTimeMs,
                        isLandscape = false
                    )
                }

                LeanHistoryGraph(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    values = trackingState.leanHistoryDeg,
                    showCursorLine = false,
                    selectedIndex = trackingState.leanHistoryDeg.lastIndex.coerceAtLeast(0)
                )

                movableBanner()
            }
        }

        // Extend Ride Dialog
        if (offerExtend != null) {
            AlertDialog(
                onDismissRequest = { onConfirmExtend(false) },
                title = { Text(stringResource(R.string.dialog_extend_ride_title)) },
                text = { Text(stringResource(R.string.dialog_extend_ride_message)) },
                confirmButton = {
                    TextButton(onClick = { onConfirmExtend(true) }) {
                        Text(stringResource(R.string.dialog_extend_ride_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onConfirmExtend(false) }) {
                        Text(stringResource(R.string.dialog_extend_ride_dismiss))
                    }
                }
            )
        }
    }
}

@Composable
private fun RainbowSearchGpsText() {
    val text = stringResource(R.string.tracking_searching_gps)

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
