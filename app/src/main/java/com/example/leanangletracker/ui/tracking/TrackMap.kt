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
import com.example.leanangletracker.TrackPoint
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
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
    forceCenterKey: Any? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val points = rideSession.points

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

    // High-performance overlay with pre-calculated color bins and spatial chunking
    val routeOverlay = remember(rideSession.startedAtMs, points) {
        LeanAngleOverlay(points)
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
                if (points.isEmpty()) return false
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

    DisposableEffect(mapView, rideSession.startedAtMs, routeOverlay) {
        mapView.overlays.clear()
        mapView.overlays.add(routeOverlay)
        mapView.overlays.add(marker)
        mapView.overlays.add(tapOverlay)
        mapView.onResume()
        onDispose { mapView.onPause() }
    }

    LaunchedEffect(forceCenterKey) {
        if (forceCenterKey != null) {
            points.getOrNull(selectedIndex)?.let {
                mapView.controller.animateTo(GeoPoint(it.latitude, it.longitude))
            }
        }
    }

    val lastSelectedIndex = remember { mutableIntStateOf(-1) }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            if (points.isEmpty()) return@AndroidView
            val p = points.getOrNull(selectedIndex) ?: points.last()
            val geoPoint = GeoPoint(p.latitude, p.longitude)
            
            if (marker.position?.latitude != geoPoint.latitude || marker.position?.longitude != geoPoint.longitude) {
                marker.position = geoPoint
                // Performance: Only invalidate if the index changed ( scrubbing )
                if (lastSelectedIndex.intValue != selectedIndex) {
                    map.invalidate()
                    lastSelectedIndex.intValue = selectedIndex
                }
            }

            if (isFirstPositioning) {
                map.controller.setCenter(geoPoint)
                isFirstPositioning = false
                onZoomChanged(map.zoomLevelDouble)
            } else {
                keepPointAwayFromBorder(map, geoPoint)
            }
        }
    )
}

/**
 * Performance-optimized Overlay for ride paths.
 * Combines spatial chunking, color binning, and adaptive geographical pruning.
 */
private class LeanAngleOverlay(private val points: List<TrackPoint>) : Overlay() {
    private val paint = Paint().apply {
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val numBins = 16
    private val binColors = IntArray(numBins) { i ->
        getInterpolatedColor((i.toFloat() / (numBins - 1)) * 50f)
    }
    
    // Pre-calculate color bins once per session
    private val pointBins = IntArray(points.size) { i ->
        val absLean = abs(points[i].leanAngleDeg)
        (absLean / (50f / (numBins - 1))).toInt().coerceIn(0, numBins - 1)
    }

    private data class TrackChunk(
        val startIdx: Int,
        val endIdx: Int,
        val minLat: Double, val maxLat: Double,
        val minLon: Double, val maxLon: Double
    ) {
        fun intersects(b: BoundingBox): Boolean {
            return !(minLat > b.latNorth || maxLat < b.latSouth || minLon > b.lonEast || maxLon < b.lonWest)
        }
    }

    private val chunks = mutableListOf<TrackChunk>()
    private val tempPoint = Point()
    private val tempGeoPoint = GeoPoint(0.0, 0.0)
    
    private val lineBuffers = Array(numBins) { FloatArray(4000) } 
    private val lineBufferIndices = IntArray(numBins)

    init {
        val chunkSize = 500
        for (i in points.indices step chunkSize) {
            val end = (i + chunkSize + 1).coerceAtMost(points.size)
            if (end - i < 2) continue
            var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
            var minLon = Double.MAX_VALUE; var maxLon = -Double.MAX_VALUE
            for (j in i until end) {
                val p = points[j]
                if (p.latitude < minLat) minLat = p.latitude
                if (p.latitude > maxLat) maxLat = p.latitude
                if (p.longitude < minLon) minLon = p.longitude
                if (p.longitude > maxLon) maxLon = p.longitude
            }
            chunks.add(TrackChunk(i, end, minLat, maxLat, minLon, maxLon))
        }
    }

    override fun draw(canvas: Canvas, map: MapView, shadow: Boolean) {
        if (shadow || points.isEmpty()) return

        val projection = map.projection
        val viewBounds = map.boundingBox
        val zoom = map.zoomLevelDouble
        val width = canvas.width.toFloat()
        val height = canvas.height.toFloat()

        // Adaptive skip factor based on zoom
        val skip = when {
            zoom >= 16.5 -> 1
            zoom >= 15.0 -> 2
            zoom >= 13.5 -> 4
            zoom >= 12.0 -> 8
            zoom >= 10.5 -> 16
            zoom >= 9.0  -> 32
            zoom >= 7.5  -> 64
            else -> 128
        }

        // Calculate world-coordinate tolerance for current zoom (~2.5 pixels)
        val latTolerance = (viewBounds.latNorth - viewBounds.latSouth) / height * 2.5
        val lonTolerance = (viewBounds.lonEast - viewBounds.lonWest) / width * 2.5

        lineBufferIndices.fill(0)

        for (chunk in chunks) {
            // Frustum Culling: Skip chunks entirely outside the view
            if (!chunk.intersects(viewBounds)) continue

            var lastX = Float.NaN; var lastY = Float.NaN
            var lastLat = Double.NaN; var lastLon = Double.NaN
            var lastBin = -1
            
            var i = chunk.startIdx
            while (i < chunk.endIdx) {
                val p = points[i]
                val currentBin = pointBins[i]

                // PERFORMANCE CRITICAL: Skip projection and draw if movement is sub-pixel 
                // AND color remains the same.
                if (!lastLat.isNaN() && 
                    abs(p.latitude - lastLat) < latTolerance && 
                    abs(p.longitude - lastLon) < lonTolerance &&
                    currentBin == lastBin &&
                    i < chunk.endIdx - 1) {
                    i += skip
                    continue
                }

                tempGeoPoint.setCoords(p.latitude, p.longitude)
                projection.toPixels(tempGeoPoint, tempPoint)
                val currX = tempPoint.x.toFloat()
                val currY = tempPoint.y.toFloat()

                if (!lastX.isNaN()) {
                    val bin = if (currentBin != lastBin) currentBin else lastBin
                    var bufferIdx = lineBufferIndices[bin]
                    
                    if (bufferIdx + 4 > lineBuffers[bin].size) {
                        paint.color = binColors[bin]
                        canvas.drawLines(lineBuffers[bin], 0, bufferIdx, paint)
                        bufferIdx = 0
                    }
                    
                    lineBuffers[bin][bufferIdx++] = lastX
                    lineBuffers[bin][bufferIdx++] = lastY
                    lineBuffers[bin][bufferIdx++] = currX
                    lineBuffers[bin][bufferIdx++] = currY
                    lineBufferIndices[bin] = bufferIdx
                }
                
                lastX = currX; lastY = currY
                lastLat = p.latitude; lastLon = p.longitude
                lastBin = currentBin

                if (i == chunk.endIdx - 1) break
                i += skip
                if (i >= chunk.endIdx) i = chunk.endIdx - 1
            }
        }

        // Draw remaining lines in buffers
        for (b in 0 until numBins) {
            val idx = lineBufferIndices[b]
            if (idx > 0) {
                paint.color = binColors[b]
                canvas.drawLines(lineBuffers[b], 0, idx, paint)
            }
        }
    }
}

private fun getInterpolatedColor(lean: Float): Int {
    val absLean = abs(lean).coerceIn(0f, 50f)
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

private fun keepPointAwayFromBorder(map: MapView, point: GeoPoint, paddingFraction: Double = 0.15) {
    val width = map.width; val height = map.height
    if (width <= 0 || height <= 0) return
    val projection = map.projection ?: return
    val screenPoint = projection.toPixels(point, null) ?: return
    val marginX = width * paddingFraction; val marginY = height * paddingFraction
    if (screenPoint.x < marginX || screenPoint.x > width - marginX ||
        screenPoint.y < marginY || screenPoint.y > height - marginY) {
        map.controller.animateTo(point)
    }
}
