package com.example.leanangletracker.ui.tracking

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.leanangletracker.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AutoResumePremiumShortcutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun visibleShortcutOpensPremiumFeature() {
        var openCount = 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val title = context.getString(R.string.auto_resume_premium_shortcut_title)
        composeRule.mainClock.autoAdvance = false

        composeRule.setContent {
            MaterialTheme {
                AutoResumePremiumShortcut(
                    displayRequestId = 1,
                    onOpenPremium = { openCount++ }
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithText(title).assertExists()
        composeRule.onNodeWithTag(AUTO_RESUME_ACTION_TAG).performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle { assertEquals(1, openCount) }
    }

    @Test
    fun shortcutExpiresAfterTenSeconds() {
        var expiredCount = 0
        composeRule.mainClock.autoAdvance = false

        composeRule.setContent {
            MaterialTheme {
                AutoResumePremiumShortcut(
                    displayRequestId = 1,
                    onOpenPremium = {},
                    onDismiss = { expiredCount++ }
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(9_000)
        composeRule.onNodeWithTag(AUTO_RESUME_SHORTCUT_TAG).assertExists()
        composeRule.onNodeWithTag(AUTO_RESUME_COUNTDOWN_TAG).assertExists()

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithTag(AUTO_RESUME_SHORTCUT_TAG).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, expiredCount) }
    }

    @Test
    fun pinnedShortcutKeepsActionAndCanBeDismissed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val title = context.getString(R.string.auto_resume_premium_shortcut_title)
        var openCount = 0
        var dismissCount = 0
        composeRule.mainClock.autoAdvance = false

        composeRule.setContent {
            MaterialTheme {
                AutoResumePremiumShortcut(
                    displayRequestId = 1,
                    onOpenPremium = { openCount++ },
                    onDismiss = { dismissCount++ }
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithText(title).performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag(AUTO_RESUME_COUNTDOWN_TAG).assertDoesNotExist()

        composeRule.mainClock.advanceTimeBy(11_000)
        composeRule.onNodeWithTag(AUTO_RESUME_SHORTCUT_TAG).assertExists()
        composeRule.onNodeWithTag(AUTO_RESUME_ACTION_TAG).performClick()
        composeRule.runOnIdle { assertEquals(1, openCount) }

        composeRule.onNodeWithTag(AUTO_RESUME_CLOSE_TAG).performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithTag(AUTO_RESUME_SHORTCUT_TAG).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, dismissCount) }
    }
}
