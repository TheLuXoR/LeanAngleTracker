package com.example.leanangletracker.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.leanangletracker.R
import com.example.leanangletracker.SettingsUiState
import com.example.leanangletracker.ui.theme.AccentGreen
import com.example.leanangletracker.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onToggleInvertLean: (Boolean) -> Unit,
    onToggleGyroFusion: (Boolean) -> Unit,
    onToggleGpsTracking: (Boolean) -> Unit,
    onSetHistoryWindow: (Int) -> Unit,
    onSetRecorderIntervalMs: (Int) -> Unit,
    onResetExtrema: () -> Unit,
    onStartCalibration: () -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var showInfo by rememberSaveable { mutableStateOf(false) }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text(stringResource(R.string.dialog_ok)) } },
            title = { Text(stringResource(R.string.settings_dialog_info_title)) },
            text = { Text(stringResource(R.string.settings_dialog_info_text)) }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showInfo = true }) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.action_info))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SettingsGroup(title = stringResource(R.string.settings_group_sensors_tracking)) {
                SettingsSwitchItem(
                    title = stringResource(R.string.settings_invert_lean_title),
                    subtitle = stringResource(R.string.settings_invert_lean_subtitle),
                    checked = state.invertLeanAngle,
                    onCheckedChange = onToggleInvertLean
                )
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                
                SettingsSwitchItem(
                    title = stringResource(R.string.settings_gyro_fusion_title),
                    subtitle = if (state.gyroscopeAvailable) stringResource(R.string.settings_gyro_fusion_subtitle_available) else stringResource(R.string.settings_gyro_fusion_subtitle_unavailable),
                    checked = state.useGyroFusion,
                    onCheckedChange = onToggleGyroFusion,
                    enabled = state.gyroscopeAvailable
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

                SettingsSwitchItem(
                    title = stringResource(R.string.settings_gps_tracking_title),
                    subtitle = if (state.locationPermissionGranted) stringResource(R.string.settings_gps_tracking_subtitle_granted) else stringResource(R.string.settings_gps_tracking_subtitle_missing),
                    checked = state.gpsTrackingEnabled,
                    onCheckedChange = onToggleGpsTracking
                )
            }

            SettingsGroup(title = stringResource(R.string.settings_group_visuals)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_history_window_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.settings_history_window_subtitle), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onSetHistoryWindow(state.historyWindowSeconds - 5) },
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_decrease), modifier = Modifier.size(18.dp))
                        }
                        
                        Text(
                            text = stringResource(R.string.settings_value_seconds, state.historyWindowSeconds),
                            modifier = Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        IconButton(
                            onClick = { onSetHistoryWindow(state.historyWindowSeconds + 5) },
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_increase), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            SettingsGroup(title = stringResource(R.string.settings_group_recording)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_recorder_tick_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.settings_recorder_tick_subtitle), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onSetRecorderIntervalMs(state.recorderIntervalMs - 50) },
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_decrease), modifier = Modifier.size(18.dp))
                        }

                        Text(
                            text = stringResource(R.string.settings_value_milliseconds, state.recorderIntervalMs),
                            modifier = Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(
                            onClick = { onSetRecorderIntervalMs(state.recorderIntervalMs + 50) },
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_increase), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(
                    onClick = onResetExtrema,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text(stringResource(R.string.settings_reset_max_values), style = MaterialTheme.typography.titleMedium)
                }

                OutlinedButton(
                    onClick = onStartCalibration,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(width = 2.dp)
                ) {
                    Text(stringResource(R.string.settings_recalibrate_device), style = MaterialTheme.typography.titleMedium)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) Color.Unspecified else TextSecondary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AccentGreen,
                checkedTrackColor = AccentGreen.copy(alpha = 0.3f)
            )
        )
    }
}
