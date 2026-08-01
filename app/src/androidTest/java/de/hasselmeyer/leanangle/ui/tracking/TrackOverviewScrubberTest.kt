package de.hasselmeyer.leanangle.ui.tracking

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.hasselmeyer.leanangle.R
import de.hasselmeyer.leanangle.RideSession
import de.hasselmeyer.leanangle.TrackPoint
import de.hasselmeyer.leanangle.ui.theme.LeanAngleTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackOverviewScrubberTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun semanticsProgressSelectsPositionOnCompleteTrack() {
        var observedIndex = -1
        composeRule.setContent {
            var selectedIndex by remember { mutableIntStateOf(0) }
            LeanAngleTrackerTheme {
                TrackOverviewScrubber(
                    pointCount = 101,
                    selectedIndex = selectedIndex,
                    onSelectedIndexChange = {
                        selectedIndex = it
                        observedIndex = it
                    },
                    onScrubFinished = {}
                )
            }
        }

        composeRule
            .onNodeWithTag(TRACK_OVERVIEW_SCRUBBER_TAG)
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(0.75f)
            }

        composeRule.runOnIdle {
            assertEquals(75, observedIndex)
        }
    }

    @Test
    fun exposesLocalizedTrackPositionDescription() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val description = context.getString(R.string.track_review_scrubber_description)

        composeRule.setContent {
            LeanAngleTrackerTheme {
                TrackOverviewScrubber(
                    pointCount = 10,
                    selectedIndex = 4,
                    onSelectedIndexChange = {},
                    onScrubFinished = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription(description).assertExists()
    }

    @Test
    fun longTrackScrubSynchronizesCompleteRideReview() {
        val points = (0..1_000).map { index ->
            TrackPoint(
                timestampMs = 1_700_000_000_000L + index * 200L,
                latitude = 52.5 + index * 0.00001,
                longitude = 13.4 + index * 0.00001,
                speedKmh = (index % 151).toFloat(),
                leanAngleDeg = ((index % 81) - 40).toFloat(),
                leanFreshnessMs = 0L,
                gpsFreshnessMs = 0L,
                hasFreshGps = true
            )
        }
        val rideSession = RideSession(
            rideId = 42L,
            startedAtMs = points.first().timestampMs,
            endedAtMs = points.last().timestampMs,
            points = points,
            trackLengthMeters = 12_500f
        )

        composeRule.setContent {
            LeanAngleTrackerTheme {
                RideReviewTemplate(
                    rideSession = rideSession,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        val map = composeRule.onNodeWithTag(TRACK_MAP_TAG)
        val mapBoundsBefore = map.fetchSemanticsNode().boundsInRoot
        val attributionBounds = composeRule
            .onNodeWithTag(TRACK_MAP_ATTRIBUTION_TAG)
            .fetchSemanticsNode()
            .boundsInRoot
        assertEquals(mapBoundsBefore.right, attributionBounds.right, 1f)
        assertEquals(mapBoundsBefore.bottom, attributionBounds.bottom, 1f)

        map.performTouchInput { swipeUp(durationMillis = 500) }
        composeRule.waitForIdle()
        val mapTopAfterGesture = composeRule
            .onNodeWithTag(TRACK_MAP_TAG)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        assertEquals(mapBoundsBefore.top, mapTopAfterGesture, 1f)

        composeRule
            .onNodeWithTag(TRACK_OVERVIEW_SCRUBBER_TAG)
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(0.25f)
            }

        composeRule.onNodeWithText(formatTimeWithTick(250, points)).assertExists()
    }
}
