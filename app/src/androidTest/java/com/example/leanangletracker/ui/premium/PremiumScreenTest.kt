package com.example.leanangletracker.ui.premium

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.example.leanangletracker.R
import com.example.leanangletracker.billing.PremiumBillingState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PremiumScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun availableSubscriptionStartsPurchaseAndExplainsBenefits() {
        var autoResumePurchaseCount = 0
        var subscribeCount = 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val oneTimePrice = "0,99 €"
        val subscriptionPrice = "2,99 € / month"
        val buyOnceLabel = context.getString(R.string.premium_buy_once, oneTimePrice)
        val subscribeLabel = context.getString(R.string.premium_subscribe, subscriptionPrice)

        composeRule.setContent {
            MaterialTheme {
                PremiumScreen(
                    isAutoResumePurchased = false,
                    isPremiumSubscribed = false,
                    billingState = PremiumBillingState(
                        isAutoResumeLoading = false,
                        isSubscriptionLoading = false,
                        autoResumePriceLabel = oneTimePrice,
                        subscriptionPriceLabel = subscriptionPrice
                    ),
                    onBack = {},
                    onBuyAutoResume = { autoResumePurchaseCount++ },
                    onSubscribe = { subscribeCount++ },
                    onRestorePurchases = {},
                    onManageSubscription = {}
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.premium_auto_resume_title)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.premium_no_ads_title)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.premium_future_title)).assertExists()
        composeRule.onNodeWithText(buyOnceLabel).performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, autoResumePurchaseCount) }
        composeRule.onNodeWithText(subscribeLabel).performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, subscribeCount) }
    }

    @Test
    fun activeSubscriptionCanBeManaged() {
        var manageCount = 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manageLabel = context.getString(R.string.premium_manage_subscription)

        composeRule.setContent {
            MaterialTheme {
                PremiumScreen(
                    isAutoResumePurchased = false,
                    isPremiumSubscribed = true,
                    billingState = PremiumBillingState(
                        isAutoResumeLoading = false,
                        isSubscriptionLoading = false,
                        isSubscribed = true
                    ),
                    onBack = {},
                    onBuyAutoResume = {},
                    onSubscribe = {},
                    onRestorePurchases = {},
                    onManageSubscription = { manageCount++ }
                )
            }
        }

        composeRule.onNodeWithText(manageLabel).performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, manageCount) }
    }
}
