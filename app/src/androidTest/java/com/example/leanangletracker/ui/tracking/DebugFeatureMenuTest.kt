package com.example.leanangletracker.ui.tracking

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.leanangletracker.R
import com.example.leanangletracker.SensorSamplingRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DebugFeatureMenuTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sensorSamplingRateCanBeChangedToHighest() {
        var selectedRate: SensorSamplingRate? = null
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeRule.setContent {
            MaterialTheme {
                DebugFeatureMenu(
                    sensorSamplingRate = SensorSamplingRate.MEDIUM,
                    onShowAutoResumeIndicator = {},
                    onSetSensorSamplingRate = { selectedRate = it }
                )
            }
        }

        composeRule
            .onNodeWithContentDescription(context.getString(R.string.debug_features_open))
            .performClick()
        composeRule
            .onNodeWithText(context.getString(R.string.debug_sensor_sampling_title))
            .performClick()
        composeRule
            .onNodeWithText(context.getString(R.string.debug_sensor_sampling_highest))
            .performClick()

        composeRule.runOnIdle {
            assertEquals(SensorSamplingRate.HIGHEST, selectedRate)
        }
    }

    @Test
    fun entitlementCacheCanBeClearedAfterConfirmation() {
        var cleared = false
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeRule.setContent {
            MaterialTheme {
                DebugFeatureMenu(
                    sensorSamplingRate = SensorSamplingRate.MEDIUM,
                    onShowAutoResumeIndicator = {},
                    onSetSensorSamplingRate = {},
                    onClearEntitlementCache = { cleared = true }
                )
            }
        }

        composeRule
            .onNodeWithContentDescription(context.getString(R.string.debug_features_open))
            .performClick()
        composeRule
            .onNodeWithText(context.getString(R.string.debug_entitlement_cache_clear))
            .performClick()
        composeRule
            .onNodeWithText(context.getString(R.string.debug_entitlement_cache_clear_confirm))
            .performClick()

        composeRule.runOnIdle { assertTrue(cleared) }
    }

    @Test
    fun entitlementCacheCanBeExpiredAfterConfirmation() {
        var expired = false
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeRule.setContent {
            MaterialTheme {
                DebugFeatureMenu(
                    sensorSamplingRate = SensorSamplingRate.MEDIUM,
                    onShowAutoResumeIndicator = {},
                    onSetSensorSamplingRate = {},
                    onExpireEntitlementCache = { expired = true }
                )
            }
        }

        composeRule
            .onNodeWithContentDescription(context.getString(R.string.debug_features_open))
            .performClick()
        composeRule
            .onNodeWithText(context.getString(R.string.debug_entitlement_cache_expire))
            .performClick()
        composeRule
            .onNodeWithText(context.getString(R.string.debug_entitlement_cache_expire_confirm))
            .performClick()

        composeRule.runOnIdle { assertTrue(expired) }
    }
}
