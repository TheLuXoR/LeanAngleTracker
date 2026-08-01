package com.example.leanangletracker

import com.example.leanangletracker.billing.PremiumEntitlements
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementUpdatePolicyTest {
    @Test
    fun verifiedDowngradeIsDeferredDuringRide() {
        assertTrue(
            shouldDeferEntitlementUpdate(
                trackingStarted = true,
                currentAutomationPackPurchased = false,
                currentPremiumSubscribed = true,
                updatedEntitlements = PremiumEntitlements(
                    isAutomationPackPurchased = false,
                    isPremiumSubscribed = false
                )
            )
        )
    }

    @Test
    fun upgradeAndStoppedRideApplyImmediately() {
        val upgrade = shouldDeferEntitlementUpdate(
            trackingStarted = true,
            currentAutomationPackPurchased = false,
            currentPremiumSubscribed = false,
            updatedEntitlements = PremiumEntitlements(
                isAutomationPackPurchased = false,
                isPremiumSubscribed = true
            )
        )
        val stoppedRideDowngrade = shouldDeferEntitlementUpdate(
            trackingStarted = false,
            currentAutomationPackPurchased = true,
            currentPremiumSubscribed = false,
            updatedEntitlements = PremiumEntitlements()
        )

        assertFalse(upgrade)
        assertFalse(stoppedRideDowngrade)
    }
}
