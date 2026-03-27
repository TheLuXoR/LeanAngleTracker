package com.example.leanangletracker.ui.intro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.leanangletracker.R
import com.example.leanangletracker.ui.animation.IntroBikeLeanAnimation
import com.example.leanangletracker.ui.animation.PhoneMountAnimation
import kotlinx.coroutines.delay

@Composable
internal fun IntroScreen(
    stage: IntroStage,
    onAction: () -> Unit,
    onTransitionFinished: () -> Unit
) {
    val isTransitionOut = stage == IntroStage.TRANSITION_OUT
    val cardAlpha by animateFloatAsState(
        targetValue = if (isTransitionOut) 0f else 1f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "intro_card_alpha"
    )
    val cardScale by animateFloatAsState(
        targetValue = if (isTransitionOut) 0.5f else 1f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "intro_card_scale"
    )

    val approachProgress = remember { Animatable(if (stage == IntroStage.LOADING) 0f else 1f) }

    LaunchedEffect(stage) {
        if (stage == IntroStage.LOADING) {
            if (approachProgress.value < 0.1f) {
                approachProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 1250, easing = FastOutSlowInEasing)
                )
            }
        } else if (stage != IntroStage.TRANSITION_OUT) {
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
            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                Box(Modifier.size(380.dp)) {
                    IntroBikeLeanAnimation(
                        modifier = Modifier.fillMaxSize(),
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

                AnimatedVisibility(
                    visible = stage == IntroStage.LEGAL || stage == IntroStage.ATTACH_PROMPT,
                    enter = expandVertically()
                ) {
                    Column(
                        modifier = Modifier.height(180.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        val titleText = when (stage) {
                            IntroStage.LOADING -> ""
                            IntroStage.LEGAL -> stringResource(R.string.intro_title_legal_notice)
                            else -> stringResource(R.string.intro_title_attach_phone)
                        }

                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        when (stage) {
                            IntroStage.LEGAL -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                        .padding(horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.intro_legal_text),
                                        style = MaterialTheme.typography.bodySmall,
                                        lineHeight = 16.sp,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            IntroStage.ATTACH_PROMPT -> {
                                Text(
                                    text = stringResource(R.string.intro_attach_phone_text),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }

                            else -> {}
                        }
                    }
                }

                AnimatedVisibility(
                    visible = stage == IntroStage.LEGAL || stage == IntroStage.ATTACH_PROMPT,
                    enter = expandVertically()
                ) {
                    Box(modifier = Modifier
                        .height(60.dp)
                        .fillMaxWidth()) {
                        if (stage == IntroStage.LEGAL || stage == IntroStage.ATTACH_PROMPT) {
                            Button(
                                onClick = onAction,
                                modifier = Modifier.fillMaxSize(),
                                shape = RoundedCornerShape(16.dp),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                            ) {
                                val buttonText =
                                    if (stage == IntroStage.LEGAL) {
                                        stringResource(R.string.intro_button_understood)
                                    } else {
                                        stringResource(R.string.intro_button_attached)
                                    }
                                Text(
                                    buttonText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
