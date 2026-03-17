package com.example.leanangletracker

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.leanangletracker.ui.intro.IntroScreen
import com.example.leanangletracker.ui.intro.IntroStage
import com.example.leanangletracker.ui.navigation.AppRoute
import com.example.leanangletracker.ui.settings.SettingsScreen
import com.example.leanangletracker.ui.theme.LeanAngleTrackerTheme
import com.example.leanangletracker.ui.tracking.LeanAngleScreen
import com.example.leanangletracker.ui.tracking.TrackReviewScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
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

                    LaunchedEffect(routeUiState.introStage) {
                        if (routeUiState.introStage != IntroStage.LOADING) return@LaunchedEffect
                        delay(900)
                        routeUiState = routeUiState.copy(introStage = IntroStage.ATTACH_PROMPT)
                    }

                    val route = resolveRoute(
                        introStage = routeUiState.introStage,
                        showSettings = routeUiState.showSettings,
                        showTrackReview = routeUiState.showTrackReview,
                        isCalibrated = state.calibration.isCalibrated,
                        hasCompletedRide = state.completedRide != null
                    )

                    AnimatedContent(
                        targetState = route,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(420, easing = LinearEasing)) togetherWith
                                fadeOut(animationSpec = tween(420, easing = LinearEasing))
                        },
                        label = "app_route"
                    ) { currentRoute ->
                        when (currentRoute) {
                            is AppRoute.Intro -> renderIntroRoute(
                                stage = currentRoute.stage,
                                onMountedConfirm = {
                                    routeUiState = routeUiState.copy(introStage = IntroStage.TRANSITION_OUT)
                                },
                                onTransitionFinished = {
                                    routeUiState = routeUiState.copy(introStage = IntroStage.DONE)
                                }
                            )

                            AppRoute.Tracking -> renderTrackingRoute(
                                trackingState = state.tracking,
                                calibrationState = state.calibration,
                                onOpenSettings = {
                                    routeUiState = routeUiState.copy(showSettings = true)
                                },
                                onStartTracking = viewModel::startTracking,
                                onFinishRide = {
                                    viewModel.finishRide()
                                    routeUiState = routeUiState.copy(showTrackReview = true)
                                }
                            )

                            AppRoute.Settings -> renderSettingsRoute(
                                state = state.settings,
                                onBack = {
                                    routeUiState = routeUiState.copy(showSettings = false)
                                },
                                onRequestLocationPermission = {
                                    permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                },
                                onStartCalibration = {
                                    routeUiState = routeUiState.copy(showSettings = false)
                                    viewModel.startCalibration()
                                }
                            )

                            AppRoute.TrackReview -> {
                                val completedRide = state.completedRide
                                if (completedRide != null) {
                                    TrackReviewScreen(
                                        rideSession = completedRide,
                                        onBack = {
                                            viewModel.clearCompletedRide()
                                            routeUiState = routeUiState.copy(showTrackReview = false)
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

    private fun resolveRoute(
        introStage: IntroStage,
        showSettings: Boolean,
        showTrackReview: Boolean,
        isCalibrated: Boolean,
        hasCompletedRide: Boolean
    ): AppRoute {
        if (introStage != IntroStage.DONE) return AppRoute.Intro(introStage)
        if (showTrackReview && hasCompletedRide) return AppRoute.TrackReview

        return if (showSettings && isCalibrated) AppRoute.Settings else AppRoute.Tracking
    }

    @Composable
    private fun renderIntroRoute(
        stage: IntroStage,
        onMountedConfirm: () -> Unit,
        onTransitionFinished: () -> Unit
    ) {
        IntroScreen(stage = stage, onMountedConfirm = onMountedConfirm, onTransitionFinished = onTransitionFinished)
    }

    @Composable
    private fun renderTrackingRoute(
        trackingState: TrackingUiState,
        calibrationState: CalibrationUiState,
        onOpenSettings: () -> Unit,
        onStartTracking: () -> Unit,
        onFinishRide: () -> Unit
    ) {
        LeanAngleScreen(
            trackingState = trackingState,
            calibrationState = calibrationState,
            onOpenSettings = onOpenSettings,
            onStartTracking = onStartTracking,
            onFinishRide = onFinishRide,
            onCaptureUpright = viewModel::captureUpright,
            onContinueCalibrationFallback = viewModel::continueCalibrationFallback
        )
    }

    @Composable
    private fun renderSettingsRoute(
        state: SettingsUiState,
        onBack: () -> Unit,
        onRequestLocationPermission: () -> Unit,
        onStartCalibration: () -> Unit
    ) {
        SettingsScreen(
            state = state,
            onBack = onBack,
            onToggleInvertLean = viewModel::setInvertLeanAngle,
            onToggleGyroFusion = viewModel::setUseGyroFusion,
            onToggleGpsTracking = { enabled ->
                if (enabled && !state.locationPermissionGranted) {
                    onRequestLocationPermission()
                }
                viewModel.setGpsTrackingEnabled(enabled)
            },
            onSetHistoryWindow = viewModel::setHistoryWindowSeconds,
            onSetRecorderIntervalMs = viewModel::setRecorderIntervalMs,
            onResetExtrema = viewModel::resetExtrema,
            onStartCalibration = onStartCalibration
        )
    }
}

private data class RouteUiState(
    val introStage: IntroStage = IntroStage.LOADING,
    val showSettings: Boolean = false,
    val showTrackReview: Boolean = false
) {
    companion object {
        val Saver: Saver<RouteUiState, Any> = listSaver(
            save = { listOf(it.introStage.name, it.showSettings, it.showTrackReview) },
            restore = {
                RouteUiState(
                    introStage = IntroStage.valueOf(it[0] as String),
                    showSettings = it[1] as Boolean,
                    showTrackReview = it[2] as Boolean
                )
            }
        )
    }
}
