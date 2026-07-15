package com.example.leanangletracker.ui.tracking

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.leanangletracker.R
import com.example.leanangletracker.ui.components.GpsStatsDashboard
import com.example.leanangletracker.ui.components.LeanHistoryGraph
import com.example.leanangletracker.ui.components.TachoGauge
import com.example.leanangletracker.ui.components.buttons.PauseButton
import com.example.leanangletracker.ui.components.buttons.RecordButton
import com.example.leanangletracker.ui.theme.AccentGreen
import com.example.leanangletracker.ui.theme.PrimaryOrange
import com.example.leanangletracker.ui.theme.SecondaryBlue

@Composable
internal fun AppTourPagePreview(pageIndex: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.testTag("appTourPreview$pageIndex"),
        contentAlignment = Alignment.Center
    ) {
        when (pageIndex) {
            0 -> GaugeTourPreview()
            1 -> RecordingTourPreview()
            2 -> PauseTourPreview()
            3 -> LiveDataTourPreview()
            4 -> HistoryTourPreview()
            else -> ManageTourPreview()
        }
    }
}

@Composable
private fun GaugeTourPreview() {
    TachoGauge(
        currentDeg = 24f,
        maxLeftDeg = -38f,
        maxRightDeg = 42f,
        modifier = Modifier.fillMaxSize(),
        onResetMaxValues = {}
    )
}

@Composable
private fun RecordingTourPreview() {
    TourPreviewSurface {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.GpsFixed, contentDescription = null, tint = AccentGreen)
                Text(
                    stringResource(R.string.app_tour_preview_gps_ready),
                    fontWeight = FontWeight.Bold,
                    color = AccentGreen
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PreviewAction(
                    label = stringResource(R.string.app_tour_preview_start),
                    icon = {
                        RecordButton(
                            onRecord = {},
                            onStopRecord = {},
                            isPaused = false,
                            isRecording = false
                        )
                    }
                )
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                PreviewAction(
                    label = stringResource(R.string.app_tour_preview_stop),
                    icon = {
                        RecordButton(
                            onRecord = {},
                            onStopRecord = {},
                            isPaused = false,
                            isRecording = true
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun PauseTourPreview() {
    TourPreviewSurface {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PauseButton(onClick = {}, isPaused = true, isVisible = true)
                Text(
                    stringResource(R.string.app_tour_preview_paused),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TourStatusPill(
                    text = stringResource(R.string.app_tour_preview_auto_pause),
                    icon = Icons.Default.PauseCircle,
                    color = PrimaryOrange
                )
                TourStatusPill(
                    text = stringResource(R.string.app_tour_preview_auto_resume),
                    icon = Icons.Default.PlayCircle,
                    color = AccentGreen
                )
            }
        }
    }
}

@Composable
private fun LiveDataTourPreview() {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val values = listOf(-5f, -18f, -32f, -20f, 4f, 22f, 37f, 25f, 8f)

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LeanHistoryGraph(
            values = values,
            selectedIndex = values.lastIndex,
            showCursorLine = false,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
        GpsStatsDashboard(
            speedKmh = 82f,
            distanceKm = 12.4f,
            elapsedTimeMs = 54 * 60 * 1_000L,
            isLandscape = isLandscape,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun HistoryTourPreview() {
    TourPreviewSurface {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                stringResource(R.string.app_tour_preview_sample_ride),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                    .padding(10.dp)
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val route = Path().apply {
                        moveTo(size.width * 0.08f, size.height * 0.72f)
                        cubicTo(
                            size.width * 0.28f, size.height * 0.12f,
                            size.width * 0.52f, size.height * 0.92f,
                            size.width * 0.68f, size.height * 0.38f
                        )
                        cubicTo(
                            size.width * 0.77f, size.height * 0.08f,
                            size.width * 0.9f, size.height * 0.5f,
                            size.width * 0.94f, size.height * 0.22f
                        )
                    }
                    drawPath(
                        path = route,
                        color = SecondaryBlue,
                        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawCircle(AccentGreen, radius = 7.dp.toPx(), center = Offset(size.width * 0.08f, size.height * 0.72f))
                    drawCircle(PrimaryOrange, radius = 7.dp.toPx(), center = Offset(size.width * 0.94f, size.height * 0.22f))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PreviewStat(stringResource(R.string.app_tour_preview_distance), "12,4 km")
                PreviewStat(stringResource(R.string.app_tour_preview_max_lean), "42,0°")
                PreviewStat(stringResource(R.string.app_tour_preview_points), "1.248")
            }
        }
    }
}

@Composable
private fun ManageTourPreview() {
    TourPreviewSurface {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(2) { index ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentGreen)
                        Text(
                            text = stringResource(R.string.app_tour_preview_selected_ride, index + 1),
                            modifier = Modifier.padding(start = 10.dp).weight(1f),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(if (index == 0) "18,2 km" else "11,7 km")
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PreviewActionLabel(Icons.AutoMirrored.Filled.MergeType, stringResource(R.string.app_tour_preview_combine))
                PreviewActionLabel(Icons.Default.Delete, stringResource(R.string.app_tour_preview_delete))
                PreviewActionLabel(Icons.Default.Settings, stringResource(R.string.app_tour_preview_settings))
            }
        }
    }
}

@Composable
private fun TourPreviewSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        content = content
    )
}

@Composable
private fun PreviewAction(label: String, icon: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        icon()
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TourStatusPill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: androidx.compose.ui.graphics.Color) {
    Surface(shape = CircleShape, color = color.copy(alpha = 0.16f)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PreviewStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PreviewActionLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
