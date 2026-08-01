package com.example.leanangletracker.ui.tracking

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import com.example.leanangletracker.R
import com.example.leanangletracker.RideSession
import com.example.leanangletracker.TrackPoint
import com.example.leanangletracker.map.OpenStreetMapConfig
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import kotlin.math.abs

private const val MAP_ZOOM_ANIMATION_DURATION_MS = 800L
internal const val TRACK_MAP_TAG = "trackMap"
internal const val TRACK_MAP_ATTRIBUTION_TAG = "trackMapAttribution"

@Composable
internal fun OSMTrackMap(
    rideSession: RideSession,
    selectedIndex: Int,
    onMapPointSelected: (Int) -> Unit,
    onZoomChanged: (Double) -> Unit = {},
    navigationMode: TrackMapNavigationMode = TrackMapNavigationMode.IDLE,
    navigationRequestKey: Int = 0,
    detailZoom: Double = DEFAULT_TRACK_DETAIL_ZOOM,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val uriHandler = LocalUriHandler.current
    val overviewPaddingPx = with(LocalDensity.current) { 48.dp.roundToPx() }
    val points = rideSession.points
    val currentOnZoomChanged by rememberUpdatedState(onZoomChanged)

    var isFirstPositioning by remember(rideSession.startedAtMs) {
        mutableStateOf(true)
    }

    val mapView = remember {
        object : MapView(context) {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
                }
                return super.dispatchTouchEvent(event)
            }
        }.apply {
            setTileSource(OpenStreetMapConfig.tileSource)
            setMultiTouchControls(true)
            controller.setZoom(DEFAULT_TRACK_DETAIL_ZOOM)

            addMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean = false
                override fun onZoom(event: ZoomEvent?): Boolean {
                    event?.let { currentOnZoomChanged(it.zoomLevel) }
                    return false
                }
            })
        }
    }

    // High-performance overlay with pre-calculated color bins and spatial chunking
    val routeOverlay = remember(rideSession.startedAtMs, points) {
        LeanAngleOverlay(points)
    }

    val marker = remember(mapView, resources) {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = resources.getDrawable(android.R.drawable.presence_online, null)
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

    val lastSelectedIndex = remember { mutableIntStateOf(-1) }

    LaunchedEffect(navigationRequestKey, navigationMode, points) {
        if (navigationRequestKey <= 0 || points.isEmpty()) return@LaunchedEffect

        mapView.doOnLayout {
            when (navigationMode) {
                TrackMapNavigationMode.OVERVIEW -> {
                    fitTrackOverview(
                        map = mapView,
                        points = points,
                        paddingPx = overviewPaddingPx
                    )
                }

                TrackMapNavigationMode.DETAIL -> {
                    points.getOrNull(selectedIndex)?.let { point ->
                        mapView.controller.setCenter(GeoPoint(point.latitude, point.longitude))
                        animateMapZoom(mapView, detailZoom)
                    }
                }

                TrackMapNavigationMode.IDLE -> Unit
            }
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier
                .fillMaxSize()
                .testTag(TRACK_MAP_TAG),
            update = { map ->
                if (points.isEmpty()) return@AndroidView
                val p = points.getOrNull(selectedIndex) ?: points.last()
                val geoPoint = GeoPoint(p.latitude, p.longitude)
                val selectionChanged = lastSelectedIndex.intValue != selectedIndex

                if (marker.position?.latitude != geoPoint.latitude || marker.position?.longitude != geoPoint.longitude) {
                    marker.position = geoPoint
                    // Performance: Only invalidate if the index changed (scrubbing).
                    if (selectionChanged) {
                        map.invalidate()
                    }
                }

                if (isFirstPositioning) {
                    map.controller.setCenter(geoPoint)
                    isFirstPositioning = false
                    currentOnZoomChanged(map.zoomLevelDouble)
                } else if (selectionChanged) {
                    when (navigationMode) {
                        TrackMapNavigationMode.OVERVIEW -> Unit
                        TrackMapNavigationMode.DETAIL -> {
                            map.controller.setCenter(geoPoint)
                        }
                        TrackMapNavigationMode.IDLE -> keepPointAwayFromBorder(map, geoPoint)
                    }
                }
                lastSelectedIndex.intValue = selectedIndex
            }
        )

        Text(
            text = stringResource(R.string.map_attribution),
            color = ComposeColor(0xFF263238),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .testTag(TRACK_MAP_ATTRIBUTION_TAG)
                .clip(RoundedCornerShape(4.dp))
                .background(ComposeColor.White.copy(alpha = 0.9f))
                .clickable {
                    uriHandler.openUri(OpenStreetMapConfig.COPYRIGHT_URL)
                }
                .padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

private fun fitTrackOverview(
    map: MapView,
    points: List<TrackPoint>,
    paddingPx: Int
) {
    if (points.isEmpty()) return

    var minLatitude = Double.POSITIVE_INFINITY
    var maxLatitude = Double.NEGATIVE_INFINITY
    var minLongitude = Double.POSITIVE_INFINITY
    var maxLongitude = Double.NEGATIVE_INFINITY

    points.forEach { point ->
        if (!point.latitude.isFinite() || !point.longitude.isFinite()) return@forEach
        minLatitude = minOf(minLatitude, point.latitude)
        maxLatitude = maxOf(maxLatitude, point.latitude)
        minLongitude = minOf(minLongitude, point.longitude)
        maxLongitude = maxOf(maxLongitude, point.longitude)
    }

    if (!minLatitude.isFinite() || !minLongitude.isFinite()) return

    val center = GeoPoint(
        (minLatitude + maxLatitude) / 2.0,
        (minLongitude + maxLongitude) / 2.0
    )
    val latitudeSpan = maxLatitude - minLatitude
    val longitudeSpan = maxLongitude - minLongitude

    if (latitudeSpan < 1e-7 && longitudeSpan < 1e-7) {
        map.controller.animateTo(
            center,
            DEFAULT_TRACK_DETAIL_ZOOM,
            MAP_ZOOM_ANIMATION_DURATION_MS
        )
        return
    }

    val minimumSpan = 1e-5
    val latitudePadding = if (latitudeSpan < minimumSpan) (minimumSpan - latitudeSpan) / 2.0 else 0.0
    val longitudePadding = if (longitudeSpan < minimumSpan) (minimumSpan - longitudeSpan) / 2.0 else 0.0
    val bounds = BoundingBox(
        maxLatitude + latitudePadding,
        maxLongitude + longitudePadding,
        minLatitude - latitudePadding,
        minLongitude - longitudePadding
    )
    map.zoomToBoundingBox(
        bounds,
        true,
        paddingPx,
        map.maxZoomLevel,
        MAP_ZOOM_ANIMATION_DURATION_MS
    )
}

private fun animateMapZoom(map: MapView, targetZoom: Double) {
    val safeTargetZoom = if (targetZoom.isFinite()) {
        targetZoom.coerceIn(map.minZoomLevel, map.maxZoomLevel)
    } else {
        DEFAULT_TRACK_DETAIL_ZOOM
    }
    if (abs(map.zoomLevelDouble - safeTargetZoom) > 0.01) {
        map.controller.zoomTo(safeTargetZoom, MAP_ZOOM_ANIMATION_DURATION_MS)
    }
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
