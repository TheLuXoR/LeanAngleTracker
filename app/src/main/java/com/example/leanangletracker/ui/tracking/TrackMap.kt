package com.example.leanangletracker.ui.tracking

import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.leanangletracker.RideSession
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
internal fun OSMTrackMap(
    rideSession: RideSession,
    selectedIndex: Int,
    onMapPointSelected: (Int) -> Unit,
    onZoomChanged: (Double) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val points = remember(rideSession.points) {
        rideSession.points.map { GeoPoint(it.latitude, it.longitude) }
    }

    // Re-center only once when a new session is shown.
    var isFirstPositioning by remember(rideSession.startedAtMs) {
        mutableStateOf(true)
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0)

            addMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean = false

                override fun onZoom(event: ZoomEvent?): Boolean {
                    event?.let { onZoomChanged(it.zoomLevel) }
                    return false
                }
            })
        }
    }

    // Keep overlays persistent instead of recreating them on every recomposition.
    val routeOverlay = remember {
        Polyline().apply {
            outlinePaint.apply {
                color = Color.parseColor("#00B4FF")
                strokeWidth = 12f
                strokeCap = Paint.Cap.ROUND
                isAntiAlias = true
            }
        }
    }

    val marker = remember(mapView) {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = context.getDrawable(android.R.drawable.presence_online)
        }
    }

    val tapOverlay = remember {
        MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                val closest = points.withIndex().minByOrNull { (_, point) ->
                    val dx = point.latitude - p.latitude
                    val dy = point.longitude - p.longitude
                    dx * dx + dy * dy
                }?.index ?: selectedIndex

                onMapPointSelected(closest)
                return true
            }

            override fun longPressHelper(p: GeoPoint?): Boolean = false
        })
    }

    // Rebuild overlays only when the session changes.
    DisposableEffect(mapView, rideSession.startedAtMs) {
        mapView.overlays.clear()
        mapView.overlays.add(routeOverlay)
        mapView.overlays.add(marker)
        mapView.overlays.add(tapOverlay)

        mapView.onResume()
        onDispose {
            mapView.onPause()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            if (points.isEmpty()) {
                return@AndroidView
            }

            routeOverlay.setPoints(points)

            val selectedGeoPoint = points.getOrNull(selectedIndex) ?: points.last()
            marker.position = selectedGeoPoint

            if (isFirstPositioning) {
                map.controller.setCenter(selectedGeoPoint)
                isFirstPositioning = false
                onZoomChanged(map.zoomLevelDouble)
            } else {
                keepPointAwayFromBorder(
                    map = map,
                    point = selectedGeoPoint
                )
            }

            map.invalidate()
        }
    )
}

private fun keepPointAwayFromBorder(
    map: MapView,
    point: GeoPoint,
    horizontalMarginFraction: Double = 0.9,
    verticalMarginFraction: Double = 0.9
) {
    val width = map.width
    val height = map.height

    if (width <= 0 || height <= 0) return

    val projection = map.projection ?: return
    val screenPoint = projection.toPixels(point, null) ?: return

    val marginX = width * horizontalMarginFraction
    val marginY = height * verticalMarginFraction

    val isNearLeft = screenPoint.x < marginX
    val isNearRight = screenPoint.x > width - marginX
    val isNearTop = screenPoint.y < marginY
    val isNearBottom = screenPoint.y > height - marginY

    if (isNearLeft || isNearRight || isNearTop || isNearBottom) {
        // Instant re-center is much faster than animateTo for frequently changing track points.
        map.controller.setCenter(point)
    }
}