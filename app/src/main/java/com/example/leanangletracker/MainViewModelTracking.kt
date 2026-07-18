package com.example.leanangletracker

import android.location.Location
import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import com.example.leanangletracker.MainViewModelConfig.EXTEND_PROXIMITY_METERS
import com.example.leanangletracker.MainViewModelConfig.GPS_FRESHNESS_THRESHOLD_MS
import com.example.leanangletracker.MainViewModelConfig.LIVE_POINTS_UI_LIMIT
import com.example.leanangletracker.MainViewModelConfig.RECORDER_INTERVAL_MAX_MS
import com.example.leanangletracker.MainViewModelConfig.RECORDER_INTERVAL_MIN_MS
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs

internal fun MainViewModel.startTracking() {
    val state = _uiState.value
    if (!state.locationPermissionGranted) return

    if (!locationUpdatesRunning) {
        if (hasLocationPermission()) startLocationUpdates()
    }

    val lastRideSummary = state.rideHistory.firstOrNull()
    val isSameDayRideAvailable = lastRideSummary != null && isSameDay(lastRideSummary.endedAtMs, System.currentTimeMillis())

    if (isSameDayRideAvailable && lastRideSummary != null) {
        val currentLoc = latestGpsLocation
        if (currentLoc != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val fullRide = rideSessionUseCases.loadSession(lastRideSummary.rideId)
                if (fullRide != null) {
                    val lastPoint = fullRide.points.lastOrNull()
                    if (lastPoint != null) {
                        val dist = distanceMeters(lastPoint.latitude, lastPoint.longitude, currentLoc.latitude, currentLoc.longitude)
                        if (dist < EXTEND_PROXIMITY_METERS) {
                            launch(Dispatchers.Main) {
                                _uiState.update { it.copy(offerExtendSession = fullRide) }
                            }
                            return@launch
                        }
                    }
                }
                launch(Dispatchers.Main) {
                    performStartNewRide()
                }
            }
        } else {
            isCheckingForExtension = true
            updateTrackingState {
                it.copy(trackingStarted = true, showAutoResumePremiumShortcut = false)
            }
        }
    } else {
        viewModelScope.launch {
            performStartNewRide()
        }
    }
}

internal fun MainViewModel.confirmExtendRide(extend: Boolean) {
    val offer = _uiState.value.offerExtendSession
    _uiState.update { it.copy(offerExtendSession = null) }
    if (extend && offer != null) {
        resumeRideSession(offer)
    } else {
        viewModelScope.launch { performStartNewRide() }
    }
}

internal suspend fun MainViewModel.performStartNewRide() {
    if (!locationUpdatesRunning) {
        if (hasLocationPermission()) startLocationUpdates()
    }
    val startTime = System.currentTimeMillis()
    activeRideStartedMs = startTime
    activeRideId = rideRepository.startNewRide(startTime)
    
    val activeSummary = RideSummary(
        rideId = activeRideId!!,
        startedAtMs = startTime,
        endedAtMs = startTime,
        isFinished = false
    )
    _uiState.update { it.copy(rideHistory = (listOf(activeSummary) + it.rideHistory).sortedByDescending { it.startedAtMs }) }

    accumulatedTimeMs = 0L
    lastResumeMs = startTime
    trackLengthMeters = 0f
    ridePointCount = 0
    rideSumSpeedKmh = 0f
    rideSumAbsLeanDeg = 0f
    activeRideExtrema = LeanExtrema.ZERO
    recentRidePoints.clear()
    pausedPointsBuffer.clear()
    peakLeanSinceLastTick = 0f
    startRecorder()
    autoResumeTimerStartMs = null
    updateTrackingState {
        it.copy(
            trackingStarted = true,
            isPaused = false,
            hasTrackData = false,
            showAutoResumePremiumShortcut = false,
            recentPoints = emptyList()
        )
    }
}

/**
 * Hydrates the ViewModel state from an existing session and resumes tracking.
 * This is the "Single Source of Truth" way to continue a ride without copying data.
 */
internal fun MainViewModel.resumeRideSession(session: RideSession) {
    if (!locationUpdatesRunning) {
        if (hasLocationPermission()) startLocationUpdates()
    }
    
    activeRideId = session.rideId
    activeRideStartedMs = session.startedAtMs
    
    // Hydrate local stats from session
    trackLengthMeters = session.trackLengthMeters
    ridePointCount = session.points.size
    rideSumSpeedKmh = session.sumSpeedKmh
    rideSumAbsLeanDeg = session.sumAbsLeanDeg
    activeRideExtrema = LeanExtrema.fromAngles(session.points.map(TrackPoint::leanAngleDeg))
    accumulatedTimeMs = session.accumulatedTimeMs
    lastResumeMs = System.currentTimeMillis()
    
    recentRidePoints.clear()
    recentRidePoints.addAll(session.points.takeLast(LIVE_POINTS_UI_LIMIT))
    pausedPointsBuffer.clear()
    peakLeanSinceLastTick = 0f

    val activeSummary = session.toSummary().copy(isFinished = false, endedAtMs = System.currentTimeMillis())
    
    _uiState.update { state -> 
        state.copy(
            rideHistory = (listOf(activeSummary) + state.rideHistory.filter { it.rideId != session.rideId })
                .sortedByDescending { it.startedAtMs }
        ) 
    }

    startRecorder()
    updateTrackingState { it.copy(
        trackingStarted = true, 
        isPaused = false, 
        hasTrackData = session.points.isNotEmpty(), 
        showAutoResumePremiumShortcut = false,
        recentPoints = recentRidePoints.toList(),
        elapsedTimeMs = accumulatedTimeMs,
        trackLengthKm = trackLengthMeters / 1000f,
        averageSpeedKmh = if (ridePointCount > 0) rideSumSpeedKmh / ridePointCount else 0f,
        averageLeanAngleDeg = if (ridePointCount > 0) rideSumAbsLeanDeg / ridePointCount else 0f
    ) }
}

internal fun MainViewModel.togglePauseTracking() {
    val currentState = _uiState.value.tracking
    if (!currentState.trackingStarted) return
    
    val now = System.currentTimeMillis()
    if (currentState.isPaused) {
        lastResumeMs = now
        if (pausedPointsBuffer.isNotEmpty()) {
            pausedPointsBuffer.forEach { point ->
                addTrackPoint(point)
                activeRideId?.let { id -> viewModelScope.launch(Dispatchers.IO) { rideRepository.recordPoint(point, id) } }
            }
            pausedPointsBuffer.clear()
        }
        autoResumeTimerStartMs = null
        updateTrackingState {
            it.copy(isPaused = false, showAutoResumePremiumShortcut = false)
        }
    } else {
        accumulatedTimeMs += (now - lastResumeMs)
        pausedPointsBuffer.clear()
        activeRideId?.let { id ->
            viewModelScope.launch(Dispatchers.IO) {
                rideRepository.flushRidePoints(id)
            }
        }
        autoResumeTimerStartMs = null
        updateTrackingState {
            it.copy(isPaused = true, showAutoResumePremiumShortcut = false)
        }
    }
}

internal fun MainViewModel.addTrackPoint(point: TrackPoint) {
    val prev = recentRidePoints.lastOrNull()
    if (prev != null) {
        trackLengthMeters += distanceMeters(prev.latitude, prev.longitude, point.latitude, point.longitude)
    }
    ridePointCount++
    rideSumSpeedKmh += point.speedKmh
    rideSumAbsLeanDeg += abs(point.leanAngleDeg)
    activeRideExtrema = activeRideExtrema.include(point.leanAngleDeg)
    
    recentRidePoints.addLast(point)
    if (recentRidePoints.size > LIVE_POINTS_UI_LIMIT) {
        recentRidePoints.removeFirst()
    }
}

internal fun MainViewModel.finishRide() {
    isCheckingForExtension = false
    val currentRideId = activeRideId
    val started = activeRideStartedMs ?: System.currentTimeMillis()
    val nowMs = System.currentTimeMillis()
    val finalElapsedMs = if (_uiState.value.tracking.isPaused) accumulatedTimeMs else accumulatedTimeMs + (nowMs - lastResumeMs)
    val ended = nowMs
    
    if (currentRideId != null) {
        if (ridePointCount > 0) {
            val rideExtrema = activeRideExtrema
            val stats = com.example.leanangletracker.data.RideStats(
                accumulatedTimeMs = finalElapsedMs,
                trackLengthMeters = trackLengthMeters,
                maxLeftDeg = rideExtrema.maxLeftDeg,
                maxRightDeg = rideExtrema.maxRightDeg,
                sumSpeedKmh = rideSumSpeedKmh,
                sumAbsLeanDeg = rideSumAbsLeanDeg,
                pointCount = ridePointCount
            )

            val skeleton = RideSummary(
                rideId = currentRideId,
                startedAtMs = started,
                endedAtMs = ended,
                isSkeleton = true,
                isFinished = false,
                accumulatedTimeMs = finalElapsedMs,
                trackLengthMeters = trackLengthMeters,
                maxLeftDeg = rideExtrema.maxLeftDeg,
                maxRightDeg = rideExtrema.maxRightDeg,
                pointCount = ridePointCount
            )
            _uiState.update { it.copy(
                rideHistory = it.rideHistory.map { summary ->
                    if (summary.rideId == currentRideId) skeleton else summary
                }.sortedByDescending { it.startedAtMs },
                lastSavedRideId = currentRideId
            ) }

            viewModelScope.launch(Dispatchers.IO) {
                rideRepository.flushRidePoints(currentRideId)
                val sessionData = rideRepository.loadFullSession(currentRideId)
                val allPoints = sessionData?.points ?: emptyList()
                val newSession = rideSessionUseCases.saveFinishedRide(currentRideId, started, ended, allPoints, stats)
                
                launch(Dispatchers.Main) {
                    _uiState.update { state ->
                        state.copy(
                            rideHistory = state.rideHistory.map {
                                if (it.rideId == currentRideId) newSession.toSummary() else it
                            },
                            expandedRides = state.expandedRides + (currentRideId to newSession)
                        )
                    }
                }
            }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                rideRepository.deleteRide(currentRideId)
            }
            _uiState.update { it.copy(rideHistory = it.rideHistory.filter { it.rideId != currentRideId }) }
        }
    }
    
    stopLocationUpdates()
    stopRecorder()
    recentRidePoints.clear()
    pausedPointsBuffer.clear()
    activeRideId = null
    activeRideStartedMs = null
    accumulatedTimeMs = 0L
    latestGpsLocation = null
    latestGpsTimestampNs = null
    speedKmh = 0f
    trackLengthMeters = 0f
    ridePointCount = 0
    rideSumSpeedKmh = 0f
    rideSumAbsLeanDeg = 0f
    activeRideExtrema = LeanExtrema.ZERO
    autoResumeTimerStartMs = null
    updateTrackingState {
        it.copy(
            hasTrackData = false,
            speedKmh = 0f,
            gpsActive = false,
            trackingStarted = false,
            isPaused = false,
            showAutoResumePremiumShortcut = false,
            currentLatitude = null,
            currentLongitude = null,
            elapsedTimeMs = 0L,
            averageSpeedKmh = 0f,
            trackLengthKm = 0f,
            averageLeanAngleDeg = 0f,
            recentPoints = emptyList()
        )
    }
}

internal fun MainViewModel.deleteRide(summary: RideSummary) {
    viewModelScope.launch(Dispatchers.IO) {
        rideSessionUseCases.deleteRide(summary.rideId)
        launch(Dispatchers.Main) {
            _uiState.update { it.copy(
                rideHistory = it.rideHistory.filter { it.rideId != summary.rideId },
                expandedRides = it.expandedRides - summary.rideId
            ) }
        }
    }
}

internal fun MainViewModel.updateRideName(summary: RideSummary, newName: String) {
    viewModelScope.launch(Dispatchers.IO) {
        val updated = rideSessionUseCases.updateRideName(summary.rideId, newName) ?: return@launch
            launch(Dispatchers.Main) {
                _uiState.update { state ->
                    state.copy(
                        rideHistory = state.rideHistory.map {
                            if (it.rideId == summary.rideId) updated.toSummary() else it
                        },
                        expandedRides = if (state.expandedRides.containsKey(summary.rideId)) {
                            state.expandedRides + (summary.rideId to updated)
                        } else state.expandedRides
                    )
                }
            }
    }
}

internal fun MainViewModel.loadFullSession(rideId: Long) {
    if (_uiState.value.expandedRides.containsKey(rideId)) return
    viewModelScope.launch(Dispatchers.IO) {
        val session = rideSessionUseCases.loadSession(rideId) ?: return@launch
        launch(Dispatchers.Main) {
            _uiState.update { it.copy(expandedRides = it.expandedRides + (rideId to session)) }
        }
    }
}

internal fun MainViewModel.combineRides(summaries: List<RideSummary>) {
    if (summaries.size < 2) return
    viewModelScope.launch(Dispatchers.IO) {
        val newSession = rideSessionUseCases.combineRides(summaries.map { it.rideId }) ?: return@launch
        
        val idsToRemove = summaries.map { it.rideId }.toSet()
        launch(Dispatchers.Main) {
            _uiState.update { it.copy(
                rideHistory = (listOf(newSession.toSummary()) + it.rideHistory.filter { it.rideId !in idsToRemove })
                    .sortedByDescending { it.startedAtMs },
                expandedRides = it.expandedRides.filterKeys { it !in idsToRemove }
            ) }
        }
    }
}

internal fun MainViewModel.startRecorder() {
    if (recorderJob?.isActive == true) return
    recorderJob = viewModelScope.launch {
        while (isActive) {
            val intervalMs = _uiState.value.settings.recorderIntervalMs
                .coerceIn(RECORDER_INTERVAL_MIN_MS, RECORDER_INTERVAL_MAX_MS)
            delay(intervalMs.toLong())
            recordFusedSample()
        }
    }
}

internal fun MainViewModel.stopRecorder() {
    recorderJob?.cancel()
    recorderJob = null
}

internal fun MainViewModel.recordFusedSample() {
    val state = _uiState.value
    if (!state.tracking.trackingStarted || isCheckingForExtension) return
    if (state.tracking.isUpsideDown) return

    val gps = latestGpsLocation ?: return
    val nowNs = SystemClock.elapsedRealtimeNanos()
    val leanAgeMs = latestLeanTimestampNs?.let { ((nowNs - it).coerceAtLeast(0L)) / 1_000_000L }
        ?: Long.MAX_VALUE
    val gpsAgeMs = latestGpsTimestampNs?.let { ((nowNs - it).coerceAtLeast(0L)) / 1_000_000L }
        ?: Long.MAX_VALUE
    val fusedSpeedKmh = (gps.speed * 3.6f).coerceAtLeast(0f)
    speedKmh = fusedSpeedKmh

    val nowMs = System.currentTimeMillis()
    
    val recordedLean = if (peakLeanSinceLastTick != 0f) peakLeanSinceLastTick else latestLeanDeg
    
    val point = TrackPoint(
        timestampMs = nowMs,
        latitude = gps.latitude,
        longitude = gps.longitude,
        speedKmh = fusedSpeedKmh,
        leanAngleDeg = recordedLean,
        leanFreshnessMs = leanAgeMs,
        gpsFreshnessMs = gpsAgeMs,
        hasFreshGps = gpsAgeMs <= GPS_FRESHNESS_THRESHOLD_MS,
        lapIndex = 0
    )

    if (state.tracking.isPaused) {
        if (state.settings.autoResumeEnabled) {
            pausedPointsBuffer.addLast(point)
            val cutoff = nowMs - 30_000L
            while (pausedPointsBuffer.isNotEmpty() && pausedPointsBuffer.first().timestampMs < cutoff) {
                pausedPointsBuffer.removeFirst()
            }
        }
        return
    }

    // Live stats update & Local buffer update
    addTrackPoint(point)
    val rideExtrema = activeRideExtrema
    
    val elapsedMs = accumulatedTimeMs + (nowMs - lastResumeMs)
    val avgSpeed = if (ridePointCount > 0) rideSumSpeedKmh / ridePointCount else 0f
    val avgLean = if (ridePointCount > 0) rideSumAbsLeanDeg / ridePointCount else 0f

    // Immediate persistence of both point AND stats
    activeRideId?.let { id ->
        viewModelScope.launch(Dispatchers.IO) {
            rideRepository.recordPoint(point, id)
            // Periodic stats update
            rideRepository.updateRideStats(
                rideId = id,
                accumulatedTimeMs = elapsedMs,
                trackLengthMeters = trackLengthMeters,
                maxLeftDeg = rideExtrema.maxLeftDeg,
                maxRightDeg = rideExtrema.maxRightDeg,
                sumSpeedKmh = rideSumSpeedKmh,
                sumAbsLeanDeg = rideSumAbsLeanDeg,
                pointCount = ridePointCount
            )
        }
    }
    
    peakLeanSinceLastTick = latestLeanDeg

    updateTrackingState {
        it.copy(
            speedKmh = fusedSpeedKmh,
            gpsActive = locationUpdatesRunning,
            hasTrackData = true,
            currentLatitude = gps.latitude,
            currentLongitude = gps.longitude,
            elapsedTimeMs = elapsedMs,
            averageSpeedKmh = avgSpeed,
            trackLengthKm = trackLengthMeters / 1000f,
            averageLeanAngleDeg = avgLean,
            recentPoints = recentRidePoints.toList()
        )
    }
}

internal fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val results = FloatArray(1)
    Location.distanceBetween(lat1, lon1, lat2, lon2, results)
    return results[0].coerceAtLeast(0f)
}

internal fun isSameDay(ms1: Long, ms2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = ms1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = ms2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}
