package de.hasselmeyer.leanangle.ui.legal

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.hasselmeyer.leanangle.R
import de.hasselmeyer.leanangle.ui.theme.LeanAngleTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegalDocumentScreenTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun privacyPolicyShowsControllerAndLocalDataDisclosure() {
        composeRule.setContent {
            LeanAngleTrackerTheme {
                LegalDocumentScreen(
                    document = LegalDocument.PRIVACY,
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.legal_privacy_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.privacy_controller_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.privacy_local_data_title))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun termsShowMeasurementAndLiabilityLimits() {
        composeRule.setContent {
            LeanAngleTrackerTheme {
                LegalDocumentScreen(
                    document = LegalDocument.TERMS,
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.legal_terms_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.terms_measurement_title))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.terms_liability_title))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun openSourceScreenLinksToGeneratedNotices() {
        composeRule.setContent {
            LeanAngleTrackerTheme {
                LegalDocumentScreen(
                    document = LegalDocument.OPEN_SOURCE,
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.settings_open_source_licenses))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.oss_summary_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.oss_open_generated))
            .performScrollTo()
            .assertIsDisplayed()
    }
}
