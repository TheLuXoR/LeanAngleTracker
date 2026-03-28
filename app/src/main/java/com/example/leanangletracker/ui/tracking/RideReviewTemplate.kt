package com.example.leanangletracker.ui.tracking

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.leanangletracker.RideSession
import com.example.leanangletracker.TrackPoint
import com.example.leanangletracker.ui.components.JogWheel
import com.example.leanangletracker.ui.components.LeanHistoryGraph
import com.example.leanangletracker.ui.theme.TextPrimary
import com.example.leanangletracker.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min
import kotlin.math.pow

@Composable
internal fun RideReviewTemplate(
    rideSession: RideSession,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember(rideSession.startedAtMs) { 
        mutableIntStateOf(rideSession.points.lastIndex.coerceAtLeast(0)) 
    }
    
    var currentZoom by remember { mutableDoubleStateOf(16.0) }
    
    if (rideSession.points.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            Text("Keine GPS-Daten verfügbar", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val selectedPoint = rideSession.points[selectedIndex]
    val allLeanValues = remember(rideSession.points) { rideSession.points.map { it.leanAngleDeg } }

    val visiblePoints = remember(currentZoom, rideSession.points.size) {
        val basePoints = 100.0
        val zoomFactor = 2.0.pow(16.0 - currentZoom)
        (basePoints * zoomFactor).toInt().coerceIn(min(20 ,rideSession.points.size ), rideSession.points.size)
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Side: Map and JogWheel
            Column(
                modifier = Modifier.weight(1.2f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    OSMTrackMap(
                        rideSession = rideSession,
                        selectedIndex = selectedIndex,
                        onMapPointSelected = { selectedIndex = it },
                        onZoomChanged = { currentZoom = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                JogWheel(
                    value = selectedIndex,
                    onValueChange = { selectedIndex = it },
                    range = 0..rideSession.points.lastIndex.coerceAtLeast(0)
                )
            }

            // Right Side: Stats and Graph
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatItem(label = "TIME", value = formatTimeWithTick(selectedIndex, rideSession.points))
                    StatItem(label = "SPEED", value = "${selectedPoint.speedKmh.toInt()} km/h")
                    StatItem(label = "LEAN", value = "${"%.1f".format(selectedPoint.leanAngleDeg)}°")
                }

                LeanHistoryGraph(
                    values = allLeanValues,
                    selectedIndex = selectedIndex,
                    visibleRangePoints = if (currentZoom > 10) visiblePoints else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                )

                Text(
                    text = "Summary: ${"%.2f".format(rideSession.points.size * 0.2)}s recorded.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                OSMTrackMap(
                    rideSession = rideSession,
                    selectedIndex = selectedIndex,
                    onMapPointSelected = { selectedIndex = it },
                    onZoomChanged = { currentZoom = it },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatItem(label = "TIME", value = formatTimeWithTick(selectedIndex, rideSession.points))
                StatItem(label = "SPEED", value = "${selectedPoint.speedKmh.toInt()} km/h")
                StatItem(label = "LEAN", value = "${"%.1f".format(selectedPoint.leanAngleDeg)}°")
            }

            LeanHistoryGraph(
                values = allLeanValues,
                selectedIndex = selectedIndex,
                visibleRangePoints = if (currentZoom > 10) visiblePoints else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )

            JogWheel(
                value = selectedIndex,
                onValueChange = { selectedIndex = it },
                range = 0..rideSession.points.lastIndex.coerceAtLeast(0)
            )
            
            Text(
                text = "Use the jog wheel to review. Summary: ${"%.2f".format(rideSession.points.size * 0.2)}s recorded.",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
    }
}

public fun formatTimeWithTick(index: Int, points: List<TrackPoint>): String {
    val point = points[index]
    val baseTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(point.timestampMs))
    var tick = 1
    val currentSecond = point.timestampMs / 1000
    for (i in index - 1 downTo 0) {
        if (points[i].timestampMs / 1000 == currentSecond) {
            tick++
        } else {
            break
        }
    }
    return "$baseTime.$tick"
}
