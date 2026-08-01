package de.hasselmeyer.leanangle.billing

internal const val PREMIUM_CACHE_FRESH_MS = 24L * 60L * 60L * 1_000L
internal const val PREMIUM_CACHE_GRACE_MS = 7L * 24L * 60L * 60L * 1_000L

internal enum class PremiumCacheFreshness {
    MISSING,
    FRESH,
    GRACE,
    HARD_EXPIRED
}

internal data class BillingEntitlementCache(
    val isAutomationPackPurchased: Boolean = false,
    val automationPackVerifiedAtMs: Long? = null,
    val isPremiumSubscribed: Boolean = false,
    val premiumVerifiedAtMs: Long? = null
)

internal data class PremiumCacheDecision(
    val grantsPremium: Boolean,
    val freshness: PremiumCacheFreshness
)

internal fun evaluatePremiumCache(
    cache: BillingEntitlementCache,
    nowMs: Long
): PremiumCacheDecision {
    if (!cache.isPremiumSubscribed || cache.premiumVerifiedAtMs == null) {
        return PremiumCacheDecision(
            grantsPremium = false,
            freshness = PremiumCacheFreshness.MISSING
        )
    }

    val ageMs = nowMs - cache.premiumVerifiedAtMs
    return when {
        ageMs < 0L -> PremiumCacheDecision(false, PremiumCacheFreshness.HARD_EXPIRED)
        ageMs <= PREMIUM_CACHE_FRESH_MS ->
            PremiumCacheDecision(true, PremiumCacheFreshness.FRESH)
        ageMs <= PREMIUM_CACHE_GRACE_MS ->
            PremiumCacheDecision(true, PremiumCacheFreshness.GRACE)
        else -> PremiumCacheDecision(false, PremiumCacheFreshness.HARD_EXPIRED)
    }
}
