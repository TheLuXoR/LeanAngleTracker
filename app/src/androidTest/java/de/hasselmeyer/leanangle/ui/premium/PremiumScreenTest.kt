package de.hasselmeyer.leanangle.ui.premium

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import de.hasselmeyer.leanangle.R
import de.hasselmeyer.leanangle.billing.PremiumBillingState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PremiumScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun availableOffersStartPurchasesAndExplainAllBenefits() {
        var automationPurchaseCount = 0
        var subscribeCount = 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val oneTimePrice = "0,99 €"
        val subscriptionPrice = "2,99 € / month"
        val buyOnceLabel = context.getString(R.string.premium_buy_once, oneTimePrice)
        val subscribeLabel = context.getString(R.string.premium_subscribe, subscriptionPrice)

        composeRule.setContent {
            MaterialTheme {
                PremiumScreen(
                    isAutomationPackPurchased = false,
                    isPremiumSubscribed = false,
                    billingState = PremiumBillingState(
                        isAutomationPackLoading = false,
                        isSubscriptionLoading = false,
                        automationPackPriceLabel = oneTimePrice,
                        subscriptionPriceLabel = subscriptionPrice
                    ),
                    onBack = {},
                    onBuyAutomationPack = { automationPurchaseCount++ },
                    onSubscribe = { subscribeCount++ },
                    onRestorePurchases = {},
                    onManageSubscription = {}
                )
            }
        }

        composeRule.onAllNodesWithText(
            context.getString(R.string.premium_automation_pack_title)
        ).assertCountEquals(2)
        composeRule.onNodeWithText(context.getString(R.string.settings_auto_pause_title)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.settings_auto_resume_title)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.premium_no_ads_title)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.premium_future_title)).assertExists()

        composeRule.onNodeWithText(buyOnceLabel).performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, automationPurchaseCount) }
        composeRule.onNodeWithText(subscribeLabel).performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, subscribeCount) }
    }

    @Test
    fun purchasedPackShowsPurchasedStatus() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeRule.setContent {
            MaterialTheme {
                PremiumScreen(
                    isAutomationPackPurchased = true,
                    isPremiumSubscribed = false,
                    billingState = PremiumBillingState(
                        isAutomationPackLoading = false,
                        isSubscriptionLoading = false
                    ),
                    onBack = {},
                    onBuyAutomationPack = {},
                    onSubscribe = {},
                    onRestorePurchases = {},
                    onManageSubscription = {}
                )
            }
        }

        composeRule.onNodeWithText(
            context.getString(R.string.premium_single_purchase_owned)
        ).assertExists()
        composeRule.onNodeWithText(
            context.getString(R.string.premium_included_in_subscription)
        ).assertDoesNotExist()
    }

    @Test
    fun activeSubscriptionShowsIncludedStatusAndCanBeManaged() {
        var manageCount = 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manageLabel = context.getString(R.string.premium_manage_subscription)

        composeRule.setContent {
            MaterialTheme {
                PremiumScreen(
                    isAutomationPackPurchased = false,
                    isPremiumSubscribed = true,
                    billingState = PremiumBillingState(
                        isAutomationPackLoading = false,
                        isSubscriptionLoading = false,
                        isSubscribed = true
                    ),
                    onBack = {},
                    onBuyAutomationPack = {},
                    onSubscribe = {},
                    onRestorePurchases = {},
                    onManageSubscription = { manageCount++ }
                )
            }
        }

        composeRule.onNodeWithText(
            context.getString(R.string.premium_included_in_subscription)
        ).assertExists()
        composeRule.onNodeWithText(manageLabel).performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, manageCount) }
    }
}
