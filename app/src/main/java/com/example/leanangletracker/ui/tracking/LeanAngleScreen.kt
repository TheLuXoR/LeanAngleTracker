package com.example.leanangletracker.ui.tracking

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.leanangletracker.BuildConfig
import com.example.leanangletracker.R
import com.example.leanangletracker.AppTourUiState
import com.example.leanangletracker.RideSession
import com.example.leanangletracker.SensorSamplingRate
import com.example.leanangletracker.TrackingUiState
import com.example.leanangletracker.ui.components.GpsStatsDashboard
import com.example.leanangletracker.ui.components.LeanHistoryGraph
import com.example.leanangletracker.ui.components.TachoGauge
import com.example.leanangletracker.ui.components.admob.AdMobBanner
import com.example.leanangletracker.ui.components.buttons.HistoryButton
import com.example.leanangletracker.ui.components.buttons.PauseButton
import com.example.leanangletracker.ui.components.buttons.RecordButton
import com.example.leanangletracker.ui.theme.AccentGreen
import com.example.leanangletracker.ui.theme.LeanAngleTrackerTheme
import kotlin.math.abs

private const val AUTO_PAUSE_LEAN_THRESHOLD = 45f

@Composable
internal fun LeanAngleScreen(
    trackingState: TrackingUiState,
    onOpenSettings: () -> Unit,
    onOpenPremium: () -> Unit,
    onOpenHistory: () -> Unit,
    onStartTracking: () -> Unit,
    onFinishRide: () -> Unit,
    onStartCalibration: () -> Unit = {},
    onTogglePause: () -> Unit = {},
    onResetGaugeExtrema: () -> Unit = {},
    onSetSensorSamplingRate: (SensorSamplingRate) -> Unit = {},
    onDebugClearEntitlementCache: () -> Unit = {},
    onDebugExpireEntitlementCache: () -> Unit = {},
    onAutoResumeIndicatorDismissed: () -> Unit = {},
    appTourState: AppTourUiState = AppTourUiState(),
    onAcceptAppTourOffer: () -> Unit = {},
    onDeclineAppTourOffer: () -> Unit = {},
    onPreviousAppTourPage: () -> Unit = {},
    onNextAppTourPage: () -> Unit = {},
    onFinishAppTour: () -> Unit = {},
    offerExtend: RideSession? = null,
    onConfirmExtend: (Boolean) -> Unit = {},
    isDebugBuild: Boolean = BuildConfig.DEBUG,
    showAdBanner: Boolean = true,
    modifier: Modifier = Modifier
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val view = LocalView.current
    var autoResumeIndicatorRequestId by remember { mutableIntStateOf(0) }

    LaunchedEffect(trackingState.showAutoResumePremiumShortcut) {
        if (trackingState.showAutoResumePremiumShortcut) {
            autoResumeIndicatorRequestId++
        }
    }

    DisposableEffect(trackingState.trackingStarted) {
        if (trackingState.trackingStarted) {
            view.keepScreenOn = true
        }
        onDispose {
            view.keepScreenOn = false
        }
    }

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
            TrackingHeader(
                trackingStarted = trackingState.trackingStarted,
                currentLatitude = trackingState.currentLatitude,
                gpsActive = trackingState.gpsActive,
                isPaused = trackingState.isPaused,
                autoPauseEnabled = trackingState.autoPauseEnabled,
                leanAngleDeg = trackingState.leanAngleDeg,
                isUpsideDown = trackingState.isUpsideDown,
                onTogglePause = onTogglePause,
                onStartTracking = onStartTracking,
                onFinishRide = onFinishRide,
                onOpenHistory = onOpenHistory,
                onOpenSettings = onOpenSettings,
                showDebugFeatures = isDebugBuild,
                sensorSamplingRate = trackingState.sensorSamplingRate,
                onShowAutoResumeIndicator = { autoResumeIndicatorRequestId++ },
                onSetSensorSamplingRate = onSetSensorSamplingRate,
                debugCacheActionsEnabled = !trackingState.trackingStarted,
                onDebugClearEntitlementCache = onDebugClearEntitlementCache,
                onDebugExpireEntitlementCache = onDebugExpireEntitlementCache
            )

            MountOrientationWarning(
                visible = trackingState.isUpsideDown || trackingState.showHighRotationWarning,
                isUpsideDown = trackingState.isUpsideDown,
                onStartCalibration = onStartCalibration
            )

            AutoResumePremiumShortcut(
                displayRequestId = autoResumeIndicatorRequestId,
                onOpenPremium = onOpenPremium,
                onDismiss = onAutoResumeIndicatorDismissed
            )

            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1.1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TachoGauge(
                            modifier = Modifier.weight(1f),
                            currentDeg = trackingState.leanAngleDeg,
                            maxLeftDeg = trackingState.gaugeExtrema.maxLeftDeg,
                            maxRightDeg = trackingState.gaugeExtrema.maxRightDeg,
                            onResetMaxValues = onResetGaugeExtrema
                        )
                        if (showAdBanner) movableBanner()
                    }

                    Column (
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ){
                        LeanHistoryGraph(
                            modifier = Modifier.weight(1f),
                            values = trackingState.leanHistoryDeg,
                            showCursorLine = false,
                            selectedIndex = trackingState.leanHistoryDeg.lastIndex.coerceAtLeast(0)
                        )

                        if (trackingState.currentLatitude != null) {
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
                TachoGauge(
                    currentDeg = trackingState.leanAngleDeg,
                    maxLeftDeg = trackingState.gaugeExtrema.maxLeftDeg,
                    maxRightDeg = trackingState.gaugeExtrema.maxRightDeg,
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f),
                    onResetMaxValues = onResetGaugeExtrema
                )

                if (trackingState.currentLatitude != null) {
                    GpsStatsDashboard(
                        speedKmh = trackingState.speedKmh,
                        distanceKm = trackingState.trackLengthKm,
                        elapsedTimeMs = trackingState.elapsedTimeMs,
                        isLandscape = false
                    )
                }

                LeanHistoryGraph(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    values = trackingState.leanHistoryDeg,
                    showCursorLine = false,
                    selectedIndex = trackingState.leanHistoryDeg.lastIndex.coerceAtLeast(0)
                )

                if (showAdBanner) movableBanner()
            }
        }

        if (offerExtend != null) {
            AlertDialog(
                onDismissRequest = { onConfirmExtend(false) },
                title = { Text(stringResource(R.string.dialog_extend_ride_title)) },
                confirmButton = {
                    TextButton(onClick = { onConfirmExtend(true) }) { Text(stringResource(R.string.dialog_extend_ride_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { onConfirmExtend(false) }) { Text(stringResource(R.string.dialog_extend_ride_dismiss)) }
                }
            )
        }

        AppTourDialogs(
            state = appTourState,
            onAcceptOffer = onAcceptAppTourOffer,
            onDeclineOffer = onDeclineAppTourOffer,
            onPreviousPage = onPreviousAppTourPage,
            onNextPage = onNextAppTourPage,
            onFinish = onFinishAppTour
        )
    }
}

@Composable
private fun TrackingHeader(
    trackingStarted: Boolean,
    currentLatitude: Double?,
    gpsActive: Boolean,
    isPaused: Boolean,
    autoPauseEnabled: Boolean,
    leanAngleDeg: Float,
    isUpsideDown: Boolean,
    onTogglePause: () -> Unit,
    onStartTracking: () -> Unit,
    onFinishRide: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    showDebugFeatures: Boolean,
    sensorSamplingRate: SensorSamplingRate,
    onShowAutoResumeIndicator: () -> Unit,
    onSetSensorSamplingRate: (SensorSamplingRate) -> Unit,
    debugCacheActionsEnabled: Boolean,
    onDebugClearEntitlementCache: () -> Unit,
    onDebugExpireEntitlementCache: () -> Unit
) {
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
            
            val statusText by remember(isPaused, autoPauseEnabled, leanAngleDeg, gpsActive) {
                derivedStateOf {
                    if (isPaused) {
                        if (autoPauseEnabled && abs(leanAngleDeg) >= AUTO_PAUSE_LEAN_THRESHOLD) {
                            "Rotation too far — paused."
                        } else {
                            "PAUSED"
                        }
                    } else if (gpsActive) {
                        "GPS ACTIVE"
                    } else ""
                }
            }

            if (trackingStarted && currentLatitude == null) {
                RainbowSearchGpsText()
            } else if (statusText.isNotEmpty()) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isPaused) Color.Yellow else AccentGreen
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PauseButton(
                onClick = onTogglePause,
                isPaused = isPaused,
                isVisible = trackingStarted && currentLatitude != null,
                enabled = !isUpsideDown && (!autoPauseEnabled || abs(leanAngleDeg) < AUTO_PAUSE_LEAN_THRESHOLD)
            )

            RecordButton(
                onRecord = onStartTracking,
                onStopRecord = onFinishRide,
                isPaused = isPaused,
                isRecording = trackingStarted,
                isWaitingForGps = trackingStarted && currentLatitude == null
            )

            HistoryButton(onOpenHistory = onOpenHistory, enabled = !trackingStarted)

            if (showDebugFeatures) {
                DebugFeatureMenu(
                    sensorSamplingRate = sensorSamplingRate,
                    onShowAutoResumeIndicator = onShowAutoResumeIndicator,
                    onSetSensorSamplingRate = onSetSensorSamplingRate,
                    cacheActionsEnabled = debugCacheActionsEnabled,
                    onClearEntitlementCache = onDebugClearEntitlementCache,
                    onExpireEntitlementCache = onDebugExpireEntitlementCache
                )
            }
            
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
internal fun MountOrientationWarning(
    visible: Boolean,
    isUpsideDown: Boolean,
    onStartCalibration: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.errorContainer).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(
                    text = if (isUpsideDown) stringResource(R.string.tracking_warning_upside_down) else stringResource(R.string.tracking_warning_high_rotation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onStartCalibration, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text(stringResource(R.string.action_recalibrate))
                }
            }
        }
    }
}

@Composable
private fun RainbowSearchGpsText() {
    val text = stringResource(R.string.tracking_searching_gps)
    val infiniteTransition = rememberInfiniteTransition(label = "rainbow")
    
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "brushOffset"
    )

    val rainbowBrush = remember(offset) {
        Brush.linearGradient(
            colors = listOf(Color.Red, Color.Yellow, Color.Green, Color.Blue, Color.Magenta, Color.Red),
            start = Offset(offset, 0f),
            end = Offset(offset + 400f, 0f),
            tileMode = TileMode.Repeated
        )
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.merge(TextStyle(brush = rainbowBrush)),
        fontWeight = FontWeight.Bold
    )
}

@Preview(showBackground = true, widthDp = 420, heightDp = 840)
@Composable
private fun LeanAngleScreenPreview() {
    LeanAngleTrackerTheme {
        LeanAngleScreen(
            trackingState = TrackingUiState(),
            onOpenSettings = {},
            onOpenPremium = {},
            onOpenHistory = {},
            onStartTracking = {},
            onFinishRide = {},
            isDebugBuild = true,
            showAdBanner = false
        )
    }
}
