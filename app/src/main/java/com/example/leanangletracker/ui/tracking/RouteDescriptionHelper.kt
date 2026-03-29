package com.example.leanangletracker.ui.tracking

import android.content.Context
import android.location.Geocoder
import com.example.leanangletracker.RideSession
import java.util.*
import kotlin.math.*

fun calculateRouteDescription(context: Context, session: RideSession): String? {
    if (session.points.isEmpty()) return null
    
    val geocoder = Geocoder(context, Locale.getDefault())
    val start = session.points.first()
    val end = session.points.last()
    
    val startName = getAddressName(geocoder, start.latitude, start.longitude)
    val endName = getAddressName(geocoder, end.latitude, end.longitude)
    
    if (startName == null || endName == null) return null
    
    // Total distance calculation
    var totalDistanceMeters = 0f
    for (i in 0 until session.points.size - 1) {
        totalDistanceMeters += distanceMeters(
            session.points[i].latitude, session.points[i].longitude,
            session.points[i+1].latitude, session.points[i+1].longitude
        )
    }
    val distanceKm = totalDistanceMeters / 1000f
    val distanceStr = String.format(Locale.getDefault(), "%.1f km", distanceKm)
    
    val routeNames = mutableListOf<String>()
    routeNames.add(startName)
    var lastAddedName = startName
    
    // 10 minutes gap detection: collect all intermediate stations
    for (i in 0 until session.points.size - 1) {
        val p1 = session.points[i]
        val p2 = session.points[i+1]
        if (p2.timestampMs - p1.timestampMs > 10 * 60 * 1000) {
            val gapName = getAddressName(geocoder, p2.latitude, p2.longitude)
            if (gapName != null && gapName != lastAddedName) {
                routeNames.add(gapName)
                lastAddedName = gapName
            }
        }
    }
    
    // Add end name if it's different from the last added name
    if (endName != lastAddedName) {
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
        addr.locality ?: addr.subLocality ?: addr.thoroughfare ?: addr.featureName
    } catch (e: Exception) {
        null
    }
}

private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val r = 6371000.0 // Earth radius in meters
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return (r * c).toFloat()
}
