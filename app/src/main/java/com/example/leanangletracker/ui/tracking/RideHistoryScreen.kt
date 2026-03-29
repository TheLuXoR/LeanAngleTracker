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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.leanangletracker.R
import com.example.leanangletracker.RideSummary
import com.example.leanangletracker.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RideHistoryScreen(
    rideHistory: List<RideSummary>,
    onSelectRide: (Long) -> Unit,
    onBack: () -> Unit,
    onDeleteRide: (RideSummary) -> Unit,
    onCombineRides: (List<RideSummary>) -> Unit = {}
) {
    val listState = rememberLazyListState()
    var selectedSessionIds by remember { mutableStateOf(setOf<Long>()) }
    val isSelectionMode = selectedSessionIds.isNotEmpty()

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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(rideHistory, key = { _, s -> s.startedAtMs }) { _, summary ->
                    val isSelected = summary.startedAtMs in selectedSessionIds
                    
                    RideHistoryItem(
                        summary = summary,
                        isSelected = isSelected,
                        onClick = { 
                            if (isSelectionMode) {
                                selectedSessionIds = if (isSelected) selectedSessionIds - summary.startedAtMs else selectedSessionIds + summary.startedAtMs
                            } else {
                                onSelectRide(summary.startedAtMs)
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                selectedSessionIds = setOf(summary.startedAtMs)
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
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
                    fontWeight = FontWeight.Bold
                )
                if (!summary.routeDescription.isNullOrBlank()) {
                    Text(
                        text = summary.routeDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.ride_history_points_recorded, summary.pointCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.ride_history_action_delete), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun formatDate(timestampMs: Long): String =
    SimpleDateFormat("EEE, MMM d, HH:mm", Locale.getDefault()).format(Date(timestampMs))
