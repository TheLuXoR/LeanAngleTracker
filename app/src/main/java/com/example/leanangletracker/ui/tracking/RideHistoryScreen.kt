package com.example.leanangletracker.ui.tracking

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.leanangletracker.RideSession
import com.example.leanangletracker.ui.theme.SecondaryBlue
import com.example.leanangletracker.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RideHistoryScreen(
    rideHistory: List<RideSession>,
    onBack: () -> Unit,
    onDeleteRide: (RideSession) -> Unit,
    lastSavedRideId: Long? = null,
    onUpdateName: (RideSession, String) -> Unit = { _, _ -> },
    onCombineRides: (List<RideSession>) -> Unit = {}
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
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (isSelectionMode) {
                        Text("${selectedSessionIds.size} ausgewählt")
                    } else {
                        Text("Ride History", style = MaterialTheme.typography.titleLarge)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (isSelectionMode) selectedSessionIds = emptySet() else onBack() }) {
                        Icon(
                            if (isSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = "Back"
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
                            Text("Zusammenführen")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        if (rideHistory.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Noch keine Aufzeichnungen.", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
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
                itemsIndexed(rideHistory, key = { _, s -> s.startedAtMs }) { index, session ->
                    val isExpanded = expandedIndex == index
                    val isSelected = session.startedAtMs in selectedSessionIds
                    val isNewlySaved = session.startedAtMs == lastSavedRideId
                    
                    RideHistoryItem(
                        session = session,
                        isExpanded = isExpanded,
                        isSelected = isSelected,
                        isNewlySaved = isNewlySaved,
                        onToggle = { 
                            if (isSelectionMode) {
                                selectedSessionIds = if (isSelected) selectedSessionIds - session.startedAtMs else selectedSessionIds + session.startedAtMs
                            } else {
                                expandedIndex = if (isExpanded) -1 else index 
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                selectedSessionIds = setOf(session.startedAtMs)
                            }
                        },
                        onDelete = { onDeleteRide(session) },
                        onUpdateName = { onUpdateName(session, it) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RideHistoryItem(
    session: RideSession,
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
    
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        if (uri != null) exportGpx(context, uri, session)
    }

    var isEditingName by remember { mutableStateOf(false) }
    var editedName by remember(session.name) { mutableStateOf(session.name ?: "") }

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
            // Wait for expansion animation to start/proceed before requesting view
            delay(150)
            bringIntoViewRequester.bringIntoView()
            // Second call after animation is mostly done to ensure full visibility
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
                        contentDescription = "Selected", 
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
                            placeholder = { Text("Fahrt Name") },
                            trailingIcon = {
                                IconButton(onClick = { 
                                    onUpdateName(editedName)
                                    isEditingName = false 
                                }) {
                                    Icon(Icons.Default.Check, contentDescription = "Speichern")
                                }
                            },
                            singleLine = true
                        )
                    } else {
                        val titleText = if (session.name.isNullOrBlank()) {
                            formatDate(session.startedAtMs)
                        } else {
                            "${session.name}: ${formatDate(session.startedAtMs)}"
                        }
                        
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "${session.points.size} Wegpunkte aufgezeichnet",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                
                Row {
                    if (isExpanded && !isSelected) {
                        IconButton(onClick = { isEditingName = !isEditingName }) {
                            Icon(
                                if (isEditingName) Icons.Default.Check else Icons.Default.Edit,
                                contentDescription = "Name bearbeiten",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { exportLauncher.launch("ride-${session.startedAtMs}.gpx") }) {
                            Icon(Icons.Default.Share, contentDescription = "Exportieren", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (!isSelected) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Details",
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
                RideReviewTemplate(rideSession = session)
            }
        }
    }
}

private fun formatDate(timestampMs: Long): String =
    SimpleDateFormat("EEE, MMM d, HH:mm", Locale.getDefault()).format(Date(timestampMs))

private fun exportGpx(context: Context, uri: Uri, rideSession: RideSession) {
    val gpx = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        appendLine("<gpx version=\"1.1\" creator=\"LeanAngleTracker\" xmlns=\"http://www.topografix.com/GPX/1/1\">")
        appendLine("  <trk>")
        appendLine("    <name>${rideSession.name ?: "Ride ${rideSession.startedAtMs}"}</name>")
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
    context.contentResolver.openOutputStream(uri)?.use { it.write(gpx.toByteArray()) }
}

private fun iso8601(timestampMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(timestampMs))
