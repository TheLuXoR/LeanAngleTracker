package com.example.leanangletracker.ui.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.leanangletracker.R

@Composable
internal fun TrackingPermissionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val maxContentHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.48f)
        .coerceIn(120.dp, 320.dp)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tracking_permission_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxContentHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PermissionReason(
                    icon = Icons.Default.LocationOn,
                    title = stringResource(R.string.tracking_permission_location_title),
                    body = stringResource(R.string.tracking_permission_location_body)
                )
                PermissionReason(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.tracking_permission_notification_title),
                    body = stringResource(R.string.tracking_permission_notification_body)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.tracking_permission_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.tracking_permission_cancel))
            }
        }
    )
}

@Composable
private fun PermissionReason(icon: ImageVector, title: String, body: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
