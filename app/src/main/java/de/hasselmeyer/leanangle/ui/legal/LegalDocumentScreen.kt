package de.hasselmeyer.leanangle.ui.legal

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.hasselmeyer.leanangle.BuildConfig
import de.hasselmeyer.leanangle.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LegalDocumentScreen(
    document: LegalDocument,
    onBack: () -> Unit,
    onOpenGeneratedLicenses: () -> Unit = {}
) {
    val title = stringResource(document.titleResource)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            when (document) {
                LegalDocument.PRIVACY -> PrivacyPolicy()
                LegalDocument.TERMS -> TermsOfUse()
                LegalDocument.LEGAL_NOTICE -> LegalNotice()
                LegalDocument.SAFETY -> SafetyInformation()
                LegalDocument.GRAPHICS -> GraphicsInformation()
                LegalDocument.OPEN_SOURCE -> OpenSourceInformation(onOpenGeneratedLicenses)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PrivacyPolicy() {
    val uriHandler = LocalUriHandler.current
    LegalSection(R.string.privacy_controller_title) {
        ProviderDetails()
    }
    LegalSection(R.string.privacy_local_data_title, R.string.privacy_local_data_body)
    LegalSection(R.string.privacy_advertising_title, R.string.privacy_advertising_body)
    LegalSection(R.string.privacy_maps_title, R.string.privacy_maps_body)
    LegalSection(R.string.privacy_billing_title, R.string.privacy_billing_body)
    LegalSection(R.string.privacy_export_title, R.string.privacy_export_body)
    LegalSection(R.string.privacy_retention_title, R.string.privacy_retention_body)
    LegalSection(R.string.privacy_rights_title, R.string.privacy_rights_body)
    LegalSection(R.string.privacy_children_title, R.string.privacy_children_body)

    if (BuildConfig.PRIVACY_POLICY_URL.isNotBlank()) {
        OutlinedButton(
            onClick = { uriHandler.openUri(BuildConfig.PRIVACY_POLICY_URL) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.privacy_open_online))
        }
    }
}

@Composable
private fun TermsOfUse() {
    LegalSection(R.string.terms_scope_title, R.string.terms_scope_body)
    LegalSection(R.string.terms_access_model_title, R.string.terms_access_model_body)
    LegalSection(R.string.terms_measurement_title, R.string.terms_measurement_body)
    LegalSection(R.string.terms_safe_use_title, R.string.terms_safe_use_body)
    LegalSection(R.string.terms_user_data_title, R.string.terms_user_data_body)
    LegalSection(R.string.terms_purchases_title, R.string.terms_purchases_body)
    LegalSection(R.string.terms_availability_title, R.string.terms_availability_body)
    LegalSection(R.string.terms_warranty_title, R.string.terms_warranty_body)
    LegalSection(R.string.terms_liability_title, R.string.terms_liability_body)
    LegalSection(R.string.terms_law_title, R.string.terms_law_body)
}

@Composable
private fun LegalNotice() {
    LegalSection(R.string.legal_provider_title) {
        ProviderDetails()
    }
    LegalSection(R.string.legal_contact_title) {
        Text(
            text = BuildConfig.LEGAL_PROVIDER_EMAIL,
            style = MaterialTheme.typography.bodyMedium
        )
    }
    LegalSection(R.string.legal_additional_title, R.string.legal_additional_body)
}

@Composable
private fun SafetyInformation() {
    LegalSection(R.string.safety_not_instrument_title, R.string.safety_not_instrument_body)
    LegalSection(R.string.safety_no_riding_use_title, R.string.safety_no_riding_use_body)
    LegalSection(R.string.safety_calibration_title, R.string.safety_calibration_body)
    LegalSection(R.string.safety_mount_title, R.string.safety_mount_body)
    LegalSection(R.string.safety_rules_title, R.string.safety_rules_body)
}

@Composable
private fun GraphicsInformation() {
    LegalSection(R.string.graphics_origin_title, R.string.graphics_origin_body)
    LegalSection(R.string.graphics_protection_title, R.string.graphics_protection_body)
    LegalSection(R.string.graphics_osm_title, R.string.graphics_osm_body)
}

@Composable
private fun OpenSourceInformation(onOpenGeneratedLicenses: () -> Unit) {
    LegalSection(R.string.oss_summary_title, R.string.oss_summary_body)
    LegalSection(R.string.oss_google_notices_title, R.string.oss_google_notices_body)
    LegalSection(R.string.oss_osm_title, R.string.oss_osm_body)
    OutlinedButton(
        onClick = onOpenGeneratedLicenses,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.oss_open_generated))
    }
}

@Composable
private fun ProviderDetails() {
    Text(
        text = buildString {
            append(BuildConfig.LEGAL_PROVIDER_NAME)
            append('\n')
            append(BuildConfig.LEGAL_PROVIDER_ADDRESS)
            append('\n')
            append(BuildConfig.LEGAL_PROVIDER_EMAIL)
        },
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun LegalSection(
    @StringRes titleResource: Int,
    @StringRes bodyResource: Int
) {
    LegalSection(titleResource) {
        Text(
            text = stringResource(bodyResource),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LegalSection(
    @StringRes titleResource: Int,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(titleResource),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        content()
    }
}

private val LegalDocument.titleResource: Int
    get() = when (this) {
        LegalDocument.PRIVACY -> R.string.legal_privacy_title
        LegalDocument.TERMS -> R.string.legal_terms_title
        LegalDocument.LEGAL_NOTICE -> R.string.legal_notice_title
        LegalDocument.SAFETY -> R.string.legal_safety_title
        LegalDocument.GRAPHICS -> R.string.legal_graphics_title
        LegalDocument.OPEN_SOURCE -> R.string.settings_open_source_licenses
    }
