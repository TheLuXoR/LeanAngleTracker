package com.example.leanangletracker.ui.components.buttons

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.leanangletracker.ui.components.AnimatedBorderBox
import com.example.leanangletracker.ui.theme.ErrorRed
import kotlinx.coroutines.delay

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
    
    // Wave control logic
    var showWave by remember { mutableStateOf(false) }
    LaunchedEffect(isRecording, isWaitingForGps) {
        if (isRecording && isWaitingForGps) {
            showWave = true
        } else {
            showWave = false
        }
    }

    // Rainbow effect for GPS searching
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
        if (recording) 8.dp else 24.dp
    }
    val shape = RoundedCornerShape(cornerRadius)

    val containerColor by transition.animateColor(label = "containerColor") { recording ->
        if (recording) MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        else MaterialTheme.colorScheme.primaryContainer
    }

    val buttonSize = 40.dp

    val elevation by transition.animateDp(label = "elevation") { recording ->
        if (recording) 8.dp else 2.dp
    }

    Box(
        modifier = modifier.size(buttonSize),
        contentAlignment = Alignment.Center
    ) {
        // Energetic Shockwave Pulse
        if (showWave && !isPaused) {
            val waveTransition = rememberInfiniteTransition(label = "shockwave")
            val waveScale by waveTransition.animateFloat(
                initialValue = 1f,
                targetValue = 2.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "waveScale"
            )
            val waveAlpha by waveTransition.animateFloat(
                initialValue = 0.7f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "waveAlpha"
            )
            Box(
                Modifier
                    .size(buttonSize)
                    .graphicsLayer {
                        scaleX = waveScale
                        scaleY = waveScale
                        alpha = waveAlpha
                        clip = false
                    }
                    .border(1.5.dp, activeColor.copy(alpha = 0.6f), shape)
            )
        }

        AnimatedBorderBox(
            modifier = Modifier
                .size(buttonSize)
                .shadow(
                    elevation = elevation,
                    shape = shape,
                    ambientColor = if (isRecording) activeColor else Color.Black,
                    spotColor = if (isRecording) activeColor else Color.Black
                ),
            shape = shape,
            borderWidth = 2.dp,
            borderColor = if (isRecording) activeColor else Color.Transparent,
            isAnimating = isRecording && !isPaused,
            animationDuration = if (isWaitingForGps) 1500 else 3000
        ) {
            Surface(
                onClick = { if (isRecording) onStopRecord() else onRecord() },
                shape = shape,
                color = containerColor,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    transition.AnimatedContent(
                        transitionSpec = {
                            if (targetState) {
                                (fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0f))
                                    .togetherWith(fadeOut(tween(240)) + scaleOut(tween(240), targetScale = 1.5f))
                            } else {
                                (fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 1.5f))
                                    .togetherWith(fadeOut(tween(240)) + scaleOut(tween(240), targetScale = 0f))
                            }
                        },
                    ) { recording ->
                        if (!recording) {
                            Icon(
                                Icons.Default.FiberManualRecord,
                                contentDescription = "Start Recording",
                                tint = ErrorRed,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Text(
                                modifier = Modifier.padding(5.dp),
                                text = if (isWaitingForGps) "WAITING FOR GPS" else "STOP RECORDING",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = activeColor,
                                fontSize = 9.sp,
                                letterSpacing = 0.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
