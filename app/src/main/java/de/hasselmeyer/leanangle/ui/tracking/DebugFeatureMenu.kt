package de.hasselmeyer.leanangle.ui.tracking

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.hasselmeyer.leanangle.R
import de.hasselmeyer.leanangle.SensorSamplingRate
import de.hasselmeyer.leanangle.ui.theme.LeanAngleTrackerTheme

@Composable
internal fun DebugFeatureMenu(
    sensorSamplingRate: SensorSamplingRate,
    onShowAutoResumeIndicator: () -> Unit,
    onSetSensorSamplingRate: (SensorSamplingRate) -> Unit,
    cacheActionsEnabled: Boolean = true,
    onClearEntitlementCache: () -> Unit = {},
    onExpireEntitlementCache: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    var showSamplingRateDialog by remember { mutableStateOf(false) }
    var pendingCacheAction by remember { mutableStateOf<DebugCacheAction?>(null) }

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
            DropdownMenuItem(
                text = {
                    Column {
                        Text(stringResource(R.string.debug_sensor_sampling_title))
                        Text(
                            text = sensorSamplingRate.label(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Speed, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    showSamplingRateDialog = true
                }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.debug_entitlement_cache_clear)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null)
                },
                enabled = cacheActionsEnabled,
                onClick = {
                    expanded = false
                    pendingCacheAction = DebugCacheAction.CLEAR
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.debug_entitlement_cache_expire)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.AccessTime, contentDescription = null)
                },
                enabled = cacheActionsEnabled,
                onClick = {
                    expanded = false
                    pendingCacheAction = DebugCacheAction.EXPIRE
                }
            )
        }
    }

    if (showSamplingRateDialog) {
        SensorSamplingRateDialog(
            selectedRate = sensorSamplingRate,
            onSelectRate = onSetSensorSamplingRate,
            onDismiss = { showSamplingRateDialog = false }
        )
    }

    pendingCacheAction?.let { action ->
        DebugCacheActionDialog(
            action = action,
            onConfirm = {
                pendingCacheAction = null
                when (action) {
                    DebugCacheAction.CLEAR -> onClearEntitlementCache()
                    DebugCacheAction.EXPIRE -> onExpireEntitlementCache()
                }
            },
            onDismiss = { pendingCacheAction = null }
        )
    }
}

private enum class DebugCacheAction {
    CLEAR,
    EXPIRE
}

@Composable
private fun DebugCacheActionDialog(
    action: DebugCacheAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = when (action) {
        DebugCacheAction.CLEAR -> R.string.debug_entitlement_cache_clear_title
        DebugCacheAction.EXPIRE -> R.string.debug_entitlement_cache_expire_title
    }
    val body = when (action) {
        DebugCacheAction.CLEAR -> R.string.debug_entitlement_cache_clear_body
        DebugCacheAction.EXPIRE -> R.string.debug_entitlement_cache_expire_body
    }
    val confirmation = when (action) {
        DebugCacheAction.CLEAR -> R.string.debug_entitlement_cache_clear_confirm
        DebugCacheAction.EXPIRE -> R.string.debug_entitlement_cache_expire_confirm
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(confirmation))
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
private fun SensorSamplingRateDialog(
    selectedRate: SensorSamplingRate,
    onSelectRate: (SensorSamplingRate) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.debug_sensor_sampling_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.debug_sensor_sampling_body),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                SensorSamplingRate.entries.forEach { rate ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = rate == selectedRate,
                                onClick = { onSelectRate(rate) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = rate == selectedRate,
                            onClick = null
                        )
                        Text(
                            text = rate.label(),
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

@Composable
private fun SensorSamplingRate.label(): String {
    return stringResource(
        when (this) {
            SensorSamplingRate.MEDIUM -> R.string.debug_sensor_sampling_medium
            SensorSamplingRate.HIGHEST -> R.string.debug_sensor_sampling_highest
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun DebugFeatureMenuPreview() {
    LeanAngleTrackerTheme {
        DebugFeatureMenu(
            sensorSamplingRate = SensorSamplingRate.MEDIUM,
            onShowAutoResumeIndicator = {},
            onSetSensorSamplingRate = {},
            onClearEntitlementCache = {},
            onExpireEntitlementCache = {}
        )
    }
}
