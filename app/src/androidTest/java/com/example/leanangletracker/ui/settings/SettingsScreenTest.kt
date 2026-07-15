package com.example.leanangletracker.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.example.leanangletracker.R
import com.example.leanangletracker.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun resetButtonTargetsGaugeAndShowsLongPressHint() {
        var resetCount = 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resetLabel = context.getString(R.string.settings_reset_gauge_max_values)
        val resetHint = context.getString(R.string.settings_reset_gauge_hint)

        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    state = SettingsUiState(),
                    onBack = {},
                    onToggleInvertLean = {},
                    onToggleGpsTracking = {},
                    onSetHistoryWindow = {},
                    onSetRecorderIntervalMs = {},
                    onResetGaugeExtrema = { resetCount++ },
                    onStartAppTour = {},
                    onStartCalibration = {},
                    onToggleAutoResume = {},
                    onPurchaseAutoResume = {},
                    onToggleAutoPause = {}
                )
            }
        }

        composeRule.onNodeWithText(resetLabel).performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, resetCount) }
        composeRule.onNodeWithText(resetHint).assertExists()
    }
}
