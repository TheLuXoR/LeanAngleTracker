package com.example.leanangletracker.ui.components.buttons

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.unit.dp
import com.example.leanangletracker.ui.components.AnimatedBorderBox
import com.example.leanangletracker.ui.theme.ErrorRed

@Composable
fun RecordButton(
    onRecord: () -> Unit,
    onStopRecord: () -> Unit,
    isPaused: Boolean,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isRecording) {
        FilledTonalIconButton(
            onClick = onRecord,
            modifier = modifier,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Icon(
                Icons.Default.FiberManualRecord,
                contentDescription = "Start Recording",
                tint = ErrorRed,
                modifier = Modifier.size(24.dp)
            )
        }
    } else {
        AnimatedBorderBox(
            modifier = modifier.size(48.dp),
            shape = RoundedCornerShape(10.dp),
            borderWidth = 3.dp,
            borderColor = MaterialTheme.colorScheme.primary,
            isAnimating = !isPaused
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha"
            )
            IconButton(
                onClick = onStopRecord,
                modifier = Modifier
                    .padding(4.dp)
                    .background(if (isPaused) ErrorRed.copy(alpha = 0.1f) else ErrorRed.copy(alpha = 0.15f * alpha))
            ) {
                Text(
                    modifier = Modifier.height(24.dp),
                    text = "Stop",
                    color = ErrorRed,
                )
            }
        }
    }
}
