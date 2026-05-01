package com.example.leanangletracker.domain

import android.app.Application
import com.example.leanangletracker.RideSession
import com.example.leanangletracker.RideSummary
import com.example.leanangletracker.TrackPoint
import com.example.leanangletracker.data.RideRepository
import com.example.leanangletracker.toSummary
import com.example.leanangletracker.ui.tracking.calculateRouteDescription

class RideSessionUseCases(
    private val application: Application,
    private val rideRepository: RideRepository
) {
    fun loadRideHistory(): List<RideSummary> = rideRepository.loadRides().map { it.toSummary() }
    fun findUnfinishedRideForRecovery(): RideSession? { /*...*/
        val unfinishedIds = rideRepository.getUnfinishedRideIds().sortedDescending()
        val latestId = unfinishedIds.firstOrNull() ?: return null
        val points = rideRepository.loadTempPoints(latestId)
        unfinishedIds.drop(1).forEach(rideRepository::clearTempRide)
        if (points.isEmpty()) { rideRepository.clearTempRide(latestId); return null }
        return RideSession(latestId, points.last().timestampMs, points, "Unfinished Ride")
    }
    fun saveRecoveredRide(session: RideSession): RideSession {
        val recoveredRide = session.copy(name = "Recovered Ride", routeDescription = calculateRouteDescription(application, session))
        rideRepository.saveRide(recoveredRide); return recoveredRide
    }
    fun backfillRouteDescriptions(): List<RideSession> = rideRepository.loadRides().mapNotNull { ride ->
        if (ride.routeDescription.isNullOrBlank()) {
            val d = calculateRouteDescription(application, ride)
            if (d != null) ride.copy(routeDescription = d).also(rideRepository::saveRide) else null
        } else null
    }
    fun saveFinishedRide(started: Long, ended: Long, points: List<TrackPoint>): RideSession {
        val temp = RideSession(started, ended, points)
        val saved = temp.copy(routeDescription = calculateRouteDescription(application, temp))
        rideRepository.saveRide(saved)
        return saved
    }
    fun deleteRide(startedAtMs: Long) { rideRepository.loadRides().find { it.startedAtMs == startedAtMs }?.let(rideRepository::deleteRide) }
    fun updateRideName(startedAtMs: Long, newName: String): RideSession? {
        val s = rideRepository.loadRides().find { it.startedAtMs == startedAtMs } ?: return null
        return s.copy(name = newName).also(rideRepository::saveRide)
    }
    fun loadSession(startedAtMs: Long): RideSession? = rideRepository.loadRides().find { it.startedAtMs == startedAtMs }
    fun combineRides(startedAtIds: List<Long>): RideSession? {
        if (startedAtIds.size < 2) return null
        val all = rideRepository.loadRides()
        val sessions = startedAtIds.mapNotNull { id -> all.find { it.startedAtMs == id } }
        if (sessions.size < 2) return null
        val sorted = sessions.sortedBy { it.startedAtMs }
        val merged = sorted.flatMap { it.points }.sortedBy { it.timestampMs }
        val temp = RideSession(sorted.first().startedAtMs, sorted.last().endedAtMs, merged, "Combined Ride")
        val newS = temp.copy(routeDescription = calculateRouteDescription(application, temp))
        rideRepository.saveRide(newS); sessions.forEach(rideRepository::deleteRide); return newS
    }
}
