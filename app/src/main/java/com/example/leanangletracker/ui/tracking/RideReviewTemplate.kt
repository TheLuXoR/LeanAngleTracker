package com.example.leanangletracker.ui.tracking

import android.content.res.Configuration
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import java.util.*
import kotlin.math.*

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

    // Performance: Pre-calculate values lists only when points change
    val allLeanValues = remember(rideSession.points) { rideSession.points.map { it.leanAngleDeg } }
    val allSpeedValues = remember(rideSession.points) { rideSession.points.map { it.speedKmh } }

    // Adaptive viewport size based on zoom level.
    // Capped at 1500 points (~5 mins at 5Hz) to keep a readable summary window 
    // even when the map is zoomed out to the max.
    val visibleRangeCount = remember(currentZoom, rideSession.points.size) {
        val basePoints = 200.0
        val zoomFactor = 2.0.pow(16.0 - currentZoom)
        val desired = (basePoints * zoomFactor).toInt()
        
        val minP = min(60, rideSession.points.size)
        val maxP = min(500, rideSession.points.size)
        
        if (minP >= maxP) rideSession.points.size
        else desired.coerceIn(minP, maxP)
    }

    val selectedPoint = rideSession.points[selectedIndex]
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
                RideSessionSummary(rideSession, onSelectIndex = onStatSelected)

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
                        values = allLeanValues,
                        selectedIndex = selectedIndex,
                        visibleRangePoints = visibleRangeCount,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        isScrollable = true,
                        onSelectedIndexChange = { selectedIndex = it }
                    )

                    SpeedHistoryGraph(
                        values = allSpeedValues,
                        selectedIndex = selectedIndex,
                        visibleRangePoints = visibleRangeCount,
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
            RideSessionSummary(rideSession, onSelectIndex = onStatSelected)

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
                values = allLeanValues,
                selectedIndex = selectedIndex,
                visibleRangePoints = visibleRangeCount,
                modifier = Modifier.fillMaxWidth().height(180.dp),
                isScrollable = true,
                onSelectedIndexChange = { selectedIndex = it }
            )

            SpeedHistoryGraph(
                values = allSpeedValues,
                selectedIndex = selectedIndex,
                visibleRangePoints = visibleRangeCount,
                modifier = Modifier.fillMaxWidth().height(140.dp),
                isScrollable = true,
                onSelectedIndexChange = { selectedIndex = it }
            )
        }
    }
}

@Composable
internal fun RideReviewSkeleton(modifier: Modifier = Modifier) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    val shimmerColors = listOf(
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim)
    )

    if (isLandscape) {
        Row(
            modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier.weight(1.2f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    repeat(4) { SkeletonStatItem(brush) }
                }
                Box(modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)).background(brush))
            }

            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    repeat(3) { SkeletonStatItem(brush) }
                }
                Box(modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)).background(brush))
            }
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                repeat(4) { SkeletonStatItem(brush) }
            }
            Box(modifier = Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(16.dp)).background(brush))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                repeat(3) { SkeletonStatItem(brush) }
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1.3f).clip(RoundedCornerShape(16.dp)).background(brush))
            Box(modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)).background(brush))
        }
    }
}

@Composable
private fun SkeletonStatItem(brush: Brush) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.width(40.dp).height(10.dp).clip(RoundedCornerShape(2.dp)).background(brush))
        Box(modifier = Modifier.width(60.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).background(brush))
    }
}

@Composable
private fun RideSessionSummary(rideSession: RideSession, onSelectIndex: (Int) -> Unit) {
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
                val x = Math.toRadians(p2.longitude - p.longitude) * cos(Math.toRadians((p.latitude + p2.latitude) / 2.0))
                val y = Math.toRadians(p2.latitude - p.latitude)
                totalDist += sqrt(x * x + y * y) * 6371000.0
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
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatItem(label = "DISTANCE", value = "%.2f km".format(sessionStats.distanceKm))
        StatItem(
            label = "MAX LEAN", 
            value = "%.1f°".format(abs(rideSession.points[sessionStats.maxLeanIndex].leanAngleDeg)),
            onClick = { onSelectIndex(sessionStats.maxLeanIndex) }
        )
        StatItem(
            label = "MAX SPEED", 
            value = "${rideSession.points[sessionStats.maxSpeedIndex].speedKmh.toInt()} km/h",
            onClick = { onSelectIndex(sessionStats.maxSpeedIndex) }
        )
        StatItem(label = "AVG SPEED", value = "${sessionStats.avgSpeed.toInt()} km/h")
    }
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
