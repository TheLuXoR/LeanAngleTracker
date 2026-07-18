package com.example.leanangletracker.ui.tracking

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
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
        val action = context.getString(R.string.auto_resume_premium_shortcut_action)

        composeRule.setContent {
            MaterialTheme {
                AutoResumePremiumShortcut(
                    visible = true,
                    onOpenPremium = { openCount++ }
                )
            }
        }

        composeRule.onNodeWithText(title).assertExists()
        composeRule.onNodeWithText(action).performClick()
        composeRule.runOnIdle { assertEquals(1, openCount) }
    }
}
