package com.example.leanangletracker.ui.tracking

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import androidx.compose.runtime.*
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
import org.osmdroid.views.overlay.Overlay
import kotlin.math.abs

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
    val leans = remember(rideSession.points) {
        rideSession.points.map { it.leanAngleDeg }
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

    // Custom overlay that colors segments by lean angle
    val routeOverlay = remember(rideSession.startedAtMs) {
        LeanAngleOverlay(points, leans)
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

/**
 * Custom Overlay to draw path segments with colors based on lean angle.
 * Efficiently draws line segments using standard Canvas operations.
 */
private class LeanAngleOverlay(
    private val points: List<GeoPoint>,
    private val leanAngles: List<Float>
) : Overlay() {
    private val paint = Paint().apply {
        strokeWidth = 14f
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }
    
    private val p1 = Point()
    private val p2 = Point()

    override fun draw(canvas: Canvas, map: MapView, shadow: Boolean) {
        if (shadow) return
        if (points.size < 2) return

        val projection = map.projection
        val width = canvas.width
        val height = canvas.height

        for (i in 0 until points.size - 1) {
            projection.toPixels(points[i], p1)
            projection.toPixels(points[i + 1], p2)
            
            // Basic viewport clipping for performance
            if ((p1.x < 0 && p2.x < 0) || (p1.x > width && p2.x > width) ||
                (p1.y < 0 && p2.y < 0) || (p1.y > height && p2.y > height)) continue

            paint.color = getInterpolatedColor(leanAngles[i])
            canvas.drawLine(p1.x.toFloat(), p1.y.toFloat(), p2.x.toFloat(), p2.y.toFloat(), paint)
        }
    }

    private fun getInterpolatedColor(lean: Float): Int {
        val absLean = abs(lean).coerceIn(0f, 50f)
        // Matching colors from LeanHistoryGraph: Green (0), Orange (Mid), Red (Extreme)
        return if (absLean < 25f) {
            val ratio = absLean / 25f
            interpolateColor(0xFF00E676.toInt(), 0xFFFF8C00.toInt(), ratio)
        } else {
            val ratio = (absLean - 25f) / 25f
            interpolateColor(0xFFFF8C00.toInt(), 0xFFFF5252.toInt(), ratio)
        }
    }

    private fun interpolateColor(color1: Int, color2: Int, ratio: Float): Int {
        val r = (Color.red(color1) * (1 - ratio) + Color.red(color2) * ratio).toInt()
        val g = (Color.green(color1) * (1 - ratio) + Color.green(color2) * ratio).toInt()
        val b = (Color.blue(color1) * (1 - ratio) + Color.blue(color2) * ratio).toInt()
        return Color.rgb(r, g, b)
    }
}

private fun keepPointAwayFromBorder(
    map: MapView,
    point: GeoPoint,
    paddingFraction: Double = 0.15
) {
    val width = map.width
    val height = map.height

    if (width <= 0 || height <= 0) return

    val projection = map.projection ?: return
    val screenPoint = projection.toPixels(point, null) ?: return

    val marginX = width * paddingFraction
    val marginY = height * paddingFraction

    if (screenPoint.x < marginX || screenPoint.x > width - marginX ||
        screenPoint.y < marginY || screenPoint.y > height - marginY) {
        map.controller.setCenter(point)
    }
}
