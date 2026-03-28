package com.example.leanangletracker.ui.components.buttons

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

@Composable
fun HistoryButton(
    onOpenHistory: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
){
    if(enabled) {
        IconButton(
            onClick = onOpenHistory,
            enabled = enabled,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.List,
                contentDescription = "History",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    } else {
        IconButton(
            onClick = onOpenHistory,
            enabled = enabled,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
        ) {
            Icon(
                Icons.AutoMirrored.Filled.List,
                contentDescription = "History",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
        }
    }

}