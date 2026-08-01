package com.example.leanangletracker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.leanangletracker.R
import com.example.leanangletracker.SettingsUiState
import com.example.leanangletracker.ui.components.Minus
import com.example.leanangletracker.ui.legal.LegalDocument
import com.example.leanangletracker.ui.theme.AccentGreen
import com.example.leanangletracker.ui.theme.LeanAngleTrackerTheme

private const val HISTORY_WINDOW_MIN_SECONDS = 5
private const val HISTORY_WINDOW_MAX_SECONDS = 120
private const val HISTORY_WINDOW_STEP_SECONDS = 5
private const val RECORDER_INTERVAL_MIN_MS = 50
private const val RECORDER_INTERVAL_MAX_MS = 1_000
private const val RECORDER_INTERVAL_STEP_MS = 50

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onSetHistoryWindow: (Int) -> Unit,
    onSetRecorderIntervalMs: (Int) -> Unit,
    onResetGaugeExtrema: () -> Unit,
    onStartAppTour: () -> Unit,
    onStartCalibration: () -> Unit,
    onToggleAutoResume: (Boolean) -> Unit,
    onOpenPremium: () -> Unit,
    onToggleAutoPause: (Boolean) -> Unit,
    privacyOptionsRequired: Boolean = false,
    onOpenPrivacyOptions: () -> Unit = {},
    onOpenLegalDocument: (LegalDocument) -> Unit = {}
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenPremium) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = stringResource(R.string.settings_open_premium),
                            tint = MaterialTheme.colorScheme.primary
                        )
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
            SettingsGroup(title = stringResource(R.string.settings_group_sensors)) {
                SettingsSwitchItem(
                    title = stringResource(R.string.settings_auto_pause_title),
                    subtitle = stringResource(R.string.settings_auto_pause_subtitle),
                    checked = state.hasAutomationAccess && state.autoPauseEnabled,
                    onCheckedChange = onToggleAutoPause
                )
                SettingsDivider()
                SettingsSwitchItem(
                    title = stringResource(R.string.settings_auto_resume_title),
                    subtitle = stringResource(R.string.settings_auto_resume_subtitle),
                    checked = state.hasAutomationAccess && state.autoResumeEnabled,
                    onCheckedChange = onToggleAutoResume
                )
                SettingsDivider()
                SettingsSwitchItem(
                    title = stringResource(R.string.settings_no_ads_title),
                    subtitle = stringResource(R.string.settings_no_ads_subtitle),
                    checked = state.isPremiumSubscribed,
                    onCheckedChange = if (state.isPremiumSubscribed) null else {
                        { enabled -> if (enabled) onOpenPremium() }
                    }
                )
                SettingsDivider()
                SettingsStepperItem(
                    title = stringResource(R.string.settings_recorder_tick_title),
                    subtitle = stringResource(R.string.settings_recorder_tick_subtitle),
                    valueLabel = stringResource(
                        R.string.settings_value_milliseconds,
                        state.recorderIntervalMs
                    ),
                    canDecrease = state.recorderIntervalMs > RECORDER_INTERVAL_MIN_MS,
                    canIncrease = state.recorderIntervalMs < RECORDER_INTERVAL_MAX_MS,
                    onDecrease = {
                        onSetRecorderIntervalMs(
                            state.recorderIntervalMs - RECORDER_INTERVAL_STEP_MS
                        )
                    },
                    onIncrease = {
                        onSetRecorderIntervalMs(
                            state.recorderIntervalMs + RECORDER_INTERVAL_STEP_MS
                        )
                    }
                )
            }

            SettingsGroup(title = stringResource(R.string.settings_group_visuals)) {
                SettingsStepperItem(
                    title = stringResource(R.string.settings_history_window_title),
                    subtitle = stringResource(R.string.settings_history_window_subtitle),
                    valueLabel = stringResource(
                        R.string.settings_value_seconds,
                        state.historyWindowSeconds
                    ),
                    canDecrease = state.historyWindowSeconds > HISTORY_WINDOW_MIN_SECONDS,
                    canIncrease = state.historyWindowSeconds < HISTORY_WINDOW_MAX_SECONDS,
                    onDecrease = {
                        onSetHistoryWindow(
                            state.historyWindowSeconds - HISTORY_WINDOW_STEP_SECONDS
                        )
                    },
                    onIncrease = {
                        onSetHistoryWindow(
                            state.historyWindowSeconds + HISTORY_WINDOW_STEP_SECONDS
                        )
                    }
                )
            }

            SettingsGroup(title = stringResource(R.string.settings_group_reset)) {
                SettingsActionItem(
                    title = stringResource(R.string.settings_reset_gauge_max_values),
                    subtitle = stringResource(R.string.settings_reset_gauge_hint),
                    onClick = onResetGaugeExtrema
                )
                SettingsDivider()
                SettingsActionItem(
                    title = stringResource(R.string.settings_recalibrate_device),
                    subtitle = stringResource(R.string.settings_recalibrate_hint),
                    onClick = onStartCalibration
                )
                SettingsDivider()
                SettingsActionItem(
                    title = stringResource(R.string.settings_start_app_tour),
                    subtitle = stringResource(R.string.settings_start_app_tour_hint),
                    onClick = onStartAppTour
                )
            }

            SettingsGroup(title = stringResource(R.string.settings_group_legal)) {
                if (privacyOptionsRequired) {
                    SettingsActionItem(
                        title = stringResource(R.string.settings_privacy_choices),
                        subtitle = stringResource(R.string.settings_privacy_choices_available),
                        onClick = onOpenPrivacyOptions
                    )
                    SettingsDivider()
                }
                SettingsActionItem(
                    title = stringResource(R.string.legal_privacy_title),
                    subtitle = stringResource(R.string.settings_privacy_policy_hint),
                    onClick = { onOpenLegalDocument(LegalDocument.PRIVACY) }
                )
                SettingsDivider()
                SettingsActionItem(
                    title = stringResource(R.string.legal_terms_title),
                    subtitle = stringResource(R.string.settings_terms_hint),
                    onClick = { onOpenLegalDocument(LegalDocument.TERMS) }
                )
                SettingsDivider()
                SettingsActionItem(
                    title = stringResource(R.string.legal_notice_title),
                    subtitle = stringResource(R.string.settings_legal_notice_hint),
                    onClick = { onOpenLegalDocument(LegalDocument.LEGAL_NOTICE) }
                )
                SettingsDivider()
                SettingsActionItem(
                    title = stringResource(R.string.legal_safety_title),
                    subtitle = stringResource(R.string.settings_safety_hint),
                    onClick = { onOpenLegalDocument(LegalDocument.SAFETY) }
                )
                SettingsDivider()
                SettingsActionItem(
                    title = stringResource(R.string.settings_open_source_licenses),
                    subtitle = stringResource(R.string.settings_open_source_licenses_hint),
                    onClick = { onOpenLegalDocument(LegalDocument.OPEN_SOURCE) }
                )
                SettingsDivider()
                SettingsActionItem(
                    title = stringResource(R.string.legal_graphics_title),
                    subtitle = stringResource(R.string.settings_graphics_hint),
                    onClick = { onOpenLegalDocument(LegalDocument.GRAPHICS) }
                )
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
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?
) {
    val interactionModifier = if (onCheckedChange == null) {
        Modifier.semantics {
            role = Role.Switch
            toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
        }
    } else {
        Modifier.toggleable(
            value = checked,
            role = Role.Switch,
            onValueChange = onCheckedChange
        )
    }
    SettingsItem(
        title = title,
        subtitle = subtitle,
        modifier = interactionModifier,
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AccentGreen,
                    checkedTrackColor = AccentGreen.copy(alpha = 0.3f)
                )
            )
        }
    )
}

@Composable
private fun SettingsStepperItem(
    title: String,
    subtitle: String,
    valueLabel: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    SettingsItem(
        title = title,
        subtitle = subtitle,
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepperButton(
                    enabled = canDecrease,
                    onClick = onDecrease,
                    contentDescription = stringResource(R.string.settings_decrease_value, title)
                ) {
                    Icon(Minus, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                StepperButton(
                    enabled = canIncrease,
                    onClick = onIncrease,
                    contentDescription = stringResource(R.string.settings_increase_value, title)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    )
}

@Composable
private fun StepperButton(
    enabled: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            enabled = enabled,
            onClick = onClick,
            modifier = Modifier
                .size(32.dp)
                .semantics { this.contentDescription = contentDescription }
        ) {
            Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsActionItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    SettingsItem(
        title = title,
        subtitle = subtitle,
        modifier = Modifier.clickable(onClick = onClick),
        trailingContent = {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailingContent: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailingContent()
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Preview(name = "Locked", showBackground = true, widthDp = 420, heightDp = 1_100)
@Composable
private fun SettingsScreenLockedPreview() {
    SettingsScreenPreviewContent(SettingsUiState())
}

@Preview(name = "Automation pack", showBackground = true, widthDp = 420, heightDp = 1_100)
@Composable
private fun SettingsScreenAutomationPreview() {
    SettingsScreenPreviewContent(
        SettingsUiState(
            isAutomationPackPurchased = true,
            autoPauseEnabled = true,
            autoResumeEnabled = true
        )
    )
}

@Preview(name = "Premium", showBackground = true, widthDp = 420, heightDp = 1_100)
@Composable
private fun SettingsScreenPremiumPreview() {
    SettingsScreenPreviewContent(
        SettingsUiState(
            isPremiumSubscribed = true,
            autoPauseEnabled = true,
            autoResumeEnabled = true
        )
    )
}

@Composable
private fun SettingsScreenPreviewContent(state: SettingsUiState) {
    LeanAngleTrackerTheme {
        SettingsScreen(
            state = state,
            onBack = {},
            onSetHistoryWindow = {},
            onSetRecorderIntervalMs = {},
            onResetGaugeExtrema = {},
            onStartAppTour = {},
            onStartCalibration = {},
            onToggleAutoResume = {},
            onOpenPremium = {},
            onToggleAutoPause = {}
        )
    }
}
