package com.example.leanangletracker.ui.premium

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.leanangletracker.R
import com.example.leanangletracker.billing.PremiumBillingMessage
import com.example.leanangletracker.billing.PremiumBillingState
import com.example.leanangletracker.ui.theme.LeanAngleTrackerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PremiumScreen(
    isAutomationPackPurchased: Boolean,
    isPremiumSubscribed: Boolean,
    billingState: PremiumBillingState,
    onBack: () -> Unit,
    onBuyAutomationPack: () -> Unit,
    onSubscribe: () -> Unit,
    onRestorePurchases: () -> Unit,
    onManageSubscription: () -> Unit,
    onOpenTerms: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.premium_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PremiumSectionTitle(
                title = stringResource(R.string.premium_single_purchases_title),
                body = stringResource(R.string.premium_single_purchases_body)
            )
            AutomationPackPurchaseCard(
                isPurchased = isAutomationPackPurchased,
                isIncludedInSubscription = isPremiumSubscribed,
                isLoading = billingState.isAutomationPackLoading,
                priceLabel = billingState.automationPackPriceLabel,
                onBuy = onBuyAutomationPack
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            PremiumSectionTitle(
                title = stringResource(R.string.premium_subscription_section_title),
                body = stringResource(R.string.premium_subscription_body)
            )
            PremiumSubscriptionCard(
                isSubscribed = isPremiumSubscribed,
                isLoading = billingState.isSubscriptionLoading,
                priceLabel = billingState.subscriptionPriceLabel,
                onSubscribe = onSubscribe,
                onManageSubscription = onManageSubscription
            )

            BillingMessage(message = billingState.message)

            TextButton(
                onClick = onRestorePurchases,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(stringResource(R.string.premium_restore_purchases))
            }

            Text(
                text = stringResource(R.string.premium_subscription_terms),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = onOpenTerms) {
                    Text(stringResource(R.string.legal_terms_title))
                }
                TextButton(onClick = onOpenPrivacyPolicy) {
                    Text(stringResource(R.string.legal_privacy_title))
                }
            }
        }
    }
}

@Composable
private fun PremiumSectionTitle(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AutomationPackPurchaseCard(
    isPurchased: Boolean,
    isIncludedInSubscription: Boolean,
    isLoading: Boolean,
    priceLabel: String?,
    onBuy: () -> Unit
) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.DirectionsBike,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.premium_automation_pack_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.premium_automation_pack_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isPurchased || isIncludedInSubscription) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = stringResource(
                            if (isPurchased) {
                                R.string.premium_single_purchase_owned
                            } else {
                                R.string.premium_included_in_subscription
                            }
                        ),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            HorizontalDivider()

            PremiumFeatureRow(
                icon = Icons.Default.PauseCircle,
                title = stringResource(R.string.settings_auto_pause_title),
                body = stringResource(R.string.settings_auto_pause_subtitle)
            )
            PremiumFeatureRow(
                icon = Icons.AutoMirrored.Filled.DirectionsBike,
                title = stringResource(R.string.settings_auto_resume_title),
                body = stringResource(R.string.settings_auto_resume_subtitle)
            )

            if (isPurchased || isIncludedInSubscription) {
                Text(
                    stringResource(
                        if (isPurchased) {
                            R.string.premium_single_purchase_owned
                        } else {
                            R.string.premium_included_in_subscription
                        }
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.End)
                )
            } else {
                PurchaseButton(
                    label = stringResource(
                        R.string.premium_buy_once,
                        priceLabel ?: stringResource(R.string.premium_price_unavailable)
                    ),
                    isLoading = isLoading,
                    enabled = priceLabel != null,
                    onClick = onBuy,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun PurchaseButton(
    label: String,
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
    }
}

@Composable
private fun PremiumSubscriptionCard(
    isSubscribed: Boolean,
    isLoading: Boolean,
    priceLabel: String?,
    onSubscribe: () -> Unit,
    onManageSubscription: () -> Unit
) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.premium_subscription_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (isSubscribed) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.premium_active_title),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            HorizontalDivider()

            PremiumFeatureRow(
                icon = Icons.Default.Block,
                title = stringResource(R.string.premium_no_ads_title),
                body = stringResource(R.string.premium_no_ads_body)
            )
            PremiumFeatureRow(
                icon = Icons.AutoMirrored.Filled.DirectionsBike,
                title = stringResource(R.string.premium_automation_pack_title),
                body = stringResource(R.string.premium_automation_pack_subscription_body)
            )
            PremiumFeatureRow(
                icon = Icons.Default.NewReleases,
                title = stringResource(R.string.premium_future_title),
                body = stringResource(R.string.premium_future_body)
            )

            if (isSubscribed) {
                Button(
                    onClick = onManageSubscription,
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) {
                    Text(stringResource(R.string.premium_manage_subscription))
                }
            } else {
                PurchaseButton(
                    label = stringResource(
                        R.string.premium_subscribe,
                        priceLabel ?: stringResource(R.string.premium_price_unavailable)
                    ),
                    isLoading = isLoading,
                    enabled = priceLabel != null,
                    onClick = onSubscribe,
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                )
            }
        }
    }
}

@Composable
private fun PremiumFeatureRow(icon: ImageVector, title: String, body: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BillingMessage(message: PremiumBillingMessage?) {
    val messageResId = when (message) {
        PremiumBillingMessage.PURCHASE_PENDING -> R.string.premium_purchase_pending
        PremiumBillingMessage.PRODUCT_UNAVAILABLE -> R.string.premium_product_unavailable
        PremiumBillingMessage.BILLING_ERROR -> R.string.premium_billing_error
        null -> return
    }
    Text(
        text = stringResource(messageResId),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}

@Preview(name = "Available", showBackground = true, widthDp = 420, heightDp = 1_200)
@Composable
private fun PremiumScreenAvailablePreview() {
    PremiumScreenPreviewContent(isAutomationPackPurchased = false, isPremiumSubscribed = false)
}

@Preview(name = "Automation purchased", showBackground = true, widthDp = 420, heightDp = 1_200)
@Composable
private fun PremiumScreenPurchasedPreview() {
    PremiumScreenPreviewContent(isAutomationPackPurchased = true, isPremiumSubscribed = false)
}

@Preview(name = "Subscribed", showBackground = true, widthDp = 420, heightDp = 1_200)
@Composable
private fun PremiumScreenSubscribedPreview() {
    PremiumScreenPreviewContent(isAutomationPackPurchased = false, isPremiumSubscribed = true)
}

@Composable
private fun PremiumScreenPreviewContent(
    isAutomationPackPurchased: Boolean,
    isPremiumSubscribed: Boolean
) {
    LeanAngleTrackerTheme {
        PremiumScreen(
            isAutomationPackPurchased = isAutomationPackPurchased,
            isPremiumSubscribed = isPremiumSubscribed,
            billingState = PremiumBillingState(
                isAutomationPackLoading = false,
                isSubscriptionLoading = false,
                automationPackPriceLabel = "0,99 €",
                subscriptionPriceLabel = "2,99 € / Monat"
            ),
            onBack = {},
            onBuyAutomationPack = {},
            onSubscribe = {},
            onRestorePurchases = {},
            onManageSubscription = {}
        )
    }
}
