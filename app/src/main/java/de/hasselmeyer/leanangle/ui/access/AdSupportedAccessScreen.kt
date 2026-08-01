package de.hasselmeyer.leanangle.ui.access

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.hasselmeyer.leanangle.R
import de.hasselmeyer.leanangle.privacy.AdSupportedAccessState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdSupportedAccessScreen(
    state: AdSupportedAccessState,
    subscriptionPriceLabel: String?,
    privacyOptionsRequired: Boolean,
    errorMessage: String?,
    onChooseAdSupportedAccess: () -> Unit,
    onOpenPremium: () -> Unit,
    onReviewPrivacyChoices: () -> Unit,
    onRetryPrivacyCheck: () -> Unit,
    onRestorePurchases: () -> Unit,
    onManageRideData: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenLegalNotice: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.access_title)) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (state) {
                AdSupportedAccessState.CHOOSE_ACCESS -> AccessChoice(
                    subscriptionPriceLabel = subscriptionPriceLabel,
                    onChooseAdSupportedAccess = onChooseAdSupportedAccess,
                    onOpenPremium = onOpenPremium
                )

                AdSupportedAccessState.CHECKING_AD_ELIGIBILITY -> CheckingAccess()

                AdSupportedAccessState.AD_ACCESS_UNAVAILABLE -> UnavailableAccess(
                    privacyOptionsRequired = privacyOptionsRequired,
                    errorMessage = errorMessage,
                    subscriptionPriceLabel = subscriptionPriceLabel,
                    onReviewPrivacyChoices = onReviewPrivacyChoices,
                    onRetryPrivacyCheck = onRetryPrivacyCheck,
                    onOpenPremium = onOpenPremium
                )

                AdSupportedAccessState.OPEN -> Unit
            }

            DataAndLegalAccess(
                onRestorePurchases = onRestorePurchases,
                onManageRideData = onManageRideData,
                onOpenPrivacyPolicy = onOpenPrivacyPolicy,
                onOpenTerms = onOpenTerms,
                onOpenLegalNotice = onOpenLegalNotice
            )
        }
    }
}

@Composable
private fun AccessChoice(
    subscriptionPriceLabel: String?,
    onChooseAdSupportedAccess: () -> Unit,
    onOpenPremium: () -> Unit
) {
    Text(
        text = stringResource(R.string.access_choice_heading),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )
    Text(
        text = stringResource(R.string.access_choice_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    AccessOptionCard(
        icon = Icons.Default.CheckCircle,
        title = stringResource(R.string.access_free_title),
        body = stringResource(R.string.access_free_body)
    ) {
        Button(
            onClick = onChooseAdSupportedAccess,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.access_free_action))
        }
    }

    AccessOptionCard(
        icon = Icons.Default.Star,
        title = stringResource(R.string.access_premium_title),
        body = stringResource(R.string.access_premium_body)
    ) {
        OutlinedButton(
            onClick = onOpenPremium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                subscriptionPriceLabel?.let {
                    stringResource(R.string.access_premium_action_with_price, it)
                } ?: stringResource(R.string.access_premium_action)
            )
        }
    }

    Text(
        text = stringResource(R.string.access_privacy_separation_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun CheckingAccess() {
    Spacer(Modifier.height(24.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
    }
    Text(
        text = stringResource(R.string.access_checking_title),
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Text(
        text = stringResource(R.string.access_checking_body),
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun UnavailableAccess(
    privacyOptionsRequired: Boolean,
    errorMessage: String?,
    subscriptionPriceLabel: String?,
    onReviewPrivacyChoices: () -> Unit,
    onRetryPrivacyCheck: () -> Unit,
    onOpenPremium: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
    }
    Text(
        text = stringResource(R.string.access_unavailable_title),
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Text(
        text = stringResource(R.string.access_unavailable_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (privacyOptionsRequired) {
        Button(
            onClick = onReviewPrivacyChoices,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PrivacyTip, contentDescription = null)
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text(stringResource(R.string.access_review_privacy))
        }
    }
    OutlinedButton(
        onClick = onRetryPrivacyCheck,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.access_retry))
    }
    OutlinedButton(
        onClick = onOpenPremium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Star, contentDescription = null)
        Spacer(Modifier.padding(horizontal = 4.dp))
        Text(
            subscriptionPriceLabel?.let {
                stringResource(R.string.access_premium_action_with_price, it)
            } ?: stringResource(R.string.access_premium_action)
        )
    }
    if (!errorMessage.isNullOrBlank()) {
        Text(
            text = stringResource(R.string.access_check_error, errorMessage),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun AccessOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            content()
        }
    }
}

@Composable
private fun DataAndLegalAccess(
    onRestorePurchases: () -> Unit,
    onManageRideData: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenLegalNotice: () -> Unit
) {
    Text(
        text = stringResource(R.string.access_always_available),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedButton(
        onClick = onRestorePurchases,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Restore, contentDescription = null)
        Spacer(Modifier.padding(horizontal = 4.dp))
        Text(stringResource(R.string.premium_restore_purchases))
    }
    OutlinedButton(
        onClick = onManageRideData,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.History, contentDescription = null)
        Spacer(Modifier.padding(horizontal = 4.dp))
        Text(stringResource(R.string.access_manage_ride_data))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        TextButton(onClick = onOpenPrivacyPolicy) {
            Text(stringResource(R.string.legal_privacy_title))
        }
        TextButton(onClick = onOpenTerms) {
            Text(stringResource(R.string.legal_terms_title))
        }
    }
    TextButton(
        onClick = onOpenLegalNotice,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.legal_notice_title))
    }
}
