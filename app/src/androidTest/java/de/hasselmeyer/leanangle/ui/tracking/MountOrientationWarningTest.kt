package de.hasselmeyer.leanangle.ui.tracking

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import de.hasselmeyer.leanangle.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MountOrientationWarningTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun upsideDownWarningExplainsInvalidValuesAndOffersRecalibration() {
        var recalibrationRequests = 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val warning = context.getString(R.string.tracking_warning_upside_down)
        val recalibrate = context.getString(R.string.action_recalibrate)

        composeRule.setContent {
            MaterialTheme {
                MountOrientationWarning(
                    visible = true,
                    isUpsideDown = true,
                    onStartCalibration = { recalibrationRequests++ }
                )
            }
        }

        composeRule.onNodeWithText(warning).assertExists()
        composeRule.onNodeWithText(recalibrate).performClick()
        composeRule.runOnIdle { assertEquals(1, recalibrationRequests) }
    }
}
