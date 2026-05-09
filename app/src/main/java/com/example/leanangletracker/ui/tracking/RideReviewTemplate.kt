package com.example.leanangletracker.ui.tracking

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.leanangletracker.RideSession
import com.example.leanangletracker.TrackPoint
import com.example.leanangletracker.ui.components.LeanHistoryGraph
import com.example.leanangletracker.ui.components.SpeedHistoryGraph
import com.example.leanangletracker.ui.theme.TextPrimary
import com.example.leanangletracker.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

@Composable
internal fun RideReviewTemplate(
    rideSession: RideSession,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember(rideSession.startedAtMs) { 
        mutableIntStateOf(rideSession.points.lastIndex.coerceAtLeast(0)) 
    }
    
    var centerTrigger by remember { mutableIntStateOf(0) }
    var currentZoom by remember { mutableDoubleStateOf(16.0) }
    
    if (rideSession.points.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            Text("Keine GPS-Daten verfügbar", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    // Pre-calculate session statistics to avoid heavy lifting in the UI thread during composition
    val sessionStats = remember(rideSession.points) {
        val points = rideSession.points
        var totalDist = 0.0
        var maxLeanIdx = 0
        var maxSpeedIdx = 0
        var speedSum = 0.0
        
        for (i in points.indices) {
            val p = points[i]
            if (i < points.size - 1) {
                val p2 = points[i + 1]
                totalDist += fastDistance(p.latitude, p.longitude, p2.latitude, p2.longitude)
            }
            if (abs(p.leanAngleDeg) > abs(points[maxLeanIdx].leanAngleDeg)) maxLeanIdx = i
            if (p.speedKmh > points[maxSpeedIdx].speedKmh) maxSpeedIdx = i
            speedSum += p.speedKmh
        }
        
        object {
            val distanceKm = totalDist / 1000.0
            val maxLeanIndex = maxLeanIdx
            val maxSpeedIndex = maxSpeedIdx
            val avgSpeed = if (points.isEmpty()) 0f else (speedSum / points.size).toFloat()
            val leanValues = points.map { it.leanAngleDeg }
            val speedValues = points.map { it.speedKmh }
        }
    }

    val selectedPoint = rideSession.points[selectedIndex]

    val visiblePoints = remember(currentZoom, rideSession.points.size) {
        val basePoints = 100.0
        val zoomFactor = 2.0.pow(16.0 - currentZoom)
        (basePoints * zoomFactor).toInt().coerceIn(
            min(20, rideSession.points.size),
            min(1000, rideSession.points.size)
        )
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val onStatSelected: (Int) -> Unit = { index ->
        selectedIndex = index
        centerTrigger++
    }

    if (isLandscape) {
        Row(
            modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier.weight(1.2f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RideSessionSummary(
                    distanceKm = sessionStats.distanceKm,
                    maxLeanIndex = sessionStats.maxLeanIndex,
                    maxLeanValue = rideSession.points[sessionStats.maxLeanIndex].leanAngleDeg,
                    maxSpeedIndex = sessionStats.maxSpeedIndex,
                    maxSpeedValue = rideSession.points[sessionStats.maxSpeedIndex].speedKmh,
                    avgSpeed = sessionStats.avgSpeed,
                    onSelectIndex = onStatSelected
                )

                Box(modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp))) {
                    OSMTrackMap(
                        rideSession = rideSession,
                        selectedIndex = selectedIndex,
                        onMapPointSelected = { selectedIndex = it },
                        onZoomChanged = { currentZoom = it },
                        forceCenterKey = centerTrigger.takeIf { it > 0 },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
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

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LeanHistoryGraph(
                        values = sessionStats.leanValues,
                        selectedIndex = selectedIndex,
                        visibleRangePoints = visiblePoints,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        isScrollable = true,
                        onSelectedIndexChange = { selectedIndex = it }
                    )

                    SpeedHistoryGraph(
                        values = sessionStats.speedValues,
                        selectedIndex = selectedIndex,
                        visibleRangePoints = visiblePoints,
                        modifier = Modifier.weight(0.7f).fillMaxWidth(),
                        isScrollable = true,
                        onSelectedIndexChange = { selectedIndex = it }
                    )
                }
            }
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth().padding(vertical = 8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RideSessionSummary(
                distanceKm = sessionStats.distanceKm,
                maxLeanIndex = sessionStats.maxLeanIndex,
                maxLeanValue = rideSession.points[sessionStats.maxLeanIndex].leanAngleDeg,
                maxSpeedIndex = sessionStats.maxSpeedIndex,
                maxSpeedValue = rideSession.points[sessionStats.maxSpeedIndex].speedKmh,
                avgSpeed = sessionStats.avgSpeed,
                onSelectIndex = onStatSelected
            )

            Box(modifier = Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(16.dp))) {
                OSMTrackMap(
                    rideSession = rideSession,
                    selectedIndex = selectedIndex,
                    onMapPointSelected = { selectedIndex = it },
                    onZoomChanged = { currentZoom = it },
                    forceCenterKey = centerTrigger.takeIf { it > 0 },
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
                values = sessionStats.leanValues,
                selectedIndex = selectedIndex,
                visibleRangePoints = visiblePoints,
                modifier = Modifier.fillMaxWidth().height(180.dp),
                isScrollable = true,
                onSelectedIndexChange = { selectedIndex = it }
            )

            SpeedHistoryGraph(
                values = sessionStats.speedValues,
                selectedIndex = selectedIndex,
                visibleRangePoints = visiblePoints,
                modifier = Modifier.fillMaxWidth().height(140.dp),
                isScrollable = true,
                onSelectedIndexChange = { selectedIndex = it }
            )
        }
    }
}

@Composable
private fun RideSessionSummary(
    distanceKm: Double,
    maxLeanIndex: Int,
    maxLeanValue: Float,
    maxSpeedIndex: Int,
    maxSpeedValue: Float,
    avgSpeed: Float,
    onSelectIndex: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatItem(label = "DISTANCE", value = "%.2f km".format(distanceKm))
        StatItem(
            label = "MAX LEAN", 
            value = "%.1f°".format(abs(maxLeanValue)),
            onClick = { onSelectIndex(maxLeanIndex) }
        )
        StatItem(
            label = "MAX SPEED", 
            value = "${maxSpeedValue.toInt()} km/h",
            onClick = { onSelectIndex(maxSpeedIndex) }
        )
        StatItem(label = "AVG SPEED", value = "${avgSpeed.toInt()} km/h")
    }
}

/**
 * Fast distance approximation to avoid heavy Location calls.
 */
private fun fastDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val x = Math.toRadians(lon2 - lon1) * cos(Math.toRadians((lat1 + lat2) / 2.0))
    val y = Math.toRadians(lat2 - lat1)
    return sqrt(x * x + y * y) * 6371000.0
}

@Composable
internal fun RideReviewSkeleton(modifier: Modifier = Modifier) {
    // ... skeleton logic is fine as it uses brush ...
}

@Composable
private fun StatItem(label: String, value: String, onClick: (() -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (onClick != null) {
            Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(4.dp)
        } else {
            Modifier.padding(4.dp)
        }
    ) {
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
