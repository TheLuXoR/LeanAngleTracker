package com.example.leanangletracker.domain

import android.app.Application
import com.example.leanangletracker.RideSession
import com.example.leanangletracker.RideSummary
import com.example.leanangletracker.TrackPoint
import com.example.leanangletracker.data.RideRepository
import com.example.leanangletracker.ui.tracking.calculateRouteDescription

class RideSessionUseCases(
    private val application: Application,
    private val rideRepository: RideRepository
) {
    suspend fun loadRideHistory(): List<RideSummary> = rideRepository.loadRideHistory()

    /**
     * Finds a ride that was started but not properly finished (metadata might be incomplete).
     * With Room, we can look for rides with few points or recent starts.
     * For now, we'll keep the logic simple: look for the most recent ride.
     */
    suspend fun findUnfinishedRideForRecovery(): RideSession? {
        val history = rideRepository.loadRideHistory()
        val latest = history.firstOrNull() ?: return null
        
        // If the ride has points but is very recent (e.g. within last hour) and was never "finished"
        // we might consider it for recovery. For simplicity, let's just allow loading any ride.
        return rideRepository.loadFullSession(latest.startedAtMs)
    }

    suspend fun saveRecoveredRide(session: RideSession): RideSession {
        // In Room, the ride is already partially saved as points were recorded live.
        // We just ensure the metadata is updated.
        val desc = calculateRouteDescription(application, session)
        rideRepository.finishRide(session.startedAtMs, session.endedAtMs, desc)
        return session.copy(routeDescription = desc)
    }

    suspend fun backfillRouteDescriptions(): List<RideSession> {
        val history = rideRepository.loadRideHistory()
        return history.mapNotNull { summary ->
            if (summary.routeDescription.isNullOrBlank()) {
                val session = rideRepository.loadFullSession(summary.startedAtMs) ?: return@mapNotNull null
                val desc = calculateRouteDescription(application, session)
                if (desc != null) {
                    rideRepository.finishRide(summary.startedAtMs, summary.endedAtMs, desc)
                    session.copy(routeDescription = desc)
                } else null
            } else null
        }
    }

    /**
     * Metadata is now updated live or at the end.
     * The points are already in the DB.
     */
    suspend fun saveFinishedRide(rideId: Long, started: Long, ended: Long, points: List<TrackPoint>): RideSession {
        val session = RideSession(started, ended, points)
        val desc = calculateRouteDescription(application, session)
        rideRepository.finishRide(rideId, ended, desc)
        return session.copy(routeDescription = desc)
    }

    suspend fun deleteRide(startedAtMs: Long) {
        rideRepository.deleteRide(startedAtMs)
    }

    suspend fun updateRideName(startedAtMs: Long, newName: String): RideSession? {
        rideRepository.updateRideName(startedAtMs, newName)
        return rideRepository.loadFullSession(startedAtMs)
    }

    suspend fun loadSession(startedAtMs: Long): RideSession? = rideRepository.loadFullSession(startedAtMs)

    suspend fun combineRides(startedAtIds: List<Long>): RideSession? {
        if (startedAtIds.size < 2) return null
        
        val sessions = startedAtIds.mapNotNull { rideRepository.loadFullSession(it) }
        if (sessions.size < 2) return null
        
        val sorted = sessions.sortedBy { it.startedAtMs }
        val mergedPoints = sorted.flatMap { it.points }.sortedBy { it.timestampMs }
        
        // Create a new ride in Room
        val newRideId = rideRepository.startNewRide(sorted.first().startedAtMs)
        mergedPoints.forEach { rideRepository.recordPoint(it, newRideId) }
        
        val temp = RideSession(sorted.first().startedAtMs, sorted.last().endedAtMs, mergedPoints, "Combined Ride")
        val desc = calculateRouteDescription(application, temp)
        rideRepository.finishRide(newRideId, sorted.last().endedAtMs, desc)
        
        // Delete old ones
        startedAtIds.forEach { rideRepository.deleteRide(it) }
        
        return temp.copy(routeDescription = desc)
    }
}
