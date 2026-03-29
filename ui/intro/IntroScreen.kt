package com.example.leanangletracker.ui.intro

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.leanangletracker.R
import com.example.leanangletracker.ui.animation.IntroBikeLeanAnimation
import com.example.leanangletracker.ui.animation.PhoneMountAnimation

@Composable
internal fun IntroScreen(
    stage: IntroStage,
    onAction: () -> Unit
) {
    val approachProgress = remember { Animatable(if (stage == IntroStage.LOADING) 0f else 1f) }

    LaunchedEffect(stage) {
        if (stage == IntroStage.LOADING) {
            if (approachProgress.value < 0.1f) {
                approachProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 1250, easing = FastOutSlowInEasing)
                )
            }
        } else {
            approachProgress.snapTo(1f)
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(horizontal = if (isLandscape) 48.dp else 24.dp)
                .fillMaxWidth()
                .then(if (isLandscape) Modifier.fillMaxHeight(0.9f) else Modifier.wrapContentHeight())
                .clip(RoundedCornerShape(28.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            if (isLandscape) {
                Row(
                    modifier = Modifier.padding(24.dp).fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Box(Modifier.weight(1.2f).fillMaxHeight()) {
                        IntroBikeLeanAnimation(
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (stage == IntroStage.ATTACH_PROMPT) {
                            PhoneMountAnimation(
                                modifier = Modifier
                                    .size(80.dp)
                                    .align(Alignment.Center)
                                    .padding(start = 25.dp, bottom = 10.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IntroContent(stage = stage, onAction = onAction, isLandscape = true)
                    }
                }
            } else {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    Box(Modifier.size(320.dp)) {
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
                    IntroContent(stage = stage, onAction = onAction, isLandscape = false)
                }
            }
        }
    }
}

@Composable
private fun IntroContent(stage: IntroStage, onAction: () -> Unit, isLandscape: Boolean) {
    AnimatedVisibility(
        visible = stage == IntroStage.LEGAL || stage == IntroStage.ATTACH_PROMPT,
        enter = if (isLandscape) expandHorizontally() else expandVertically()
    ) {
        Column(
            modifier = Modifier.then(if (isLandscape) Modifier.wrapContentHeight() else Modifier.height(180.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            val titleText = when (stage) {
                IntroStage.LOADING -> ""
                IntroStage.LEGAL -> stringResource(R.string.intro_legal_title)
                else -> stringResource(R.string.intro_attach_title)
            }

            Text(
                text = titleText,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(8.dp))

            when (stage) {
                IntroStage.LEGAL -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isLandscape) Modifier.heightIn(max = 120.dp) else Modifier)
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
                        text = stringResource(R.string.intro_attach_text),
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
        enter = if (isLandscape) expandHorizontally() else expandVertically()
    ) {
        Box(modifier = Modifier
            .height(56.dp)
            .fillMaxWidth()) {
            if (stage == IntroStage.LEGAL || stage == IntroStage.ATTACH_PROMPT) {
                Button(
                    onClick = onAction,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    val buttonText =
                        if (stage == IntroStage.LEGAL) stringResource(R.string.intro_button_understood) else stringResource(R.string.intro_button_attached)
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
