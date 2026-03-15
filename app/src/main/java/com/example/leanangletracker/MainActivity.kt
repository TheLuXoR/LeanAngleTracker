package com.example.leanangletracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.leanangletracker.ui.intro.IntroScreen
import com.example.leanangletracker.ui.intro.IntroStage
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
                    var showSettings by rememberSaveable { mutableStateOf(false) }
                    var introStage by rememberSaveable { mutableStateOf(IntroStage.LOADING) }

                    LaunchedEffect(Unit) {
                        delay(900)
                        introStage = IntroStage.ATTACH_PROMPT
                    }

                    AnimatedContent(targetState = introStage, label = "intro_or_app") { stage ->
                        when (stage) {
                            IntroStage.LOADING,
                            IntroStage.ATTACH_PROMPT,
                            IntroStage.TRANSITION_OUT -> IntroScreen(
                                stage = stage,
                                onMountedConfirm = { introStage = IntroStage.TRANSITION_OUT },
                                onTransitionFinished = { introStage = IntroStage.DONE }
                            )

                            IntroStage.DONE -> {
                                if (state.isCalibrated && showSettings) {
                                    SettingsScreen(
                                        state = state,
                                        onBack = { showSettings = false },
                                        onToggleInvertLean = viewModel::setInvertLeanAngle,
                                        onSetHistoryWindow = viewModel::setHistoryWindowSeconds,
                                        onResetExtrema = viewModel::resetExtrema,
                                        onStartCalibration = {
                                            showSettings = false
                                            viewModel.startCalibration()
                                        }
                                    )
                                } else {
                                    LeanAngleScreen(
                                        state = state,
                                        onOpenSettings = { showSettings = true },
                                        onCaptureUpright = viewModel::captureUpright,
                                        onStartLeftMeasurement = viewModel::startLeftMeasurement,
                                        onStartRightMeasurement = viewModel::startRightMeasurement
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
