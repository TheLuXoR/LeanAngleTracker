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
     * Finds a ride that was started but not properly finished.
     * Now looks directly into the database for isFinished = false.
     */
    suspend fun findUnfinishedRideForRecovery(): RideSession? {
        val unfinished = rideRepository.getLatestUnfinishedRide() ?: return null
        return rideRepository.loadFullSession(unfinished.rideId)
    }

    suspend fun saveRecoveredRide(session: RideSession): RideSession {
        // Mark as finished in the DB
        val desc = calculateRouteDescription(application, session)
        rideRepository.finishRide(session.rideId, session.endedAtMs, desc)
        return session.copy(routeDescription = desc, isFinished = true)
    }

    suspend fun backfillRouteDescriptions(): List<RideSession> {
        val history = rideRepository.loadRideHistory()
        return history.mapNotNull { summary ->
            if (summary.routeDescription.isNullOrBlank() && summary.isFinished) {
                val session = rideRepository.loadFullSession(summary.rideId) ?: return@mapNotNull null
                val desc = calculateRouteDescription(application, session)
                if (desc != null) {
                    rideRepository.finishRide(summary.rideId, summary.endedAtMs, desc)
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
        val session = RideSession(rideId = rideId, startedAtMs = started, endedAtMs = ended, points = points, isFinished = true)
        val desc = calculateRouteDescription(application, session)
        rideRepository.finishRide(rideId, ended, desc)
        return session.copy(routeDescription = desc)
    }

    suspend fun deleteRide(rideId: Long) {
        rideRepository.deleteRide(rideId)
    }

    suspend fun updateRideName(rideId: Long, newName: String): RideSession? {
        rideRepository.updateRideName(rideId, newName)
        return rideRepository.loadFullSession(rideId)
    }

    suspend fun loadSession(rideId: Long): RideSession? = rideRepository.loadFullSession(rideId)

    suspend fun combineRides(rideIds: List<Long>): RideSession? {
        if (rideIds.size < 2) return null
        
        val sessions = rideIds.mapNotNull { rideRepository.loadFullSession(it) }
        if (sessions.size < 2) return null
        
        val sorted = sessions.sortedBy { it.startedAtMs }
        val mergedPoints = sorted.flatMap { it.points }.sortedBy { it.timestampMs }
        
        // Create a new ride in Room
        val newRideId = rideRepository.startNewRide(sorted.first().startedAtMs)
        mergedPoints.forEach { rideRepository.recordPoint(it, newRideId) }
        
        val temp = RideSession(newRideId, sorted.first().startedAtMs, sorted.last().endedAtMs, mergedPoints, "Combined Ride", isFinished = true)
        val desc = calculateRouteDescription(application, temp)
        rideRepository.finishRide(newRideId, sorted.last().endedAtMs, desc)
        
        // Delete old ones
        rideIds.forEach { rideRepository.deleteRide(it) }
        
        return temp.copy(routeDescription = desc)
    }
}
