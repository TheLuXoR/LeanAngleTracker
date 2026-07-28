package com.example.leanangletracker.ui.tracking

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.leanangletracker.GpxImportUiState
import com.example.leanangletracker.R
import com.example.leanangletracker.RideSummary
import com.example.leanangletracker.ui.theme.LeanAngleTrackerTheme
import com.example.leanangletracker.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RideHistoryScreen(
    rideHistory: List<RideSummary>,
    onSelectRide: (Long) -> Unit,
    onBack: () -> Unit,
    onDeleteRide: (RideSummary) -> Unit,
    onImportGpx: () -> Unit,
    importState: GpxImportUiState = GpxImportUiState(),
    onImportErrorConsumed: () -> Unit = {},
    onCombineRides: (List<RideSummary>) -> Unit = {}
) {
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedSessionIds by remember { mutableStateOf(setOf<Long>()) }
    val isSelectionMode = selectedSessionIds.isNotEmpty()
    val importErrorMessage = importState.errorResId?.let { stringResource(it) }

    LaunchedEffect(importState.errorResId, importErrorMessage) {
        if (importErrorMessage != null) {
            snackbarHostState.showSnackbar(importErrorMessage)
            onImportErrorConsumed()
        }
    }

    BackHandler(isSelectionMode) {
        selectedSessionIds = emptySet()
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
                                val toCombine = rideHistory.filter { it.rideId in selectedSessionIds }
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
                    } else if (!isSelectionMode) {
                        IconButton(
                            onClick = onImportGpx,
                            enabled = !importState.isImporting,
                            modifier = Modifier.testTag(RIDE_HISTORY_IMPORT_ACTION_TAG)
                        ) {
                            if (importState.isImporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.UploadFile,
                                    contentDescription = stringResource(
                                        R.string.ride_history_action_import_gpx
                                    )
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (rideHistory.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.ride_history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onImportGpx,
                    enabled = !importState.isImporting,
                    modifier = Modifier.testTag(RIDE_HISTORY_EMPTY_IMPORT_ACTION_TAG)
                ) {
                    if (importState.isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.UploadFile, contentDescription = null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ride_history_action_import_gpx))
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(rideHistory, key = { _, s -> s.rideId }) { _, summary ->
                    val isSelected = summary.rideId in selectedSessionIds
                    
                    RideHistoryItem(
                        summary = summary,
                        isSelected = isSelected,
                        onClick = { 
                            if (!summary.isFinished) return@RideHistoryItem
                            if (isSelectionMode) {
                                selectedSessionIds = if (isSelected) selectedSessionIds - summary.rideId else selectedSessionIds + summary.rideId
                            } else {
                                onSelectRide(summary.rideId)
                            }
                        },
                        onLongClick = {
                            if (!summary.isFinished) return@RideHistoryItem
                            if (!isSelectionMode) {
                                selectedSessionIds = setOf(summary.rideId)
                            }
                        },
                        onDelete = { onDeleteRide(summary) }
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
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit
) {
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        !summary.isFinished -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                enabled = true
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
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
            } else if (!summary.isFinished) {
                Icon(
                    Icons.Default.RadioButtonChecked, 
                    contentDescription = "Recording", 
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                val titleText = if (summary.name.isNullOrBlank()) {
                    formatDate(summary.startedAtMs)
                } else {
                    "${summary.name}: ${formatDate(summary.startedAtMs)}"
                }
                
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (summary.isFinished) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                
                if (!summary.isFinished) {
                    Text(
                        text = "Recording in progress...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                } else if (!summary.routeDescription.isNullOrBlank()) {
                    Text(
                        text = summary.routeDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "%.1f km".format(summary.trackLengthMeters / 1000f),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Max: %.1f°".format(maxOf(abs(summary.maxLeftDeg), abs(summary.maxRightDeg))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.ride_history_points_recorded, summary.pointCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.ride_history_action_delete), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun formatDate(timestampMs: Long): String =
    SimpleDateFormat("EEE, MMM d, HH:mm", Locale.getDefault()).format(Date(timestampMs))

internal const val RIDE_HISTORY_IMPORT_ACTION_TAG = "ride_history_import_action"
internal const val RIDE_HISTORY_EMPTY_IMPORT_ACTION_TAG = "ride_history_empty_import_action"

@Preview(showBackground = true, widthDp = 420, heightDp = 800)
@Composable
private fun RideHistoryScreenPreview() {
    LeanAngleTrackerTheme {
        RideHistoryScreen(
            rideHistory = listOf(
                RideSummary(
                    rideId = 1L,
                    startedAtMs = 1_720_000_000_000L,
                    endedAtMs = 1_720_003_600_000L,
                    name = "Alpenrunde",
                    pointCount = 1_200,
                    trackLengthMeters = 82_400f,
                    maxLeftDeg = -42f,
                    maxRightDeg = 47f
                )
            ),
            onSelectRide = {},
            onBack = {},
            onDeleteRide = {},
            onImportGpx = {}
        )
    }
}
