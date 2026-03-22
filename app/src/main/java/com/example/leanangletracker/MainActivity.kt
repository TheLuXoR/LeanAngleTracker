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
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import com.example.leanangletracker.ui.settings.SettingsScreen
import com.example.leanangletracker.ui.theme.LeanAngleTrackerTheme
import com.example.leanangletracker.ui.tracking.LeanAngleScreen
import com.example.leanangletracker.ui.tracking.RideHistoryScreen
import kotlinx.coroutines.delay
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically

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

                    LaunchedEffect(routeUiState.introStage) {
                        if (routeUiState.introStage != IntroStage.LOADING) return@LaunchedEffect
                        delay(800)
                        routeUiState = routeUiState.copy(introStage = IntroStage.LEGAL)
                    }

                    val route = resolveRoute(
                        introStage = routeUiState.introStage,
                        showSettings = routeUiState.showSettings,
                        showHistory = routeUiState.showHistory,
                        isCalibrated = state.calibration.isCalibrated
                    )

                    AnimatedContent(
                        targetState = route,
                        transitionSpec = {
                            val forward = targetState.index() > initialState.index()

                            slideInVertically(
                                    animationSpec = tween(300),
                                    initialOffsetY = { fullHeight -> if (forward) -fullHeight else fullHeight } // slide from bottom
                                ) togetherWith slideOutVertically(
                                    animationSpec = tween(300),
                                    targetOffsetY = { fullHeight -> if (forward) fullHeight else -fullHeight } // slide to top
                                )
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
                                calibrationState = state.calibration,
                                onOpenSettings = { routeUiState = routeUiState.copy(showSettings = true) },
                                onOpenHistory = { routeUiState = routeUiState.copy(showHistory = true) },
                                onStartTracking = viewModel::startTracking,
                                onFinishRide = {
                                    viewModel.finishRide()
                                    routeUiState = routeUiState.copy(showHistory = true)
                                },
                                onCaptureUpright = viewModel::captureUpright,
                                onContinueCalibrationFallback = viewModel::continueCalibrationFallback
                            )

                            AppRoute.Settings -> SettingsScreen(
                                state = state.settings,
                                onBack = { routeUiState = routeUiState.copy(showSettings = false) },
                                onToggleInvertLean = viewModel::setInvertLeanAngle,
                                onToggleGyroFusion = viewModel::setUseGyroFusion,
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
                                onBack = { routeUiState = routeUiState.copy(showHistory = false) },
                                onDeleteRide = viewModel::deleteRide
                            )
                        }
                    }
                }
            }
        }
    }

    private fun resolveRoute(introStage: IntroStage, showSettings: Boolean, showHistory: Boolean, isCalibrated: Boolean): AppRoute {
        if (introStage != IntroStage.DONE) {
            return AppRoute.Intro(introStage)
        }
        if (showHistory) {
            return AppRoute.TrackReview
        }
        if (showSettings && isCalibrated) {
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
    val showHistory: Boolean = false
) {
    companion object {
        val Saver: Saver<RouteUiState, Any> = listSaver(
            save = { listOf(it.introStage.name, it.showSettings, it.showHistory) },
            restore = {
                RouteUiState(
                    introStage = IntroStage.valueOf(it[0] as String),
                    showSettings = it[1] as Boolean,
                    showHistory = it[2] as Boolean
                )
            }
        )
    }
}
