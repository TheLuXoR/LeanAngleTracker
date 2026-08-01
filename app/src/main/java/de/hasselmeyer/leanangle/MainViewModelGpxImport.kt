package de.hasselmeyer.leanangle

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.viewModelScope
import de.hasselmeyer.leanangle.data.gpx.EmptyGpxException
import de.hasselmeyer.leanangle.data.gpx.GpxTooLargeException
import de.hasselmeyer.leanangle.data.gpx.InvalidGpxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

internal fun MainViewModel.importGpx(uri: Uri) {
    if (_uiState.value.gpxImport.isImporting) return

    _uiState.update {
        it.copy(gpxImport = GpxImportUiState(isImporting = true))
    }

    viewModelScope.launch(Dispatchers.IO) {
        val application = getApplication<Application>()
        try {
            val sourceFileName = application.contentResolver.queryDisplayName(uri)
            val importedSession = application.contentResolver.openInputStream(uri)?.use { inputStream ->
                rideSessionUseCases.importGpx(
                    inputStream = inputStream,
                    sourceFileName = sourceFileName,
                    fallbackName = application.getString(R.string.ride_history_import_default_name),
                    importedAtMs = System.currentTimeMillis()
                )
            } ?: throw IOException("The selected document could not be opened.")

            _uiState.update { state ->
                state.copy(
                    rideHistory = (listOf(importedSession.toSummary()) + state.rideHistory)
                        .sortedByDescending(RideSummary::startedAtMs),
                    expandedRides = state.expandedRides +
                        (importedSession.rideId to importedSession),
                    lastSavedRideId = importedSession.rideId,
                    gpxImport = GpxImportUiState()
                )
            }
        } catch (_: GpxTooLargeException) {
            updateGpxImportError(R.string.ride_history_import_error_too_large)
        } catch (_: EmptyGpxException) {
            updateGpxImportError(R.string.ride_history_import_error_empty)
        } catch (_: InvalidGpxException) {
            updateGpxImportError(R.string.ride_history_import_error_invalid)
        } catch (_: IOException) {
            updateGpxImportError(R.string.ride_history_import_error_read)
        } catch (_: SecurityException) {
            updateGpxImportError(R.string.ride_history_import_error_read)
        } catch (_: Exception) {
            updateGpxImportError(R.string.ride_history_import_error_save)
        }
    }
}

internal fun MainViewModel.consumeGpxImportError() {
    _uiState.update { state ->
        state.copy(gpxImport = state.gpxImport.copy(errorResId = null))
    }
}

private fun MainViewModel.updateGpxImportError(errorResId: Int) {
    _uiState.update {
        it.copy(
            gpxImport = GpxImportUiState(
                isImporting = false,
                errorResId = errorResId
            )
        )
    }
}

private fun android.content.ContentResolver.queryDisplayName(uri: Uri): String? {
    return runCatching {
        query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val displayNameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (displayNameColumn >= 0) cursor.getString(displayNameColumn) else null
        }
    }.getOrNull()
}
