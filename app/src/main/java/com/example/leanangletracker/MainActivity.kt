package com.example.leanangletracker

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.leanangletracker.ui.intro.IntroScreen
import com.example.leanangletracker.ui.intro.IntroStage
import com.example.leanangletracker.ui.navigation.AppRoute
import com.example.leanangletracker.ui.navigation.ScreenDirection
import com.example.leanangletracker.ui.settings.SettingsScreen
import com.example.leanangletracker.ui.theme.LeanAngleTrackerTheme
import com.example.leanangletracker.ui.tracking.LeanAngleScreen
import com.example.leanangletracker.ui.tracking.RideHistoryScreen
import com.example.leanangletracker.ui.tracking.RideDetailScreen
import com.example.leanangletracker.ui.calibration.CalibrationScreen
import kotlinx.coroutines.delay
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LeanAngleTrackerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    var routeUiState by rememberSaveable(stateSaver = RouteUiState.Saver) {
                        mutableStateOf(RouteUiState())
                    }

                    val permissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { granted ->
                        viewModel.onLocationPermissionResult(granted)
                    }

                    // Handle Foreground Service for tracking
                    LaunchedEffect(state.tracking.trackingStarted) {
                        val intent = Intent(this@MainActivity, TrackingService::class.java)
                        if (state.tracking.trackingStarted) {
                            ContextCompat.startForegroundService(this@MainActivity, intent)
                        } else {
                            stopService(intent)
                        }
                    }

                    LaunchedEffect(routeUiState.introStage) {
                        if (routeUiState.introStage != IntroStage.LOADING) return@LaunchedEffect
                        delay(800)
                        routeUiState = routeUiState.copy(introStage = IntroStage.LEGAL)
                    }

                    val route = resolveRoute(
                        introStage = routeUiState.introStage,
                        showSettings = routeUiState.showSettings,
                        showHistory = routeUiState.showHistory,
                        selectedRideId = routeUiState.selectedRideId,
                        isCalibrated = state.calibration.isCalibrated
                    )

                    BackHandler(enabled = route is AppRoute.Settings || route is AppRoute.TrackReview || route is AppRoute.RideDetail || route is AppRoute.Calibration) {
                        when (route) {
                            AppRoute.Settings -> routeUiState = routeUiState.copy(showSettings = false)
                            AppRoute.TrackReview -> routeUiState = routeUiState.copy(showHistory = false)
                            is AppRoute.RideDetail -> routeUiState = routeUiState.copy(selectedRideId = null)
                            AppRoute.Calibration -> {
                                // Calibration is mandatory if not calibrated, otherwise we can go back
                                if (state.calibration.isCalibrated) {
                                    // Normally calibration is triggered from settings
                                    // If we're here, we just want to go back to wherever we came from
                                }
                            }
                            else -> Unit
                        }
                    }

                    AnimatedContent(
                        targetState = route,
                        transitionSpec = {
                            val forward = targetState.index() > initialState.index()
                            val effectiveDirection = if (forward) {
                                targetState.screen.screenEntryDirection
                            } else {
                                initialState.screen.screenEntryDirection
                            }
                            
                            if (effectiveDirection == ScreenDirection.HORIZONTAL) {
                                slideInHorizontally(
                                    animationSpec = tween(300),
                                    initialOffsetX = { fullWidth -> if (forward) fullWidth else -fullWidth }
                                ) togetherWith slideOutHorizontally(
                                    animationSpec = tween(300),
                                    targetOffsetX = { fullWidth -> if (forward) -fullWidth else fullWidth }
                                )
                            } else {
                                slideInVertically(
                                    animationSpec = tween(300),
                                    initialOffsetY = { fullHeight -> if (forward) -fullHeight else fullHeight }
                                ) togetherWith slideOutVertically(
                                    animationSpec = tween(300),
                                    targetOffsetY = { fullHeight -> if (forward) fullHeight else -fullHeight }
                                )
                            }
                        },
                        contentKey = { it::class },
                        label = "app_route"
                    ) { currentRoute ->
                        when (currentRoute) {
                            is AppRoute.Intro -> renderIntroRoute(
                                stage = currentRoute.stage,
                                onAction = {
                                    when (currentRoute.stage) {
                                        IntroStage.LEGAL -> {
                                            routeUiState = routeUiState.copy(introStage = IntroStage.ATTACH_PROMPT)
                                        }
                                        IntroStage.ATTACH_PROMPT -> {
                                            routeUiState = routeUiState.copy(introStage = IntroStage.TRANSITION_OUT)
                                        }
                                        else -> {}
                                    }
                                },
                                onTransitionFinished = {
                                    routeUiState = routeUiState.copy(introStage = IntroStage.DONE)
                                }
                            )

                            AppRoute.Tracking -> LeanAngleScreen(
                                trackingState = state.tracking,
                                onOpenSettings = { routeUiState = routeUiState.copy(showSettings = true) },
                                onOpenHistory = { routeUiState = routeUiState.copy(showHistory = true) },
                                onStartTracking = viewModel::startTracking,
                                onFinishRide = {
                                    val hadData = state.tracking.hasTrackData
                                    viewModel.finishRide()
                                    if (hadData) {
                                        routeUiState = routeUiState.copy(showHistory = true)
                                    }
                                },
                                onTogglePause = viewModel::togglePauseTracking,
                                offerExtend = state.offerExtendSession,
                                onConfirmExtend = viewModel::confirmExtendRide
                            )

                            AppRoute.Calibration -> CalibrationScreen(
                                calibrationState = state.calibration,
                                onCaptureUpright = viewModel::captureUpright,
                                onContinueFallback = viewModel::continueCalibrationFallback
                            )

                            AppRoute.Settings -> SettingsScreen(
                                state = state.settings,
                                onBack = { routeUiState = routeUiState.copy(showSettings = false) },
                                onToggleInvertLean = viewModel::setInvertLeanAngle,
                                onToggleGpsTracking = { enabled ->
                                    if (enabled && !state.settings.locationPermissionGranted) {
                                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                    }
                                    viewModel.setGpsTrackingEnabled(enabled)
                                },
                                onSetHistoryWindow = viewModel::setHistoryWindowSeconds,
                                onSetRecorderIntervalMs = viewModel::setRecorderIntervalMs,
                                onResetExtrema = viewModel::resetExtrema,
                                onStartCalibration = {
                                    routeUiState = routeUiState.copy(showSettings = false)
                                    viewModel.startCalibration()
                                }
                            )

                            AppRoute.TrackReview -> RideHistoryScreen(
                                rideHistory = state.rideHistory,
                                onSelectRide = { id -> 
                                    viewModel.loadFullSession(id)
                                    routeUiState = routeUiState.copy(selectedRideId = id) 
                                },
                                onBack = { routeUiState = routeUiState.copy(showHistory = false) },
                                onDeleteRide = viewModel::deleteRide,
                                onCombineRides = viewModel::combineRides
                            )

                            is AppRoute.RideDetail -> {
                                val summary = state.rideHistory.find { it.startedAtMs == currentRoute.rideId }
                                if (summary != null) {
                                    RideDetailScreen(
                                        rideSummary = summary,
                                        fullSession = state.expandedRides[currentRoute.rideId],
                                        onBack = { routeUiState = routeUiState.copy(selectedRideId = null) },
                                        onUpdateName = { viewModel.updateRideName(summary, it) },
                                        onDelete = { 
                                            viewModel.deleteRide(summary)
                                            routeUiState = routeUiState.copy(selectedRideId = null)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun resolveRoute(introStage: IntroStage, showSettings: Boolean, showHistory: Boolean, selectedRideId: Long?, isCalibrated: Boolean): AppRoute {
        if (introStage != IntroStage.DONE) {
            return AppRoute.Intro(introStage)
        }
        if (!isCalibrated) {
            return AppRoute.Calibration
        }
        if (selectedRideId != null) {
            return AppRoute.RideDetail(selectedRideId)
        }
        if (showHistory) {
            return AppRoute.TrackReview
        }
        if (showSettings) {
            return AppRoute.Settings
        } else {
            return AppRoute.Tracking
        }
    }

    @Composable
    private fun renderIntroRoute(
        stage: IntroStage,
        onAction: () -> Unit,
        onTransitionFinished: () -> Unit
    ) {
        IntroScreen(stage = stage, onAction = onAction, onTransitionFinished = onTransitionFinished)
    }
}

private data class RouteUiState(
    val introStage: IntroStage = IntroStage.LOADING,
    val showSettings: Boolean = false,
    val showHistory: Boolean = false,
    val selectedRideId: Long? = null
) {
    companion object {
        val Saver: Saver<RouteUiState, Any> = listSaver(
            save = { listOf(it.introStage.name, it.showSettings, it.showHistory, it.selectedRideId ?: -1L) },
            restore = {
                val rideId = it[3] as Long
                RouteUiState(
                    introStage = IntroStage.valueOf(it[0] as String),
                    showSettings = it[1] as Boolean,
                    showHistory = it[2] as Boolean,
                    selectedRideId = if (rideId == -1L) null else rideId
                )
            }
        )
    }
}
