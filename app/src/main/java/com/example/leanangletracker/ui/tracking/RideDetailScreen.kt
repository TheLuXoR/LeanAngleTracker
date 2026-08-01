package com.example.leanangletracker.ui.tracking

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.leanangletracker.R
import com.example.leanangletracker.RideSession
import com.example.leanangletracker.RideSummary
import com.example.leanangletracker.data.gpx.GpxCodec
import com.example.leanangletracker.ui.components.admob.loadInterstitial
import com.example.leanangletracker.ui.components.admob.showInterstitial
import com.example.leanangletracker.ui.theme.LeanAngleTrackerTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RideDetailScreen(
    rideSummary: RideSummary,
    fullSession: RideSession?,
    onBack: () -> Unit,
    onUpdateName: (String) -> Unit,
    onDelete: () -> Unit,
    isPremiumSubscribed: Boolean = false,
    restrictedToDataManagement: Boolean = false
) {
    val context = LocalContext.current
    var isEditingName by remember { mutableStateOf(false) }
    var editedName by remember(rideSummary.name) { mutableStateOf(rideSummary.name ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        val titleText = if (rideSummary.name.isNullOrBlank()) {
                            formatDate(rideSummary.startedAtMs)
                        } else {
                            rideSummary.name
                        }
                        Text(titleText, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!rideSummary.routeDescription.isNullOrBlank()) {
                            Text(rideSummary.routeDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (!restrictedToDataManagement) {
                        IconButton(onClick = { isEditingName = !isEditingName }) {
                            Icon(
                                if (isEditingName) Icons.Default.Check else Icons.Default.Edit,
                                contentDescription = stringResource(
                                    R.string.ride_history_action_edit_name
                                )
                            )
                        }
                    }
                    IconButton(
                        enabled = fullSession != null,
                        onClick = {
                            fullSession?.let { session ->
                                runWithOptionalAd(context, isPremiumSubscribed) {
                                    openGpxFile(context, session)
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "Open")
                    }
                    IconButton(
                        enabled = fullSession != null,
                        onClick = {
                            fullSession?.let { session ->
                                runWithOptionalAd(context, isPremiumSubscribed) {
                                    shareGpxFile(context, session)
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            if (isEditingName && !restrictedToDataManagement) {
                TextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
            }

            if (fullSession == null) {
                RideReviewSkeleton(modifier = Modifier.fillMaxSize())
            } else {
                RideReviewTemplate(
                    rideSession = fullSession,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private fun runWithOptionalAd(
    context: Context,
    isPremiumSubscribed: Boolean,
    action: () -> Unit
) {
    if (isPremiumSubscribed) {
        action()
    } else {
        loadInterstitial(context) { ad -> showInterstitial(context, ad, action) }
    }
}

private fun formatDate(timestampMs: Long): String =
    SimpleDateFormat("EEE, MMM d, HH:mm", Locale.getDefault()).format(Date(timestampMs))

@Preview(showBackground = true, widthDp = 420, heightDp = 800)
@Composable
private fun RideDetailScreenPreview() {
    LeanAngleTrackerTheme {
        RideDetailScreen(
            rideSummary = RideSummary(startedAtMs = 0L, endedAtMs = 0L, name = "Sample ride"),
            fullSession = null,
            onBack = {},
            onUpdateName = {},
            onDelete = {},
            isPremiumSubscribed = true
        )
    }
}

private fun openGpxFile(context: Context, session: RideSession) {
    val gpxContent = buildGpxString(context, session)
    val fileName = "ride_${session.startedAtMs}.gpx"
    try {
        val cacheFile = File(context.cacheDir, fileName).apply { writeText(gpxContent) }
        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
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
        val cacheFile = File(context.cacheDir, fileName).apply { writeText(gpxContent) }
        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
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
    return GpxCodec.encode(
        rideSession = rideSession,
        defaultName = context.getString(
            R.string.ride_history_default_name,
            rideSession.startedAtMs
        )
    )
}
