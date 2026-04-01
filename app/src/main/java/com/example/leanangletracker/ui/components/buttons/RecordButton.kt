package com.example.leanangletracker.ui.components.buttons

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.leanangletracker.ui.components.AnimatedBorderBox
import com.example.leanangletracker.ui.theme.ErrorRed
import com.example.leanangletracker.ui.theme.TextPrimary

@Composable
fun RecordButton(
    onRecord: () -> Unit,
    onStopRecord: () -> Unit,
    isPaused: Boolean,
    isRecording: Boolean,
    isWaitingForGps: Boolean = false,
    modifier: Modifier = Modifier
) {
    val transition = updateTransition(targetState = isRecording, label = "RecordButtonState")
    
    var showWave by remember { mutableStateOf(false) }
    LaunchedEffect(isRecording, isWaitingForGps) {
        showWave = isRecording && isWaitingForGps
    }

    val infiniteTransition = rememberInfiniteTransition(label = "rainbow_button")
    val rainbowPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rainbowPhase"
    )
    val activeColor = if (isRecording && isWaitingForGps) {
        Color.hsv(rainbowPhase, 0.7f, 0.9f)
    } else {
        ErrorRed
    }

    val cornerRadius by transition.animateDp(label = "cornerRadius") { recording ->
        if (recording) 12.dp else 24.dp
    }
    val shape = RoundedCornerShape(cornerRadius)

    val containerColor by animateColorAsState(
        targetValue = when {
            isRecording && !isWaitingForGps -> ErrorRed
            isRecording -> MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        label = "containerColor"
    )

    val elevation by transition.animateDp(label = "elevation") { recording ->
        if (recording) 8.dp else 2.dp
    }

    val horizontalPadding by transition.animateDp(
        label = "horizontalPadding",
        transitionSpec = { tween(400, easing = FastOutSlowInEasing) }
    ) { recording ->
        if (recording) 12.dp else 0.dp
    }

    Box(
        modifier = modifier
            .height(40.dp)
            .widthIn(min = 40.dp)
            .animateContentSize(animationSpec = tween(400, easing = FastOutSlowInEasing)),
        contentAlignment = Alignment.Center
    ) {
        // Pulse Effect
        if (showWave && !isPaused) {
            val waveTransition = rememberInfiniteTransition(label = "shockwave")
            val waveScale by waveTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "waveScale"
            )
            val waveAlpha by waveTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "waveAlpha"
            )
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = waveScale
                        scaleY = waveScale
                        alpha = waveAlpha
                    }
                    .border(1.5.dp, activeColor.copy(alpha = 0.4f), shape)
            )
        }

        // Main Button Surface
        AnimatedBorderBox(
            modifier = Modifier
                .shadow(elevation = elevation, shape = shape, spotColor = if (isRecording && !isWaitingForGps) ErrorRed else activeColor),
            shape = shape,
            borderWidth = 2.dp,
            borderColor = if (isRecording && isWaitingForGps) activeColor else Color.Transparent,
            isAnimating = isRecording && !isPaused && isWaitingForGps
        ) {
            Surface(
                onClick = { if (isRecording) onStopRecord() else onRecord() },
                shape = shape,
                color = containerColor,
                modifier = Modifier.height(40.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = horizontalPadding)
                        .widthIn(min = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    transition.AnimatedContent(
                        transitionSpec = {
                            (fadeIn(tween(300)) togetherWith fadeOut(tween(300)))
                                .using(SizeTransform(clip = false) { _, _ -> snap() })
                        },
                        contentAlignment = Alignment.Center
                    ) { recording ->
                        if (!recording) {
                            Icon(
                                Icons.Default.FiberManualRecord,
                                contentDescription = "Start",
                                tint = ErrorRed,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (!isWaitingForGps) {
                                    Icon(
                                        Icons.Default.Stop,
                                        contentDescription = "Stop",
                                        tint = if (isRecording && !isWaitingForGps) TextPrimary else activeColor,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Text(
                                    text = if (isWaitingForGps) "WAIT GPS" else "RECORDING",
                                    fontWeight = FontWeight.Black,
                                    color = if (isRecording && !isWaitingForGps) TextPrimary else activeColor,
                                    fontSize = 9.sp,
                                    textAlign = TextAlign.Center,
                                    softWrap = false,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
