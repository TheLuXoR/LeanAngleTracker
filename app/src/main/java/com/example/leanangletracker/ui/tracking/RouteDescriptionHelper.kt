package com.example.leanangletracker.ui.tracking

import android.content.Context
import android.location.Geocoder
import com.example.leanangletracker.RideSession
import java.util.*
import kotlin.math.*

/**
 * Calculates a human-readable description of the route.
 * Optimization: Uses a fast distance approximation and samples points for long rides.
 */
fun calculateRouteDescription(context: Context, session: RideSession): String? {
    val points = session.points
    if (points.isEmpty()) return null
    
    val geocoder = Geocoder(context, Locale.getDefault())
    val start = points.first()
    val end = points.last()
    
    // 1. Calculate total distance efficiently
    // Sample points for distance calculation if the list is huge to keep UI responsive.
    // 2000 points are plenty for a highly accurate distance estimate.
    val distSkip = (points.size / 2000).coerceAtLeast(1)
    var totalDistanceMeters = 0.0
    for (i in 0 until points.size - distSkip step distSkip) {
        val p1 = points[i]
        val p2 = points[i + distSkip]
        if (p1.lapIndex == p2.lapIndex) {
            totalDistanceMeters += fastDistanceMeters(
                p1.latitude,
                p1.longitude,
                p2.latitude,
                p2.longitude
            )
        }
    }
    
    val distanceKm = totalDistanceMeters / 1000.0
    val distanceStr = String.format(Locale.getDefault(), "%.1f km", distanceKm)
    
    // 2. Resolve Names (Priority: Start and End)
    val startName = getAddressName(geocoder, start.latitude, start.longitude)
    val endName = getAddressName(geocoder, end.latitude, end.longitude)
    
    if (startName == null && endName == null) return "$distanceStr Route"
    
    val routeNames = mutableListOf<String>()
    if (startName != null) routeNames.add(startName)
    var lastAddedName = startName
    
    // 3. Detect major stops (> 15 mins)
    // Limit Geocoder calls to avoid blocking the main thread.
    var geoCalls = 0
    val gapSkip = (points.size / 500).coerceAtLeast(1)
    for (i in 0 until points.size - gapSkip step gapSkip) {
        val p1 = points[i]
        val p2 = points[i + gapSkip]
        if (p2.timestampMs - p1.timestampMs > 15 * 60 * 1000) { // 15 min gap
            if (geoCalls < 3) {
                val gapName = getAddressName(geocoder, p2.latitude, p2.longitude)
                if (gapName != null && gapName != lastAddedName && gapName != endName) {
                    routeNames.add(gapName)
                    lastAddedName = gapName
                    geoCalls++
                }
            }
        }
    }
    
    if (endName != null && endName != lastAddedName) {
        routeNames.add(endName)
    }
    
    val routeStr = routeNames.joinToString(" -> ")
    return "$distanceStr: $routeStr"
}

private fun getAddressName(geocoder: Geocoder, lat: Double, lon: Double): String? {
    return try {
        @Suppress("DEPRECATION")
        val addresses = geocoder.getFromLocation(lat, lon, 1)
        if (addresses.isNullOrEmpty()) return null
        val addr = addresses[0]
        // Prefer city name, then district, then street
        addr.locality ?: addr.subLocality ?: addr.adminArea ?: addr.thoroughfare
    } catch (e: Exception) {
        null
    }
}

/**
 * Fast equirectangular distance approximation. 
 * Sufficiently accurate for route descriptions and much faster than Haversine.
 */
private fun fastDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val x = Math.toRadians(lon2 - lon1) * cos(Math.toRadians((lat1 + lat2) / 2.0))
    val y = Math.toRadians(lat2 - lat1)
    return sqrt(x * x + y * y) * 6371000.0
}
