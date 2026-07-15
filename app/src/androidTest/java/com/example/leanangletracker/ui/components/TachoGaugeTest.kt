package com.example.leanangletracker.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TachoGaugeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun longPressAndAccessibilityActionResetGauge() {
        var resetCount = 0
        composeRule.setContent {
            MaterialTheme {
                TachoGauge(
                    currentDeg = 12f,
                    maxLeftDeg = -28f,
                    maxRightDeg = 31f,
                    modifier = Modifier.testTag("gauge"),
                    onResetMaxValues = { resetCount++ }
                )
            }
        }

        val gauge = composeRule.onNodeWithTag("gauge")
        gauge.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
        gauge.performTouchInput { longClick() }
        composeRule.runOnIdle { assertEquals(1, resetCount) }

        gauge.performSemanticsAction(SemanticsActions.OnLongClick) { action -> action() }
        composeRule.runOnIdle { assertEquals(2, resetCount) }
    }
}
