package com.example.leanangletracker.ui.intro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight

internal enum class IntroStage { LOADING, ATTACH_PROMPT, TRANSITION_OUT, DONE }

@Composable
internal fun IntroScreen(
    stage: IntroStage,
    onMountedConfirm: () -> Unit,
    onTransitionFinished: () -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "intro")
    val pulse by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    if (stage == IntroStage.TRANSITION_OUT) {
        LaunchedEffect(Unit) {
            delay(550)
            onTransitionFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF051524), Color(0xFF0A2A45))))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(modifier = Modifier.fillMaxWidth().alpha(if (stage == IntroStage.TRANSITION_OUT) 0.35f else 1f)) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PhoneBikeAnimation(modifier = Modifier.size(220.dp), pulse = pulse)

                when (stage) {
                    IntroStage.LOADING -> Text("Initialisiere…", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    else -> Text("Telefon am Motorrad befestigen", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }

                AnimatedVisibility(visible = stage == IntroStage.ATTACH_PROMPT) {
                    Button(onClick = onMountedConfirm, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text("Befestigt", fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneBikeAnimation(modifier: Modifier, pulse: Float) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val roadY = h * 0.78f

        drawLine(Color(0xCCFFFFFF), Offset(0f, roadY), Offset(w, roadY), strokeWidth = 8f)
        drawCircle(Color(0xFF2E3E52), radius = 24f, center = Offset(w * 0.27f, roadY - 18f), style = Stroke(8f))
        drawCircle(Color(0xFF2E3E52), radius = 24f, center = Offset(w * 0.73f, roadY - 18f), style = Stroke(8f))
        drawLine(Color(0xFF2E3E52), Offset(w * 0.27f, roadY - 18f), Offset(w * 0.54f, roadY - 70f), strokeWidth = 8f)
        drawLine(Color(0xFF2E3E52), Offset(w * 0.54f, roadY - 70f), Offset(w * 0.73f, roadY - 18f), strokeWidth = 8f)

        drawRoundRect(
            color = Color(0xFF9CD0FF).copy(alpha = pulse),
            topLeft = Offset(w * 0.42f, h * 0.24f),
            size = Size(w * 0.16f, h * 0.30f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f)
        )
    }
}
