package com.example.leanangletracker.ui.tracking

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.leanangletracker.R
import com.example.leanangletracker.ui.theme.LeanAngleTrackerTheme

@Composable
internal fun DebugFeatureMenu(
    onShowAutoResumeIndicator: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = stringResource(R.string.debug_features_open),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.debug_feature_auto_resume_indicator)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onShowAutoResumeIndicator()
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DebugFeatureMenuPreview() {
    LeanAngleTrackerTheme {
        DebugFeatureMenu(onShowAutoResumeIndicator = {})
    }
}
