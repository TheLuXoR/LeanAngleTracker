package de.hasselmeyer.leanangle

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import de.hasselmeyer.leanangle.billing.PREMIUM_CACHE_GRACE_MS
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettingsStoreTest {
    private lateinit var context: Context
    private lateinit var store: SettingsStore

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        clearPreferences()
        store = SettingsStore(context)
    }

    @After
    fun tearDown() {
        clearPreferences()
    }

    @Test
    fun gaugeExtremaAndTourVersionSurviveReload() {
        val expectedExtrema = LeanExtrema(maxLeftDeg = -34.5f, maxRightDeg = 29.25f)
        store.saveGaugeExtrema(expectedExtrema)
        store.saveCompletedAppTourVersion(1)

        val reloaded = SettingsStore(context).load(
            recorderIntervalMinMs = 50,
            recorderIntervalMaxMs = 1_000
        )

        assertEquals(expectedExtrema, reloaded.gaugeExtrema)
        assertEquals(1, reloaded.completedAppTourVersion)
    }

    @Test
    fun freshInstallStartsWithAutomationLockedAndDisabled() {
        val reloaded = store.load(
            recorderIntervalMinMs = 50,
            recorderIntervalMaxMs = 1_000
        )

        assertEquals(false, reloaded.settings.isAutomationPackPurchased)
        assertEquals(false, reloaded.settings.isPremiumSubscribed)
        assertEquals(false, reloaded.settings.autoPauseEnabled)
        assertEquals(false, reloaded.settings.autoResumeEnabled)
    }

    @Test
    fun legacyPositivePremiumGetsOneMigrationGracePeriod() {
        context.getSharedPreferences("lean_angle_tracker_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("premium_subscribed", true)
            .commit()

        val reloaded = store.load(
            recorderIntervalMinMs = 50,
            recorderIntervalMaxMs = 1_000
        )
        val preferences = context.getSharedPreferences(
            "lean_angle_tracker_prefs",
            Context.MODE_PRIVATE
        )

        assertTrue(reloaded.settings.isPremiumSubscribed)
        assertEquals(1, preferences.getInt("entitlement_cache_version", 0))
        assertTrue(preferences.getLong("premium_verified_at_ms", 0L) > 0L)
    }

    @Test
    fun premiumCacheOlderThanSevenDaysDoesNotUnlockPremium() {
        val eightDaysMs = 8L * 24L * 60L * 60L * 1_000L
        context.getSharedPreferences("lean_angle_tracker_prefs", Context.MODE_PRIVATE)
            .edit()
            .putInt("entitlement_cache_version", 1)
            .putBoolean("premium_subscribed", true)
            .putLong("premium_verified_at_ms", System.currentTimeMillis() - eightDaysMs)
            .commit()

        val reloaded = store.load(
            recorderIntervalMinMs = 50,
            recorderIntervalMaxMs = 1_000
        )

        assertEquals(false, reloaded.settings.isPremiumSubscribed)
    }

    @Test
    fun debugClearRemovesAllEntitlementCacheData() {
        store.saveVerifiedAutomationPack(isPurchased = true, verifiedAtMs = 100L)
        store.saveVerifiedPremium(isSubscribed = true, verifiedAtMs = 100L)

        assertTrue(store.debugClearBillingEntitlementCache())
        val cache = store.loadBillingEntitlementCache(nowMs = 200L)

        assertEquals(false, cache.isAutomationPackPurchased)
        assertEquals(null, cache.automationPackVerifiedAtMs)
        assertEquals(false, cache.isPremiumSubscribed)
        assertEquals(null, cache.premiumVerifiedAtMs)
    }

    @Test
    fun debugExpirePreservesFlagsAndAgesEveryTimestampBeyondGrace() {
        val nowMs = PREMIUM_CACHE_GRACE_MS + 10_000L
        store.saveVerifiedAutomationPack(isPurchased = true, verifiedAtMs = nowMs)
        store.saveVerifiedPremium(isSubscribed = true, verifiedAtMs = nowMs)

        assertTrue(store.debugAgeBillingEntitlementCacheBeyondGrace(nowMs))
        val cache = store.loadBillingEntitlementCache(nowMs)

        assertTrue(cache.isAutomationPackPurchased)
        assertTrue(cache.isPremiumSubscribed)
        assertTrue(nowMs - requireNotNull(cache.automationPackVerifiedAtMs) > PREMIUM_CACHE_GRACE_MS)
        assertTrue(nowMs - requireNotNull(cache.premiumVerifiedAtMs) > PREMIUM_CACHE_GRACE_MS)
    }

    private fun clearPreferences() {
        context.getSharedPreferences("lean_angle_tracker_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
