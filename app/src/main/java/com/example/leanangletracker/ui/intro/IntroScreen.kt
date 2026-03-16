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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class IntroStage { LOADING, ATTACH_PROMPT, TRANSITION_OUT, DONE }

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
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (stage == IntroStage.TRANSITION_OUT) 0f else 1f)
                .clip(RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                PhoneBikeAnimation(modifier = Modifier.size(240.dp), pulse = pulse)

                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val titleText = when (stage) {
                        IntroStage.LOADING -> "Initialisiere…"
                        else -> "Telefon am Motorrad befestigen"
                    }
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    if (stage == IntroStage.ATTACH_PROMPT) {
                        Text(
                            text = "Bitte stellen Sie sicher, dass das Telefon fest in einer Halterung sitzt.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                AnimatedVisibility(visible = stage == IntroStage.ATTACH_PROMPT) {
                    Button(
                        onClick = onMountedConfirm,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Befestigt", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneBikeAnimation(modifier: Modifier, pulse: Float) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val roadY = h * 0.85f

        // Road
        drawLine(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.3f), Color.Transparent)),
            start = Offset(0f, roadY),
            end = Offset(w, roadY),
            strokeWidth = 4f
        )

        // Bike simple representation
        val bikeColor = secondary.copy(alpha = 0.8f)
        drawCircle(bikeColor, radius = 20f, center = Offset(w * 0.3f, roadY - 20f), style = Stroke(6f))
        drawCircle(bikeColor, radius = 20f, center = Offset(w * 0.7f, roadY - 20f), style = Stroke(6f))
        drawLine(bikeColor, Offset(w * 0.3f, roadY - 20f), Offset(w * 0.5f, roadY - 60f), strokeWidth = 6f)
        drawLine(bikeColor, Offset(w * 0.5f, roadY - 60f), Offset(w * 0.7f, roadY - 20f), strokeWidth = 6f)

        // Phone with pulse
        val phoneW = w * 0.18f
        val phoneH = h * 0.32f
        val phoneX = w * 0.41f
        val phoneY = h * 0.25f

        drawRoundRect(
            color = primary.copy(alpha = pulse),
            topLeft = Offset(phoneX, phoneY),
            size = Size(phoneW, phoneH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx(), 12.dp.toPx())
        )
        
        drawRoundRect(
            color = primary,
            topLeft = Offset(phoneX, phoneY),
            size = Size(phoneW, phoneH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx(), 12.dp.toPx()),
            style = Stroke(width = 3.dp.toPx())
        )
    }
}
