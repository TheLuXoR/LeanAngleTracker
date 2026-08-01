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

class TrackingPermissionDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun explainsBothPermissionsBeforeContinuing() {
        var confirmCount = 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val title = context.getString(R.string.tracking_permission_dialog_title)
        val locationReason = context.getString(R.string.tracking_permission_location_body)
        val notificationReason = context.getString(R.string.tracking_permission_notification_body)
        val continueLabel = context.getString(R.string.tracking_permission_continue)

        composeRule.setContent {
            MaterialTheme {
                TrackingPermissionDialog(
                    onConfirm = { confirmCount++ },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText(title).assertExists()
        composeRule.onNodeWithText(locationReason).assertExists()
        composeRule.onNodeWithText(notificationReason).assertExists()
        composeRule.onNodeWithText(continueLabel).performClick()
        composeRule.runOnIdle { assertEquals(1, confirmCount) }
    }
}
