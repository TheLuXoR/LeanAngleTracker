package com.example.leanangletracker.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementCachePolicyTest {
    private val nowMs = 1_000_000_000L

    @Test
    fun positivePremiumCacheIsFreshForFirst24Hours() {
        val decision = decisionAtAge(PREMIUM_CACHE_FRESH_MS)

        assertTrue(decision.grantsPremium)
        assertEquals(PremiumCacheFreshness.FRESH, decision.freshness)
    }

    @Test
    fun positivePremiumCacheUsesGraceThroughSevenDays() {
        val decision = decisionAtAge(PREMIUM_CACHE_GRACE_MS)

        assertTrue(decision.grantsPremium)
        assertEquals(PremiumCacheFreshness.GRACE, decision.freshness)
    }

    @Test
    fun positivePremiumCacheRequiresRefreshAfterSevenDays() {
        val decision = decisionAtAge(PREMIUM_CACHE_GRACE_MS + 1L)

        assertFalse(decision.grantsPremium)
        assertEquals(PremiumCacheFreshness.HARD_EXPIRED, decision.freshness)
    }

    @Test
    fun negativeAndFutureDatedCachesNeverGrantPremium() {
        val negative = evaluatePremiumCache(BillingEntitlementCache(), nowMs)
        val futureDated = evaluatePremiumCache(
            BillingEntitlementCache(
                isPremiumSubscribed = true,
                premiumVerifiedAtMs = nowMs + 1L
            ),
            nowMs
        )

        assertFalse(negative.grantsPremium)
        assertEquals(PremiumCacheFreshness.MISSING, negative.freshness)
        assertFalse(futureDated.grantsPremium)
        assertEquals(PremiumCacheFreshness.HARD_EXPIRED, futureDated.freshness)
    }

    private fun decisionAtAge(ageMs: Long): PremiumCacheDecision = evaluatePremiumCache(
        cache = BillingEntitlementCache(
            isPremiumSubscribed = true,
            premiumVerifiedAtMs = nowMs - ageMs
        ),
        nowMs = nowMs
    )
}
