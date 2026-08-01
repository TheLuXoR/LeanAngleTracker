package de.hasselmeyer.leanangle.ui.tracking

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.hasselmeyer.leanangle.GpxImportUiState
import de.hasselmeyer.leanangle.RideSummary
import de.hasselmeyer.leanangle.ui.theme.LeanAngleTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RideHistoryImportTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyHistoryOffersImportInToolbarAndContent() {
        var importClicks = 0
        composeRule.setContent {
            LeanAngleTrackerTheme {
                RideHistoryScreen(
                    rideHistory = emptyList(),
                    onSelectRide = {},
                    onBack = {},
                    onDeleteRide = {},
                    onImportGpx = { importClicks++ }
                )
            }
        }

        composeRule.onNodeWithTag(RIDE_HISTORY_IMPORT_ACTION_TAG).assertIsEnabled()
        composeRule
            .onNodeWithTag(RIDE_HISTORY_EMPTY_IMPORT_ACTION_TAG)
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, importClicks)
        }
    }

    @Test
    fun loadingDisablesBothImportActions() {
        composeRule.setContent {
            LeanAngleTrackerTheme {
                RideHistoryScreen(
                    rideHistory = emptyList(),
                    onSelectRide = {},
                    onBack = {},
                    onDeleteRide = {},
                    onImportGpx = {},
                    importState = GpxImportUiState(isImporting = true)
                )
            }
        }

        composeRule.onNodeWithTag(RIDE_HISTORY_IMPORT_ACTION_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(RIDE_HISTORY_EMPTY_IMPORT_ACTION_TAG).assertIsNotEnabled()
    }

    @Test
    fun selectionModeHidesImportAction() {
        val ride = RideSummary(
            rideId = 7L,
            startedAtMs = 1_700_000_000_000L,
            endedAtMs = 1_700_000_001_000L,
            name = "Selection ride",
            pointCount = 10,
            isFinished = true
        )
        composeRule.setContent {
            LeanAngleTrackerTheme {
                RideHistoryScreen(
                    rideHistory = listOf(ride),
                    onSelectRide = {},
                    onBack = {},
                    onDeleteRide = {},
                    onImportGpx = {}
                )
            }
        }

        composeRule.onNodeWithText("Selection ride", substring = true)
            .performTouchInput { longClick() }

        composeRule.onNodeWithTag(RIDE_HISTORY_IMPORT_ACTION_TAG).assertDoesNotExist()
    }
}
