package de.hasselmeyer.leanangle.ui.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import de.hasselmeyer.leanangle.R
import de.hasselmeyer.leanangle.ui.theme.LeanAngleTrackerTheme
import kotlin.math.roundToInt

internal const val TRACK_OVERVIEW_SCRUBBER_TAG = "trackOverviewScrubber"
internal const val DEFAULT_TRACK_DETAIL_ZOOM = 17.0

internal enum class TrackMapNavigationMode {
    IDLE,
    OVERVIEW,
    DETAIL
}

internal fun trackIndexToProgress(selectedIndex: Int, pointCount: Int): Float {
    if (pointCount <= 1) return 0f
    val lastIndex = pointCount - 1
    return selectedIndex.coerceIn(0, lastIndex).toFloat() / lastIndex
}

internal fun progressToTrackIndex(progress: Float, pointCount: Int): Int {
    if (pointCount <= 1) return 0
    val normalizedProgress = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    return (normalizedProgress * (pointCount - 1)).roundToInt()
}

@Composable
internal fun TrackOverviewScrubber(
    pointCount: Int,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    onScrubFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (pointCount < 2) return
    val scrubberDescription = stringResource(R.string.track_review_scrubber_description)

    Column(modifier = modifier) {
        Slider(
            value = trackIndexToProgress(selectedIndex, pointCount),
            onValueChange = { progress ->
                onSelectedIndexChange(progressToTrackIndex(progress, pointCount))
            },
            onValueChangeFinished = onScrubFinished,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = scrubberDescription }
                .testTag(TRACK_OVERVIEW_SCRUBBER_TAG),
            valueRange = 0f..1f
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.track_review_start),
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = stringResource(R.string.track_review_end),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun TrackOverviewScrubberPreview() {
    LeanAngleTrackerTheme {
        TrackOverviewScrubber(
            pointCount = 101,
            selectedIndex = 42,
            onSelectedIndexChange = {},
            onScrubFinished = {}
        )
    }
}
