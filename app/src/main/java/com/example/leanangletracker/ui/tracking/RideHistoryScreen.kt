package com.example.leanangletracker.ui.tracking

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.leanangletracker.R
import com.example.leanangletracker.RideSession
import com.example.leanangletracker.RideSummary
import com.example.leanangletracker.ui.components.admob.loadInterstitial
import com.example.leanangletracker.ui.components.admob.showInterstitial
import com.example.leanangletracker.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RideHistoryScreen(
    rideHistory: List<RideSummary>,
    expandedRides: Map<Long, RideSession>,
    onLoadDetails: (Long) -> Unit,
    onBack: () -> Unit,
    onDeleteRide: (RideSummary) -> Unit,
    lastSavedRideId: Long? = null,
    onUpdateName: (RideSummary, String) -> Unit = { _, _ -> },
    onCombineRides: (List<RideSummary>) -> Unit = {}
) {
    val listState = rememberLazyListState()
    var expandedIndex by rememberSaveable { mutableIntStateOf(if (lastSavedRideId != null) 0 else -1) }
    var selectedSessionIds by remember { mutableStateOf(setOf<Long>()) }
    val isSelectionMode = selectedSessionIds.isNotEmpty()

    BackHandler(isSelectionMode) {
        selectedSessionIds = emptySet()
    }

    LaunchedEffect(lastSavedRideId) {
        if (lastSavedRideId != null && rideHistory.isNotEmpty()) {
            val index = rideHistory.indexOfFirst { it.startedAtMs == lastSavedRideId }
            if (index != -1) {
                expandedIndex = index
                onLoadDetails(lastSavedRideId)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (isSelectionMode) {
                        Text(stringResource(R.string.ride_history_selected_count, selectedSessionIds.size))
                    } else {
                        Text(stringResource(R.string.ride_history_title), style = MaterialTheme.typography.titleLarge)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (isSelectionMode) selectedSessionIds = emptySet() else onBack() }) {
                        Icon(
                            if (isSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    if (isSelectionMode && selectedSessionIds.size >= 2) {
                        Button(
                            onClick = {
                                val toCombine = rideHistory.filter { it.startedAtMs in selectedSessionIds }
                                onCombineRides(toCombine)
                                selectedSessionIds = emptySet()
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Merge, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.ride_history_action_combine))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        if (rideHistory.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.ride_history_empty), style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(rideHistory, key = { _, s -> s.startedAtMs }) { index, summary ->
                    val isExpanded = expandedIndex == index
                    val isSelected = summary.startedAtMs in selectedSessionIds
                    val isNewlySaved = summary.startedAtMs == lastSavedRideId
                    val fullSession = expandedRides[summary.startedAtMs]
                    
                    RideHistoryItem(
                        summary = summary,
                        fullSession = fullSession,
                        isExpanded = isExpanded,
                        isSelected = isSelected,
                        isNewlySaved = isNewlySaved,
                        onToggle = { 
                            if (isSelectionMode) {
                                selectedSessionIds = if (isSelected) selectedSessionIds - summary.startedAtMs else selectedSessionIds + summary.startedAtMs
                            } else {
                                if (!isExpanded) onLoadDetails(summary.startedAtMs)
                                expandedIndex = if (isExpanded) -1 else index 
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                selectedSessionIds = setOf(summary.startedAtMs)
                            }
                        },
                        onDelete = { onDeleteRide(summary) },
                        onUpdateName = { onUpdateName(summary, it) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RideHistoryItem(
    summary: RideSummary,
    fullSession: RideSession?,
    isExpanded: Boolean,
    isSelected: Boolean,
    isNewlySaved: Boolean,
    onToggle: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onUpdateName: (String) -> Unit
) {
    val context = LocalContext.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    
    var isEditingName by remember { mutableStateOf(false) }
    var editedName by remember(summary.name) { mutableStateOf(summary.name ?: "") }

    val containerColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            isNewlySaved -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            isExpanded -> MaterialTheme.colorScheme.surface
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        label = "item_color"
    )

    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            delay(150)
            bringIntoViewRequester.bringIntoView()
            delay(200)
            bringIntoViewRequester.bringIntoView()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onToggle,
                        onLongClick = onLongClick
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.CheckCircle, 
                        contentDescription = stringResource(R.string.ride_history_selected), 
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    if (isEditingName && isExpanded) {
                        TextField(
                            value = editedName,
                            onValueChange = { editedName = it },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            placeholder = { Text(stringResource(R.string.ride_history_name_placeholder)) },
                            trailingIcon = {
                                IconButton(onClick = { 
                                    onUpdateName(editedName)
                                    isEditingName = false 
                                }) {
                                    Icon(Icons.Default.Check, contentDescription = stringResource(R.string.action_save))
                                }
                            },
                            singleLine = true
                        )
                    } else {
                        val titleText = if (summary.name.isNullOrBlank()) {
                            formatDate(summary.startedAtMs)
                        } else {
                            "${summary.name}: ${formatDate(summary.startedAtMs)}"
                        }
                        
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = stringResource(R.string.ride_history_points_recorded, summary.pointCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                
                Row {
                    if (isExpanded && !isSelected) {
                        IconButton(onClick = { isEditingName = !isEditingName }) {
                            Icon(
                                if (isEditingName) Icons.Default.Check else Icons.Default.Edit,
                                contentDescription = stringResource(R.string.ride_history_action_edit_name),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        // Open with...
                        IconButton(
                            enabled = fullSession != null,
                            onClick = {
                                loadInterstitial(context) { interstitialAd ->
                                    showInterstitial(context, interstitialAd) {
                                        fullSession?.let { openGpxFile(context, it) }
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "Open in...", tint = if (fullSession != null) MaterialTheme.colorScheme.primary else TextSecondary)
                        }
                        // Share...
                        IconButton(
                            enabled = fullSession != null,
                            onClick = {
                                loadInterstitial(context) { interstitialAd ->
                                    showInterstitial(context, interstitialAd) {
                                        fullSession?.let { shareGpxFile(context, it) }
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = if (fullSession != null) MaterialTheme.colorScheme.primary else TextSecondary)
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.ride_history_action_delete), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (!isSelected) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.ride_history_action_details),
                            tint = TextSecondary
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded && !isSelected,
                enter = expandVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)),
                exit = shrinkVertically(animationSpec = tween(300, easing = FastOutSlowInEasing))
            ) {
                if (fullSession == null) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                } else {
                    RideReviewTemplate(rideSession = fullSession)
                }
            }
        }
    }
}

private fun formatDate(timestampMs: Long): String =
    SimpleDateFormat("EEE, MMM d, HH:mm", Locale.getDefault()).format(Date(timestampMs))

private fun openGpxFile(context: Context, session: RideSession) {
    val gpxContent = buildGpxString(context, session)
    val fileName = "ride_${session.startedAtMs}.gpx"
    
    try {
        val cacheFile = File(context.cacheDir, fileName)
        cacheFile.writeText(gpxContent)

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cacheFile
        )

        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/gpx+xml")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(viewIntent, "Open GPX with..."))
    } catch (e: Exception) {
        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun shareGpxFile(context: Context, session: RideSession) {
    val gpxContent = buildGpxString(context, session)
    val fileName = "ride_${session.startedAtMs}.gpx"
    
    try {
        val cacheFile = File(context.cacheDir, fileName)
        cacheFile.writeText(gpxContent)

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cacheFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share GPX with..."))
    } catch (e: Exception) {
        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun buildGpxString(context: Context, rideSession: RideSession): String {
    return buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        appendLine("<gpx version=\"1.1\" creator=\"LeanAngleTracker\" xmlns=\"http://www.topografix.com/GPX/1/1\">")
        appendLine("  <trk>")
        appendLine("    <name>${rideSession.name ?: context.getString(R.string.ride_history_default_name, rideSession.startedAtMs)}</name>")
        appendLine("    <trkseg>")
        rideSession.points.forEach { point ->
            appendLine("      <trkpt lat=\"${point.latitude}\" lon=\"${point.longitude}\">")
            appendLine("        <time>${iso8601(point.timestampMs)}</time>")
            appendLine("        <extensions>")
            appendLine("          <speedKmh>${point.speedKmh}</speedKmh>")
            appendLine("          <leanDeg>${point.leanAngleDeg}</leanDeg>")
            appendLine("        </extensions>")
            appendLine("      </trkpt>")
        }
        appendLine("    </trkseg>")
        appendLine("  </trk>")
        appendLine("</gpx>")
    }
}

private fun iso8601(timestampMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(timestampMs))
