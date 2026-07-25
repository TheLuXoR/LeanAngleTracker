package com.example.leanangletracker

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
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

    private fun clearPreferences() {
        context.getSharedPreferences("lean_angle_tracker_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
