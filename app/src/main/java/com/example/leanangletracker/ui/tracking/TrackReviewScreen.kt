package com.example.leanangletracker.ui.tracking

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.leanangletracker.R
import com.example.leanangletracker.RideSession
import com.example.leanangletracker.ui.theme.SecondaryBlue
import com.example.leanangletracker.ui.theme.TextSecondary
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrackReviewScreen(
    rideSession: RideSession,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    Configuration.getInstance().userAgentValue = context.packageName

    if (rideSession.points.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Keine GPS-Daten verfügbar", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onBack) { Text("Zurück") }
            }
        }
        return
    }

    var selectedIndex by rememberSaveable(rideSession.startedAtMs) { mutableIntStateOf(rideSession.points.lastIndex.coerceAtLeast(0)) }
    val selectedPoint = rideSession.points[selectedIndex]
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        if (uri != null) exportGpx(context, uri, rideSession)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ride Analysis", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { exportLauncher.launch("lean-angle-track-${rideSession.startedAtMs}.gpx") }) {
                        Icon(Icons.Default.Share, contentDescription = "Export GPX")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                OSMTrackMap(
                    rideSession = rideSession,
                    selectedIndex = selectedIndex,
                    onMapPointSelected = { selectedIndex = it },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatItem(label = "TIME", value = formatTime(selectedPoint.timestampMs))
                        StatItem(label = "SPEED", value = "${selectedPoint.speedKmh.toInt()} km/h")
                        StatItem(label = "LEAN", value = "${"%.1f".format(selectedPoint.leanAngleDeg)}°")
                    }

                    Slider(
                        value = selectedIndex.toFloat(),
                        onValueChange = { selectedIndex = it.toInt().coerceIn(0, rideSession.points.lastIndex) },
                        valueRange = 0f..rideSession.points.lastIndex.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                    )

                    Text(
                        text = "Slide to review specific parts of your ride. Export as GPX to use in external telemetry tools.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.titleLarge, color = SecondaryBlue, fontWeight = FontWeight.Bold)
    }
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
