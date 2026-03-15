package com.example.leanangletracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.leanangletracker.ui.intro.IntroScreen
import com.example.leanangletracker.ui.intro.IntroStage
import com.example.leanangletracker.ui.navigation.AppRoute
import com.example.leanangletracker.ui.settings.SettingsScreen
import com.example.leanangletracker.ui.tracking.LeanAngleScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    var routeUiState by rememberSaveable(stateSaver = RouteUiState.Saver) {
                        mutableStateOf(RouteUiState())
                    }

                    LaunchedEffect(Unit) {
                        delay(900)
                        routeUiState = routeUiState.copy(introStage = IntroStage.ATTACH_PROMPT)
                    }

                    val route = resolveRoute(
                        introStage = routeUiState.introStage,
                        showSettings = routeUiState.showSettings,
                        isCalibrated = state.isCalibrated
                    )

                    AnimatedContent(targetState = route, label = "app_route") { currentRoute ->
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
                                state = state,
                                onOpenSettings = {
                                    routeUiState = routeUiState.copy(showSettings = true)
                                }
                            )

                            AppRoute.Settings -> renderSettingsRoute(
                                state = state,
                                onBack = {
                                    routeUiState = routeUiState.copy(showSettings = false)
                                },
                                onStartCalibration = {
                                    routeUiState = routeUiState.copy(showSettings = false)
                                    viewModel.startCalibration()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun resolveRoute(
        introStage: IntroStage,
        showSettings: Boolean,
        isCalibrated: Boolean
    ): AppRoute {
        if (introStage != IntroStage.DONE) {
            return AppRoute.Intro(introStage)
        }

        return if (showSettings && isCalibrated) {
            AppRoute.Settings
        } else {
            AppRoute.Tracking
        }
    }

    @Composable
    private fun renderIntroRoute(
        stage: IntroStage,
        onMountedConfirm: () -> Unit,
        onTransitionFinished: () -> Unit
    ) {
        IntroScreen(
            stage = stage,
            onMountedConfirm = onMountedConfirm,
            onTransitionFinished = onTransitionFinished
        )
    }

    @Composable
    private fun renderTrackingRoute(
        state: UiState,
        onOpenSettings: () -> Unit
    ) {
        LeanAngleScreen(
            state = state,
            onOpenSettings = onOpenSettings,
            onCaptureUpright = viewModel::captureUpright,
            onStartLeftMeasurement = viewModel::startLeftMeasurement,
            onStartRightMeasurement = viewModel::startRightMeasurement
        )
    }

    @Composable
    private fun renderSettingsRoute(
        state: UiState,
        onBack: () -> Unit,
        onStartCalibration: () -> Unit
    ) {
        SettingsScreen(
            state = state,
            onBack = onBack,
            onToggleInvertLean = viewModel::setInvertLeanAngle,
            onSetHistoryWindow = viewModel::setHistoryWindowSeconds,
            onResetExtrema = viewModel::resetExtrema,
            onStartCalibration = onStartCalibration
        )
    }
}

private data class RouteUiState(
    val introStage: IntroStage = IntroStage.LOADING,
    val showSettings: Boolean = false
) {
    companion object {
        val Saver: Saver<RouteUiState, Any> = listSaver(
            save = { listOf(it.introStage.name, it.showSettings) },
            restore = {
                RouteUiState(
                    introStage = IntroStage.valueOf(it[0] as String),
                    showSettings = it[1] as Boolean
                )
            }
        )
    }
}
