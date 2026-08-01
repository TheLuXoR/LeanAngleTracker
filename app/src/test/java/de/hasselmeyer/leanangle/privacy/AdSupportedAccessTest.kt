package de.hasselmeyer.leanangle.privacy

import org.junit.Assert.assertEquals
import org.junit.Test

class AdSupportedAccessTest {
    @Test
    fun premiumAlwaysOpensApp() {
        assertEquals(
            AdSupportedAccessState.OPEN,
            resolveAdSupportedAccessState(
                isPremiumSubscribed = true,
                hasChosenAdSupportedAccess = false,
                consentRequestStarted = false,
                consentState = AdsConsentState()
            )
        )
    }

    @Test
    fun freeAccessRequiresExplicitProductModeChoice() {
        assertEquals(
            AdSupportedAccessState.CHOOSE_ACCESS,
            resolveAdSupportedAccessState(
                isPremiumSubscribed = false,
                hasChosenAdSupportedAccess = false,
                consentRequestStarted = false,
                consentState = AdsConsentState()
            )
        )
    }

    @Test
    fun eligibleLimitedOrPersonalizedAdsOpenApp() {
        assertEquals(
            AdSupportedAccessState.OPEN,
            resolveAdSupportedAccessState(
                isPremiumSubscribed = false,
                hasChosenAdSupportedAccess = true,
                consentRequestStarted = true,
                consentState = AdsConsentState(
                    canRequestAds = true,
                    requestCompleted = true
                )
            )
        )
    }

    @Test
    fun completedIneligibleDecisionKeepsCoreAppClosed() {
        assertEquals(
            AdSupportedAccessState.AD_ACCESS_UNAVAILABLE,
            resolveAdSupportedAccessState(
                isPremiumSubscribed = false,
                hasChosenAdSupportedAccess = true,
                consentRequestStarted = true,
                consentState = AdsConsentState(
                    canRequestAds = false,
                    requestCompleted = true
                )
            )
        )
    }

    @Test
    fun umpPreviousSessionEligibilityOpensWhileRefreshIsRunning() {
        assertEquals(
            AdSupportedAccessState.OPEN,
            resolveAdSupportedAccessState(
                isPremiumSubscribed = false,
                hasChosenAdSupportedAccess = true,
                consentRequestStarted = true,
                consentState = AdsConsentState(
                    canRequestAds = true,
                    requestCompleted = false
                )
            )
        )
    }
}
