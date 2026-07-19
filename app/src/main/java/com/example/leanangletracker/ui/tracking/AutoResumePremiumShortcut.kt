package com.example.leanangletracker.ui.tracking

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.leanangletracker.R
import com.example.leanangletracker.ui.theme.LeanAngleTrackerTheme

internal const val AUTO_RESUME_SHORTCUT_TAG = "auto_resume_shortcut"
internal const val AUTO_RESUME_COUNTDOWN_TAG = "auto_resume_countdown"
internal const val AUTO_RESUME_ACTION_TAG = "auto_resume_action"
internal const val AUTO_RESUME_CLOSE_TAG = "auto_resume_close"
private const val AUTO_RESUME_SHORTCUT_DURATION_MS = 10_000

@Composable
internal fun AutoResumePremiumShortcut(
    displayRequestId: Int,
    onOpenPremium: () -> Unit,
    onDismiss: () -> Unit = {}
) {
    var isShown by remember { mutableStateOf(false) }
    var isPinned by remember { mutableStateOf(false) }
    val remainingProgress = remember { Animatable(1f) }

    LaunchedEffect(displayRequestId) {
        if (displayRequestId <= 0) {
            isShown = false
            isPinned = false
            return@LaunchedEffect
        }

        isShown = true
        isPinned = false
        remainingProgress.snapTo(1f)
        remainingProgress.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = AUTO_RESUME_SHORTCUT_DURATION_MS,
                easing = LinearEasing
            )
        )

        if (!isPinned) {
            isShown = false
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = isShown,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AUTO_RESUME_SHORTCUT_TAG),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Column {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = !isPinned) { isPinned = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text(
                                text = stringResource(R.string.auto_resume_premium_shortcut_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.auto_resume_premium_shortcut_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    TextButton(
                        onClick = onOpenPremium,
                        modifier = Modifier.testTag(AUTO_RESUME_ACTION_TAG)
                    ) {
                        Text(stringResource(R.string.auto_resume_premium_shortcut_action))
                    }
                    if (isPinned) {
                        IconButton(
                            onClick = {
                                isShown = false
                                onDismiss()
                            },
                            modifier = Modifier.testTag(AUTO_RESUME_CLOSE_TAG)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.auto_resume_indicator_close)
                            )
                        }
                    }
                }

                if (!isPinned) {
                    LinearProgressIndicator(
                        progress = { remainingProgress.value },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .testTag(AUTO_RESUME_COUNTDOWN_TAG),
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.45f),
                        trackColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun AutoResumePremiumShortcutPreview() {
    LeanAngleTrackerTheme {
        AutoResumePremiumShortcut(
            displayRequestId = 1,
            onOpenPremium = {}
        )
    }
}
