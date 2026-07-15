package com.example.leanangletracker.ui.tracking

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    fun acceptedTourNavigatesAllSixPagesAndFinishes() {
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

        val pageTitles = listOf(
            R.string.app_tour_gauge_title,
            R.string.app_tour_recording_title,
            R.string.app_tour_pause_title,
            R.string.app_tour_live_data_title,
            R.string.app_tour_history_title,
            R.string.app_tour_manage_title
        )
        pageTitles.forEachIndexed { index, titleRes ->
            composeRule.onNodeWithText(context.getString(titleRes)).assertExists()
            composeRule.onNodeWithTag("appTourPreview$index").assertExists()
            val actionRes = if (index == pageTitles.lastIndex) R.string.app_tour_finish else R.string.app_tour_next
            composeRule.onNodeWithText(context.getString(actionRes)).performClick()
        }

        composeRule.runOnIdle { assertTrue(finished) }
    }
}
