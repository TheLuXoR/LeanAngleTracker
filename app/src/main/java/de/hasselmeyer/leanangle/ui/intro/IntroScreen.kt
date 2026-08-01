package de.hasselmeyer.leanangle.ui.intro

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.hasselmeyer.leanangle.R
import de.hasselmeyer.leanangle.ui.animation.IntroBikeLeanAnimation
import de.hasselmeyer.leanangle.ui.animation.PhoneMountAnimation
import de.hasselmeyer.leanangle.ui.theme.LeanAngleTrackerTheme

@Composable
internal fun IntroScreen(
    stage: IntroStage,
    animationReady: Boolean,
    onAction: () -> Unit,
    onTransitionFinished: () -> Unit
) {
    if (stage == IntroStage.TRANSITION_OUT) {
        LaunchedEffect(Unit) {
            onTransitionFinished()
        }
    }

    val configuration = LocalConfiguration.current
    val layoutDirection = LocalLayoutDirection.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isLoading = stage == IntroStage.LOADING
    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
    // The platform splash is centered in the full window, while this screen uses the safe area.
    val splashCenterOffsetX = (
        safeDrawingPadding.calculateEndPadding(layoutDirection) -
            safeDrawingPadding.calculateStartPadding(layoutDirection)
        ) / 2
    val splashCenterOffsetY = (
        safeDrawingPadding.calculateBottomPadding() -
            safeDrawingPadding.calculateTopPadding()
        ) / 2
    val cardOffsetX by animateDpAsState(
        targetValue = if (isLoading) splashCenterOffsetX else 0.dp,
        animationSpec = tween(durationMillis = 350),
        label = "introCardOffsetX"
    )
    val cardOffsetY by animateDpAsState(
        targetValue = if (isLoading) splashCenterOffsetY else 0.dp,
        animationSpec = tween(durationMillis = 350),
        label = "introCardOffsetY"
    )
    val screenBackgroundColor by animateColorAsState(
        targetValue = if (isLoading) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.background
        },
        animationSpec = tween(durationMillis = 350),
        label = "introScreenBackground"
    )
    val cardElevation by animateDpAsState(
        targetValue = if (isLoading) 0.dp else 12.dp,
        animationSpec = tween(durationMillis = 350),
        label = "introCardElevation"
    )
    val portraitIllustrationSize by animateDpAsState(
        targetValue = if (isLoading) 288.dp else 320.dp,
        animationSpec = tween(durationMillis = 350),
        label = "introIllustrationSize"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBackgroundColor)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .offset(x = cardOffsetX, y = cardOffsetY)
                .padding(horizontal = if (isLandscape) 48.dp else 24.dp)
                .fillMaxWidth()
                .then(if (isLandscape) Modifier.fillMaxHeight(0.9f) else Modifier.wrapContentHeight())
                .clip(RoundedCornerShape(28.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = cardElevation)
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
                            startAnimation = animationReady,
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
                    Box(Modifier.size(portraitIllustrationSize)) {
                        IntroBikeLeanAnimation(
                            modifier = Modifier.fillMaxSize(),
                            startAnimation = animationReady,
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
        enter = fadeIn(animationSpec = tween(durationMillis = 300)) +
            if (isLandscape) expandHorizontally() else expandVertically()
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
        enter = fadeIn(animationSpec = tween(durationMillis = 300)) +
            if (isLandscape) expandHorizontally() else expandVertically()
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

@Preview(widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun IntroLoadingPreview() {
    LeanAngleTrackerTheme {
        IntroScreen(
            stage = IntroStage.LOADING,
            animationReady = true,
            onAction = {},
            onTransitionFinished = {}
        )
    }
}
