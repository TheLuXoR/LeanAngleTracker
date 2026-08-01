package de.hasselmeyer.leanangle.ui.tracking

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.hasselmeyer.leanangle.R
import de.hasselmeyer.leanangle.ui.components.GpsStatsDashboard
import de.hasselmeyer.leanangle.ui.components.LeanHistoryGraph
import de.hasselmeyer.leanangle.ui.components.TachoGauge
import de.hasselmeyer.leanangle.ui.components.buttons.PauseButton
import de.hasselmeyer.leanangle.ui.components.buttons.RecordButton
import de.hasselmeyer.leanangle.ui.theme.AccentGreen
import de.hasselmeyer.leanangle.ui.theme.PrimaryOrange
import de.hasselmeyer.leanangle.ui.theme.SecondaryBlue
import kotlinx.coroutines.delay

@Composable
internal fun AppTourPagePreview(
    pageIndex: Int,
    modifier: Modifier = Modifier,
    isCompleted: Boolean = false,
    onInteractionCompleted: () -> Unit = {}
) {
    Box(
        modifier = modifier.testTag("appTourPreview$pageIndex"),
        contentAlignment = Alignment.Center
    ) {
        when (pageIndex) {
            0 -> GaugeTourPreview(
                isCompleted = isCompleted,
                onCompleted = onInteractionCompleted
            )
            1 -> RecordingTourPreview(
                isCompleted = isCompleted,
                onCompleted = onInteractionCompleted
            )
            2 -> LiveDataTourPreview()
            3 -> HistoryTourPreview(
                isCompleted = isCompleted,
                onCompleted = onInteractionCompleted
            )
            else -> ManageTourPreview()
        }
    }
}

@Composable
private fun GaugeTourPreview(
    isCompleted: Boolean,
    onCompleted: () -> Unit
) {
    var phase by remember(isCompleted) {
        mutableStateOf(
            if (isCompleted) GaugeTourPhase.RESET else GaugeTourPhase.DEMONSTRATING
        )
    }
    var currentTarget by remember(isCompleted) { mutableStateOf(0f) }
    var maxLeftTarget by remember(isCompleted) { mutableStateOf(0f) }
    var maxRightTarget by remember(isCompleted) { mutableStateOf(0f) }
    val animatedCurrent by animateFloatAsState(
        targetValue = currentTarget,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "TourCurrentLean"
    )
    val animatedMaxLeft by animateFloatAsState(
        targetValue = maxLeftTarget,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "TourMaxLeft"
    )
    val animatedMaxRight by animateFloatAsState(
        targetValue = maxRightTarget,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "TourMaxRight"
    )

    LaunchedEffect(isCompleted) {
        if (isCompleted) return@LaunchedEffect
        delay(400)
        currentTarget = 38f
        maxRightTarget = 38f
        delay(900)
        currentTarget = 0f
        delay(750)
        currentTarget = -42f
        maxLeftTarget = -42f
        delay(900)
        currentTarget = 0f
        delay(850)
        phase = GaugeTourPhase.READY_TO_RESET
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TachoGauge(
            currentDeg = animatedCurrent,
            maxLeftDeg = animatedMaxLeft,
            maxRightDeg = animatedMaxRight,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("appTourGauge"),
            onResetMaxValues = {
                if (phase == GaugeTourPhase.READY_TO_RESET) {
                    maxLeftTarget = 0f
                    maxRightTarget = 0f
                    phase = GaugeTourPhase.RESET
                    onCompleted()
                }
            }
        )
        GaugeInteractionStatus(phase = phase)
    }
}

@Composable
private fun GaugeInteractionStatus(phase: GaugeTourPhase) {
    AnimatedContent(
        targetState = phase,
        transitionSpec = {
            (fadeIn(tween(250)) + scaleIn(initialScale = 0.92f)) togetherWith
                fadeOut(tween(150))
        },
        label = "GaugeTourStatus"
    ) { currentPhase ->
        when (currentPhase) {
            GaugeTourPhase.DEMONSTRATING -> TourStatusPill(
                text = stringResource(R.string.app_tour_gauge_demo_running),
                icon = Icons.Default.PlayCircle,
                color = MaterialTheme.colorScheme.primary
            )
            GaugeTourPhase.READY_TO_RESET -> {
                val pulse = rememberInfiniteTransition(label = "GaugeLongPressHint")
                val scale by pulse.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.08f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "GaugeLongPressHintScale"
                )
                TourStatusPill(
                    text = stringResource(R.string.app_tour_gauge_long_press),
                    icon = Icons.Default.TouchApp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                )
            }
            GaugeTourPhase.RESET -> TourStatusPill(
                text = stringResource(R.string.app_tour_gauge_reset_success),
                icon = Icons.Default.CheckCircle,
                color = AccentGreen
            )
        }
    }
}

private enum class GaugeTourPhase {
    DEMONSTRATING,
    READY_TO_RESET,
    RESET
}

@Composable
private fun RecordingTourPreview(
    isCompleted: Boolean,
    onCompleted: () -> Unit
) {
    var stage by remember(isCompleted) {
        mutableStateOf(
            if (isCompleted) RecordingTourStage.SAVED else RecordingTourStage.READY
        )
    }

    LaunchedEffect(stage, isCompleted) {
        if (!isCompleted && stage == RecordingTourStage.GPS_SEARCHING) {
            delay(1_800)
            stage = RecordingTourStage.RECORDING
        }
    }

    TourPreviewSurface {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
        ) {
            RecordingTourStep(
                number = 1,
                text = stringResource(R.string.app_tour_recording_step_start),
                state = when (stage) {
                    RecordingTourStage.READY -> RecordingStepState.CURRENT
                    else -> RecordingStepState.COMPLETE
                }
            )
            RecordingTourStep(
                number = 2,
                text = stringResource(R.string.app_tour_recording_step_gps),
                state = when (stage) {
                    RecordingTourStage.READY -> RecordingStepState.UPCOMING
                    RecordingTourStage.GPS_SEARCHING -> RecordingStepState.CURRENT
                    RecordingTourStage.RECORDING,
                    RecordingTourStage.PAUSED,
                    RecordingTourStage.RESUMED,
                    RecordingTourStage.SAVED -> RecordingStepState.COMPLETE
                }
            )
            RecordingTourStep(
                number = 3,
                text = stringResource(R.string.app_tour_recording_step_pause_resume),
                state = when (stage) {
                    RecordingTourStage.READY,
                    RecordingTourStage.GPS_SEARCHING -> RecordingStepState.UPCOMING
                    RecordingTourStage.RECORDING,
                    RecordingTourStage.PAUSED -> RecordingStepState.CURRENT
                    RecordingTourStage.RESUMED,
                    RecordingTourStage.SAVED -> RecordingStepState.COMPLETE
                }
            )
            RecordingTourStep(
                number = 4,
                text = stringResource(R.string.app_tour_recording_step_stop),
                state = when (stage) {
                    RecordingTourStage.READY,
                    RecordingTourStage.GPS_SEARCHING,
                    RecordingTourStage.RECORDING,
                    RecordingTourStage.PAUSED -> RecordingStepState.UPCOMING
                    RecordingTourStage.RESUMED -> RecordingStepState.CURRENT
                    RecordingTourStage.SAVED -> RecordingStepState.COMPLETE
                }
            )
            RecordingTourStatus(stage = stage)
            RecordingTourAction(
                stage = stage,
                onStageChanged = { stage = it },
                onCompleted = onCompleted
            )
        }
    }
}

@Composable
private fun RecordingTourStatus(stage: RecordingTourStage) {
    AnimatedContent(
        targetState = stage,
        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
        label = "RecordingTourStatus"
    ) { currentStage ->
        when (currentStage) {
            RecordingTourStage.READY -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.GpsNotFixed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = stringResource(R.string.app_tour_recording_not_started),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            RecordingTourStage.GPS_SEARCHING -> Row(
                modifier = Modifier.testTag("appTourGpsSearching"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Icon(
                    Icons.Default.GpsNotFixed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.app_tour_recording_gps_searching),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            RecordingTourStage.RECORDING,
            RecordingTourStage.RESUMED -> RecordingActiveStatus()
            RecordingTourStage.PAUSED -> TourStatusPill(
                text = stringResource(R.string.app_tour_preview_paused),
                icon = Icons.Default.PauseCircle,
                color = PrimaryOrange
            )
            RecordingTourStage.SAVED -> TourStatusPill(
                text = stringResource(R.string.app_tour_recording_saved),
                icon = Icons.Default.CheckCircle,
                color = AccentGreen
            )
        }
    }
}

@Composable
private fun RecordingActiveStatus() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            Icons.Default.RadioButtonChecked,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = stringResource(R.string.app_tour_recording_active),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RecordingTourAction(
    stage: RecordingTourStage,
    onStageChanged: (RecordingTourStage) -> Unit,
    onCompleted: () -> Unit
) {
    AnimatedContent(
        targetState = stage,
        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
        label = "RecordingTourAction"
    ) { currentStage ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            when (currentStage) {
                RecordingTourStage.READY -> {
                    RecordingActionLabel(R.string.app_tour_recording_tap_start)
                    Box(Modifier.testTag("appTourRecordStart")) {
                        RecordButton(
                            onRecord = { onStageChanged(RecordingTourStage.GPS_SEARCHING) },
                            onStopRecord = {},
                            isPaused = false,
                            isRecording = false
                        )
                    }
                }
                RecordingTourStage.GPS_SEARCHING -> {
                    RecordingActionLabel(R.string.app_tour_recording_wait_for_gps)
                    Box(Modifier.testTag("appTourGpsRainbow")) {
                        RecordButton(
                            onRecord = {},
                            onStopRecord = {},
                            isPaused = false,
                            isRecording = true,
                            isWaitingForGps = true
                        )
                    }
                }
                RecordingTourStage.RECORDING -> {
                    RecordingActionLabel(R.string.app_tour_recording_tap_pause)
                    Box(Modifier.testTag("appTourPause")) {
                        PauseButton(
                            onClick = { onStageChanged(RecordingTourStage.PAUSED) },
                            isPaused = false,
                            isVisible = true
                        )
                    }
                }
                RecordingTourStage.PAUSED -> {
                    RecordingActionLabel(R.string.app_tour_recording_tap_resume)
                    Box(Modifier.testTag("appTourResume")) {
                        PauseButton(
                            onClick = { onStageChanged(RecordingTourStage.RESUMED) },
                            isPaused = true,
                            isVisible = true
                        )
                    }
                }
                RecordingTourStage.RESUMED -> {
                    RecordingActionLabel(R.string.app_tour_recording_tap_stop)
                    Box(Modifier.testTag("appTourRecordStop")) {
                        RecordButton(
                            onRecord = {},
                            onStopRecord = {
                                onStageChanged(RecordingTourStage.SAVED)
                                onCompleted()
                            },
                            isPaused = false,
                            isRecording = true
                        )
                    }
                }
                RecordingTourStage.SAVED -> Unit
            }
        }
    }
}

@Composable
private fun RecordingActionLabel(textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun RecordingTourStep(
    number: Int,
    text: String,
    state: RecordingStepState
) {
    val color by animateColorAsState(
        targetValue = when (state) {
            RecordingStepState.COMPLETE -> AccentGreen
            RecordingStepState.CURRENT -> MaterialTheme.colorScheme.primary
            RecordingStepState.UPCOMING -> MaterialTheme.colorScheme.outline
        },
        label = "RecordingStepColor"
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.16f),
            modifier = Modifier.size(30.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (state == RecordingStepState.COMPLETE) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text(
                        text = number.toString(),
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (state == RecordingStepState.CURRENT) {
                FontWeight.Bold
            } else {
                FontWeight.Normal
            },
            color = color
        )
    }
}

private enum class RecordingTourStage {
    GPS_SEARCHING,
    READY,
    RECORDING,
    PAUSED,
    RESUMED,
    SAVED
}

private enum class RecordingStepState {
    COMPLETE,
    CURRENT,
    UPCOMING
}

@Preview(widthDp = 400, heightDp = 300)
@Composable
private fun GaugeTourPreviewPreview() {
    MaterialTheme {
        GaugeTourPreview(
            isCompleted = false,
            onCompleted = {}
        )
    }
}

@Preview(widthDp = 400, heightDp = 300)
@Composable
private fun RecordingTourPreviewPreview() {
    MaterialTheme {
        RecordingTourPreview(
            isCompleted = false,
            onCompleted = {}
        )
    }
}

@Preview(widthDp = 400, heightDp = 300)
@Composable
private fun HistoryTourPreviewPreview() {
    MaterialTheme {
        HistoryTourPreview(
            isCompleted = false,
            onCompleted = {}
        )
    }
}

@Composable
private fun LiveDataTourPreview() {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val values = listOf(-5f, -18f, -32f, -20f, 4f, 22f, 37f, 25f, 8f)

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LeanHistoryGraph(
            values = values,
            selectedIndex = values.lastIndex,
            showCursorLine = false,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
        GpsStatsDashboard(
            speedKmh = 82f,
            distanceKm = 12.4f,
            elapsedTimeMs = 54 * 60 * 1_000L,
            isLandscape = isLandscape,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun HistoryTourPreview(
    isCompleted: Boolean,
    onCompleted: () -> Unit
) {
    var selectedTarget by remember {
        mutableStateOf(if (isCompleted) HistoryJumpTarget.MAX_LEAN else null)
    }
    var visitedTargets by remember {
        mutableStateOf(
            if (isCompleted) HistoryJumpTarget.entries.toSet() else emptySet()
        )
    }
    val pulseTransition = rememberInfiniteTransition(label = "HistoryJumpPulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "HistoryJumpPulseValue"
    )

    fun selectTarget(target: HistoryJumpTarget) {
        selectedTarget = target
        val updatedTargets = visitedTargets + target
        visitedTargets = updatedTargets
        if (!isCompleted && updatedTargets.size == HistoryJumpTarget.entries.size) {
            onCompleted()
        }
    }

    TourPreviewSurface {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                stringResource(R.string.app_tour_preview_sample_ride),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                    .padding(10.dp)
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val route = Path().apply {
                        moveTo(size.width * 0.08f, size.height * 0.72f)
                        cubicTo(
                            size.width * 0.28f, size.height * 0.12f,
                            size.width * 0.52f, size.height * 0.92f,
                            size.width * 0.68f, size.height * 0.38f
                        )
                        cubicTo(
                            size.width * 0.77f, size.height * 0.08f,
                            size.width * 0.9f, size.height * 0.5f,
                            size.width * 0.94f, size.height * 0.22f
                        )
                    }
                    drawPath(
                        path = route,
                        color = SecondaryBlue,
                        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawCircle(AccentGreen, radius = 7.dp.toPx(), center = Offset(size.width * 0.08f, size.height * 0.72f))
                    drawCircle(PrimaryOrange, radius = 7.dp.toPx(), center = Offset(size.width * 0.94f, size.height * 0.22f))

                    val selectedPoint = when (selectedTarget) {
                        HistoryJumpTarget.MAX_LEAN -> Offset(size.width * 0.68f, size.height * 0.38f)
                        HistoryJumpTarget.V_MAX -> Offset(size.width * 0.426f, size.height * 0.545f)
                        null -> null
                    }
                    selectedPoint?.let { point ->
                        drawCircle(
                            color = PrimaryOrange.copy(alpha = 0.35f * (1f - pulse)),
                            radius = (12f + pulse * 18f).dp.toPx(),
                            center = point,
                            style = Stroke(width = 3.dp.toPx())
                        )
                        drawCircle(
                            color = PrimaryOrange,
                            radius = 8.dp.toPx(),
                            center = point
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PreviewStat(
                    label = stringResource(R.string.app_tour_preview_distance),
                    value = "12,4 km"
                )
                PreviewStat(
                    label = stringResource(R.string.app_tour_preview_max_lean),
                    value = "42,0°",
                    isInteractive = true,
                    isSelected = selectedTarget == HistoryJumpTarget.MAX_LEAN,
                    modifier = Modifier
                        .testTag("appTourMaxLean")
                        .clickable { selectTarget(HistoryJumpTarget.MAX_LEAN) }
                )
                PreviewStat(
                    label = stringResource(R.string.app_tour_preview_v_max),
                    value = "128 km/h",
                    isInteractive = true,
                    isSelected = selectedTarget == HistoryJumpTarget.V_MAX,
                    modifier = Modifier
                        .testTag("appTourVMax")
                        .clickable { selectTarget(HistoryJumpTarget.V_MAX) }
                )
                PreviewStat(
                    label = stringResource(R.string.app_tour_preview_points),
                    value = "1.248"
                )
            }
            AnimatedContent(
                targetState = selectedTarget,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                label = "HistoryJumpHint"
            ) { target ->
                TourStatusPill(
                    text = stringResource(
                        when (target) {
                            HistoryJumpTarget.MAX_LEAN -> R.string.app_tour_history_jump_max_lean
                            HistoryJumpTarget.V_MAX -> R.string.app_tour_history_jump_v_max
                            null -> R.string.app_tour_history_tap_values
                        }
                    ),
                    icon = if (target == null) Icons.Default.TouchApp else Icons.Default.PlayCircle,
                    color = if (target == null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        AccentGreen
                    }
                )
            }
        }
    }
}

private enum class HistoryJumpTarget {
    MAX_LEAN,
    V_MAX
}

@Composable
private fun ManageTourPreview() {
    TourPreviewSurface {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(2) { index ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentGreen)
                        Text(
                            text = stringResource(R.string.app_tour_preview_selected_ride, index + 1),
                            modifier = Modifier.padding(start = 10.dp).weight(1f),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(if (index == 0) "18,2 km" else "11,7 km")
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PreviewActionLabel(Icons.AutoMirrored.Filled.MergeType, stringResource(R.string.app_tour_preview_combine))
                PreviewActionLabel(Icons.Default.Delete, stringResource(R.string.app_tour_preview_delete))
                PreviewActionLabel(Icons.Default.Settings, stringResource(R.string.app_tour_preview_settings))
            }
        }
    }
}

@Composable
private fun TourPreviewSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        content = content
    )
}

@Composable
private fun TourStatusPill(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = color.copy(alpha = 0.16f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PreviewStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isInteractive: Boolean = false,
    isSelected: Boolean = false
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "PreviewStatScale"
    )
    Surface(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        } else {
            Color.Transparent
        },
        border = if (isInteractive) {
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(
                    alpha = if (isSelected) 0.9f else 0.35f
                )
            )
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PreviewActionLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
