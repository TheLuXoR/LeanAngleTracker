package com.example.leanangletracker.ui.tracking

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
import org.osmdroid.views.overlay.Polyline

@Composable
internal fun OSMTrackMap(
    rideSession: RideSession,
    selectedIndex: Int,
    onMapPointSelected: (Int) -> Unit,
    onZoomChanged: (Double) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val points = remember(rideSession.points) { rideSession.points.map { GeoPoint(it.latitude, it.longitude) } }
    val context = LocalContext.current
    
    // Track if this is the first time the map is positioned for this session
    var isFirstPositioning by remember(rideSession.startedAtMs) { mutableStateOf(true) }

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

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose { mapView.onPause() }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            map.overlays.clear()

            if (points.isNotEmpty()) {
                val routeOverlay = Polyline().apply {
                    setPoints(points)
                    outlinePaint.apply {
                        color = android.graphics.Color.parseColor("#00B4FF")
                        strokeWidth = 12f
                        strokeCap = android.graphics.Paint.Cap.ROUND
                    }
                }
                map.overlays.add(routeOverlay)

                val selectedGeoPoint = points.getOrNull(selectedIndex) ?: points.last()
                val marker = Marker(map).apply {
                    position = selectedGeoPoint
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = context.getDrawable(android.R.drawable.presence_online)
                }
                map.overlays.add(marker)

                val tapOverlay = MapEventsOverlay(object : MapEventsReceiver {
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
                map.overlays.add(tapOverlay)

                if (isFirstPositioning) {
                    map.controller.setCenter(selectedGeoPoint)
                    isFirstPositioning = false
                    onZoomChanged(16.0)
                } else {
                    map.controller.animateTo(selectedGeoPoint)
                }
            }

            map.invalidate()
        }
    )
}
