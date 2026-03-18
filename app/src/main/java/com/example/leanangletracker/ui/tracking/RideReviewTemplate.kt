package com.example.leanangletracker.ui.tracking

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.leanangletracker.RideSession
import com.example.leanangletracker.ui.theme.SecondaryBlue
import com.example.leanangletracker.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.*
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

    // Map zoom to a number of visible points in the graph.
    // At zoom 16 (default), maybe show 100 points. 
    // Zooming in (higher zoom) shows fewer points (more detail).
    // Zooming out (lower zoom) shows more points.
    val visiblePoints = remember(currentZoom, rideSession.points.size) {
        val basePoints = 100.0
        val zoomFactor = 2.0.pow(16.0 - currentZoom)
        (basePoints * zoomFactor).toInt().coerceIn(20, rideSession.points.size)
    }

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
            StatItem(label = "TIME", value = formatTime(selectedPoint.timestampMs))
            StatItem(label = "SPEED", value = "${selectedPoint.speedKmh.toInt()} km/h")
            StatItem(label = "LEAN", value = "${"%.1f".format(selectedPoint.leanAngleDeg)}°")
        }

        // The graph now shows a subset centered on selectedIndex, 
        // with the window size controlled by map zoom.
        LeanHistoryGraph(
            values = allLeanValues,
            selectedIndex = selectedIndex,
            visibleRangePoints = if (currentZoom > 10) visiblePoints else null, // Show all if zoomed out very far
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

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.titleMedium, color = SecondaryBlue, fontWeight = FontWeight.Bold)
    }
}

private fun formatTime(timestampMs: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestampMs))
