package com.example.leanangletracker.ui.tracking

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.example.leanangletracker.AppTourUiState
import com.example.leanangletracker.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppTourDialogsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun interactiveTourRequiresGaugeResetAndRecordingStepsBeforeFinishing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var state by mutableStateOf(AppTourUiState(offerPending = true))
        var finished = false

        composeRule.setContent {
            MaterialTheme {
                AppTourDialogs(
                    state = state,
                    onAcceptOffer = {
                        state = AppTourUiState(isActive = true, currentPage = 0)
                    },
                    onDeclineOffer = {
                        finished = true
                        state = AppTourUiState()
                    },
                    onPreviousPage = {
                        state = state.copy(currentPage = state.currentPage - 1)
                    },
                    onNextPage = {
                        state = state.copy(currentPage = state.currentPage + 1)
                    },
                    onFinish = {
                        finished = true
                        state = AppTourUiState()
                    }
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.app_tour_prompt_start)).performClick()

        val nextLabel = context.getString(R.string.app_tour_next)
        val pageTitles = listOf(
            R.string.app_tour_gauge_title,
            R.string.app_tour_recording_title,
            R.string.app_tour_live_data_title,
            R.string.app_tour_history_title,
            R.string.app_tour_manage_title
        )

        composeRule.onNodeWithText(context.getString(pageTitles[0])).assertExists()
        composeRule.onNodeWithText(nextLabel).assertIsNotEnabled()
        composeRule.onNodeWithText(
            context.getString(R.string.app_tour_gauge_demo_running)
        ).assertExists()
        composeRule.mainClock.advanceTimeBy(5_000)
        composeRule.onNodeWithText(
            context.getString(R.string.app_tour_gauge_long_press)
        ).assertExists()
        composeRule.onNodeWithTag("appTourGauge").performTouchInput { longClick() }
        composeRule.onNodeWithText(context.getString(R.string.app_tour_gauge_reset_success)).assertExists()
        composeRule.onNodeWithText(nextLabel).assertIsEnabled().performClick()

        composeRule.onNodeWithText(context.getString(pageTitles[1])).assertExists()
        composeRule.onNodeWithText(nextLabel).assertIsNotEnabled()
        composeRule.onNodeWithText(
            context.getString(R.string.app_tour_recording_not_started)
        ).assertExists()
        composeRule.onNodeWithTag("appTourRecordStart").performTouchInput { click() }
        composeRule.onNodeWithText(
            context.getString(R.string.app_tour_recording_gps_searching)
        ).assertExists()
        composeRule.onNodeWithTag("appTourGpsRainbow").assertExists()
        composeRule.mainClock.advanceTimeBy(2_500)
        composeRule.onNodeWithText(context.getString(R.string.app_tour_recording_active)).assertExists()
        composeRule.onNodeWithText(nextLabel).assertIsNotEnabled()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithTag("appTourPause").performTouchInput { click() }
        composeRule.onNodeWithText(context.getString(R.string.app_tour_preview_paused)).assertExists()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithTag("appTourResume").performTouchInput { click() }
        composeRule.onNodeWithText(context.getString(R.string.app_tour_recording_active)).assertExists()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithTag("appTourRecordStop").performTouchInput { click() }
        composeRule.onNodeWithText(context.getString(R.string.app_tour_recording_saved)).assertExists()
        composeRule.onNodeWithText(nextLabel).assertIsEnabled().performClick()

        composeRule.onNodeWithText(context.getString(pageTitles[2])).assertExists()
        composeRule.onNodeWithTag("appTourPreview2").assertExists()
        composeRule.onNodeWithText(nextLabel).performClick()

        composeRule.onNodeWithText(context.getString(pageTitles[3])).assertExists()
        composeRule.onNodeWithText(nextLabel).assertIsNotEnabled()
        composeRule.onNodeWithTag("appTourMaxLean").performClick()
        composeRule.onNodeWithText(
            context.getString(R.string.app_tour_history_jump_max_lean)
        ).assertExists()
        composeRule.onNodeWithText(nextLabel).assertIsNotEnabled()
        composeRule.onNodeWithTag("appTourVMax").performClick()
        composeRule.onNodeWithText(
            context.getString(R.string.app_tour_history_jump_v_max)
        ).assertExists()
        composeRule.onNodeWithText(nextLabel).assertIsEnabled().performClick()

        composeRule.onNodeWithText(context.getString(pageTitles[4])).assertExists()
        composeRule.onNodeWithTag("appTourPreview4").assertExists()
        composeRule.onNodeWithText(context.getString(R.string.app_tour_finish)).performClick()

        composeRule.runOnIdle { assertTrue(finished) }
    }
}
