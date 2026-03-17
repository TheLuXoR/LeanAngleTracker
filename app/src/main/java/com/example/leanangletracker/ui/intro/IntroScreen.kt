package com.example.leanangletracker.ui.intro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.leanangletracker.CalibrationStep
import com.example.leanangletracker.ui.animation.BikeLeanAnimation
import com.example.leanangletracker.ui.animation.PhoneMountAnimation
import kotlinx.coroutines.delay

@Composable
internal fun IntroScreen(
    stage: IntroStage,
    onMountedConfirm: () -> Unit,
    onTransitionFinished: () -> Unit
) {
    val isTransitionOut = stage == IntroStage.TRANSITION_OUT
    val cardAlpha by animateFloatAsState(
        targetValue = if (isTransitionOut) 0f else 1f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "intro_card_alpha"
    )
    val cardScale by animateFloatAsState(
        targetValue = if (isTransitionOut) 0.97f else 1f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "intro_card_scale"
    )

    // Approach animation state. 
    // We remember the Animatable so it survives recompositions when the stage changes.
    // The initial value depends on whether we are starting fresh (LOADING) or resuming.
    val approachProgress = remember { Animatable(if (stage == IntroStage.LOADING) 0f else 1f) }
    
    LaunchedEffect(stage) {
        if (stage == IntroStage.LOADING) {
            // Only animate if we are not already at the end
            if (approachProgress.value < 0.1f) {
                approachProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 1250, easing = FastOutSlowInEasing)
                )
            }
        } else if (stage != IntroStage.TRANSITION_OUT) {
            // Ensure it's at the end for ATTACH_PROMPT
            approachProgress.snapTo(1f)
        }
    }

    if (stage == IntroStage.TRANSITION_OUT) {
        LaunchedEffect(Unit) {
            delay(450)
            onTransitionFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .scale(cardScale)
                .alpha(cardAlpha)
                .clip(RoundedCornerShape(28.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Fixed size Box for the animation ensures it stays "pixel perfect" 
                // in the same spot during stage transitions.
                Box(Modifier.size(380.dp)){
                    BikeLeanAnimation(
                        modifier = Modifier.fillMaxSize(), 
                        step = CalibrationStep.UPRIGHT, 
                        approachProgress = approachProgress.value
                    )
                    if (stage == IntroStage.ATTACH_PROMPT) {
                        PhoneMountAnimation(
                            modifier = Modifier
                                .width(90.dp)
                                .height(90.dp)
                                .align(Alignment.Center)
                                .padding(start = 35.dp, bottom = 10.dp)
                        )
                    }
                }

                // Title and description with fixed height or top-alignment to prevent layout shifts
                Column(
                    modifier = Modifier.height(140.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, 
                    verticalArrangement = Arrangement.Top
                ) {
                    val titleText = if (stage == IntroStage.LOADING) "Initialisiere…" else "Telefon montieren"
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    if (stage == IntroStage.ATTACH_PROMPT) {
                        Text(
                            modifier = Modifier.padding(top = 12.dp),
                            text = "Smartphone sicher in einer Halterung am Motorrad befestigen.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                // Placeholder for button to keep layout stable
                Box(modifier = Modifier.height(60.dp).fillMaxWidth()) {
                    if (stage == IntroStage.ATTACH_PROMPT) {
                        Button(
                            onClick = onMountedConfirm,
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(16.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                        ) {
                            Text("Befestigt", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
