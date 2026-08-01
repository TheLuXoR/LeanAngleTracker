package de.hasselmeyer.leanangle.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import de.hasselmeyer.leanangle.R
import de.hasselmeyer.leanangle.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun groupsAppearInRequestedOrder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        setContent(SettingsUiState())

        val sensorsTop = composeRule
            .onNodeWithText(context.getString(R.string.settings_group_sensors))
            .fetchSemanticsNode().boundsInRoot.top
        val displayTop = composeRule
            .onNodeWithText(context.getString(R.string.settings_group_visuals))
            .fetchSemanticsNode().boundsInRoot.top
        val resetTop = composeRule
            .onNodeWithText(context.getString(R.string.settings_group_reset))
            .fetchSemanticsNode().boundsInRoot.top

        assertTrue(sensorsTop < displayTop)
        assertTrue(displayTop < resetTop)
    }

    @Test
    fun lockedAutomationSwitchesRequestActivationWithoutChangingDisplayedState() {
        var autoPauseToggleCount = 0
        var autoResumeToggleCount = 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        setContent(
            state = SettingsUiState(),
            onToggleAutoPause = { autoPauseToggleCount++ },
            onToggleAutoResume = { autoResumeToggleCount++ }
        )

        composeRule.onNodeWithText(
            context.getString(R.string.settings_auto_pause_title)
        ).performClick()
        composeRule.onNodeWithText(
            context.getString(R.string.settings_auto_resume_title)
        ).performClick()

        composeRule.runOnIdle {
            assertEquals(1, autoPauseToggleCount)
            assertEquals(1, autoResumeToggleCount)
        }
        composeRule.onNodeWithText(
            context.getString(R.string.settings_auto_pause_title)
        ).assertIsOff()
        composeRule.onNodeWithText(
            context.getString(R.string.settings_auto_resume_title)
        ).assertIsOff()
    }

    @Test
    fun entitlementStatesAreReflectedBySwitches() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val autoPause = context.getString(R.string.settings_auto_pause_title)
        val autoResume = context.getString(R.string.settings_auto_resume_title)
        val noAds = context.getString(R.string.settings_no_ads_title)
        val state = mutableStateOf(SettingsUiState())

        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    state = state.value,
                    onBack = {},
                    onSetHistoryWindow = {},
                    onSetRecorderIntervalMs = {},
                    onResetGaugeExtrema = {},
                    onStartAppTour = {},
                    onStartCalibration = {},
                    onToggleAutoResume = {},
                    onOpenPremium = {},
                    onToggleAutoPause = {}
                )
            }
        }
        composeRule.onNodeWithText(autoPause).assertIsOff()
        composeRule.onNodeWithText(autoResume).assertIsOff()
        composeRule.onNodeWithText(noAds).assertIsOff()

        composeRule.runOnIdle {
            state.value = SettingsUiState(
                isAutomationPackPurchased = true,
                autoPauseEnabled = true,
                autoResumeEnabled = true
            )
        }
        composeRule.onNodeWithText(autoPause).assertIsOn()
        composeRule.onNodeWithText(autoResume).assertIsOn()
        composeRule.onNodeWithText(noAds).assertIsOff()

        composeRule.runOnIdle {
            state.value = SettingsUiState(
                isPremiumSubscribed = true,
                autoPauseEnabled = true,
                autoResumeEnabled = true
            )
        }
        composeRule.onNodeWithText(autoPause).assertIsOn()
        composeRule.onNodeWithText(autoResume).assertIsOn()
        composeRule.onNodeWithText(noAds).assertIsOn()
    }

    @Test
    fun steppersDisableButtonsAtTheirBounds() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val recorderTitle = context.getString(R.string.settings_recorder_tick_title)
        val historyTitle = context.getString(R.string.settings_history_window_title)

        setContent(
            SettingsUiState(
                recorderIntervalMs = 50,
                historyWindowSeconds = 120
            )
        )

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.settings_decrease_value, recorderTitle)
        ).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.settings_increase_value, recorderTitle)
        ).assertIsEnabled()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.settings_decrease_value, historyTitle)
        ).assertIsEnabled()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.settings_increase_value, historyTitle)
        ).assertIsNotEnabled()
    }

    @Test
    fun premiumTopBarActionIsAlwaysAvailable() {
        var openPremiumCount = 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val description = context.getString(R.string.settings_open_premium)

        setContent(SettingsUiState(), onOpenPremium = { openPremiumCount++ })

        composeRule.onNodeWithContentDescription(description).performClick()
        composeRule.runOnIdle { assertEquals(1, openPremiumCount) }
    }

    @Test
    fun resetRowTargetsGaugeAndExplainsScope() {
        var resetCount = 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resetLabel = context.getString(R.string.settings_reset_gauge_max_values)
        val resetHint = context.getString(R.string.settings_reset_gauge_hint)

        setContent(SettingsUiState(), onResetGaugeExtrema = { resetCount++ })

        composeRule.onNodeWithText(resetLabel).performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, resetCount) }
        composeRule.onNodeWithText(resetHint).assertTextContains("MAX L")
    }

    private fun setContent(
        state: SettingsUiState,
        onOpenPremium: () -> Unit = {},
        onToggleAutoPause: (Boolean) -> Unit = {},
        onToggleAutoResume: (Boolean) -> Unit = {},
        onResetGaugeExtrema: () -> Unit = {}
    ) {
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    state = state,
                    onBack = {},
                    onSetHistoryWindow = {},
                    onSetRecorderIntervalMs = {},
                    onResetGaugeExtrema = onResetGaugeExtrema,
                    onStartAppTour = {},
                    onStartCalibration = {},
                    onToggleAutoResume = onToggleAutoResume,
                    onOpenPremium = onOpenPremium,
                    onToggleAutoPause = onToggleAutoPause
                )
            }
        }
    }
}
