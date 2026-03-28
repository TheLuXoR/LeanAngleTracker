package com.example.leanangletracker.ui.components.buttons

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp


@Composable
fun PauseButton(
    onClick: () -> Unit,
    isPaused: Boolean = false,
    isVisible: Boolean = false,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.5f) + expandHorizontally(),
        exit = fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 0.5f) + shrinkHorizontally()
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .padding(end = 8.dp) // Versatz zu den anderen Buttons
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Icon(
                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = if (isPaused) "Resume" else "Pause",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
