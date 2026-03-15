package com.example.leanangletracker.ui.tracking

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.example.leanangletracker.RideSession
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun TrackReviewScreen(
    rideSession: RideSession,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    Configuration.getInstance().userAgentValue = context.packageName

    if (rideSession.points.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Keine GPS-Daten verfügbar", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = onBack) { Text("Zurück") }
        }
        return
    }

    var selectedIndex by rememberSaveable(rideSession.startedAtMs) { mutableStateOf(rideSession.points.lastIndex) }
    var sliderValue by rememberSaveable(rideSession.startedAtMs) { mutableFloatStateOf(rideSession.points.lastIndex.toFloat()) }
    val selectedPoint = rideSession.points[selectedIndex]

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        if (uri != null) exportGpx(context, uri, rideSession)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Streckenanalyse", style = MaterialTheme.typography.headlineSmall)

        OSMTrackMap(
            rideSession = rideSession,
            selectedIndex = selectedIndex,
            onMapPointSelected = { index ->
                selectedIndex = index
                sliderValue = index.toFloat()
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Slider(
            value = sliderValue,
            onValueChange = {
                sliderValue = it
                selectedIndex = it.toInt().coerceIn(0, rideSession.points.lastIndex)
            },
            valueRange = 0f..rideSession.points.lastIndex.toFloat()
        )

        Text("Zeit: ${formatTime(selectedPoint.timestampMs)} · Speed: ${"%.1f".format(selectedPoint.speedKmh)} km/h · Lean: ${"%.1f".format(selectedPoint.leanAngleDeg)}°")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBack, modifier = Modifier.weight(1f).height(52.dp)) { Text("Zurück") }
            Button(
                onClick = { exportLauncher.launch("lean-angle-track-${rideSession.startedAtMs}.gpx") },
                modifier = Modifier.weight(1f).height(52.dp)
            ) { Text("GPX exportieren") }
        }

        Text("Vorbereitet für Rennstrecke: jeder Punkt enthält lapIndex (derzeit 0).", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun OSMTrackMap(
    rideSession: RideSession,
    selectedIndex: Int,
    onMapPointSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val points = remember(rideSession.points) { rideSession.points.map { GeoPoint(it.latitude, it.longitude) } }
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
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

            val routeOverlay = Polyline().apply {
                setPoints(points)
                outlinePaint.strokeWidth = 9f
            }
            map.overlays.add(routeOverlay)

            val selectedGeoPoint = points[selectedIndex]
            val marker = Marker(map).apply {
                position = selectedGeoPoint
                title = "Ausgewählter Punkt"
                snippet = "${rideSession.points[selectedIndex].speedKmh.toInt()} km/h, Lean ${"%.1f".format(rideSession.points[selectedIndex].leanAngleDeg)}°"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
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

            map.controller.setZoom(17.0)
            map.controller.animateTo(selectedGeoPoint)

            map.invalidate()
        }
    )
}

private fun exportGpx(context: Context, uri: Uri, rideSession: RideSession) {
    val gpx = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        appendLine("<gpx version=\"1.1\" creator=\"LeanAngleTracker\" xmlns=\"http://www.topografix.com/GPX/1/1\">")
        appendLine("  <trk>")
        appendLine("    <name>Ride ${rideSession.startedAtMs}</name>")
        appendLine("    <trkseg>")
        rideSession.points.forEach { point ->
            appendLine("      <trkpt lat=\"${point.latitude}\" lon=\"${point.longitude}\">")
            appendLine("        <time>${iso8601(point.timestampMs)}</time>")
            appendLine("        <extensions>")
            appendLine("          <speedKmh>${point.speedKmh}</speedKmh>")
            appendLine("          <leanDeg>${point.leanAngleDeg}</leanDeg>")
            appendLine("          <lapIndex>${point.lapIndex}</lapIndex>")
            appendLine("        </extensions>")
            appendLine("      </trkpt>")
        }
        appendLine("    </trkseg>")
        appendLine("  </trk>")
        appendLine("</gpx>")
    }
    context.contentResolver.openOutputStream(uri)?.use { it.write(gpx.toByteArray()) }
}

private fun formatTime(timestampMs: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestampMs))

private fun iso8601(timestampMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date(timestampMs))
