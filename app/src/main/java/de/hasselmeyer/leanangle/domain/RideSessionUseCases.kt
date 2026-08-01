package de.hasselmeyer.leanangle.domain

import android.app.Application
import de.hasselmeyer.leanangle.RideSession
import de.hasselmeyer.leanangle.RideSummary
import de.hasselmeyer.leanangle.TrackPoint
import de.hasselmeyer.leanangle.data.RideRepository
import de.hasselmeyer.leanangle.data.RideStats
import de.hasselmeyer.leanangle.data.gpx.GpxCodec
import de.hasselmeyer.leanangle.ui.tracking.calculateRouteDescription
import java.io.InputStream

class RideSessionUseCases(
    private val application: Application,
    private val rideRepository: RideRepository
) {
    suspend fun loadRideHistory(): List<RideSummary> = rideRepository.loadRideHistory()

    suspend fun importGpx(
        inputStream: InputStream,
        sourceFileName: String?,
        fallbackName: String,
        importedAtMs: Long
    ): RideSession {
        val parsedSession = GpxCodec.decode(
            inputStream = inputStream,
            sourceFileName = sourceFileName,
            fallbackName = fallbackName,
            importedAtMs = importedAtMs
        )
        val routeDescription = calculateRouteDescription(application, parsedSession)
        return rideRepository.importRide(parsedSession.copy(routeDescription = routeDescription))
    }

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
        val stats = RideStats(
            accumulatedTimeMs = session.accumulatedTimeMs,
            trackLengthMeters = session.trackLengthMeters,
            maxLeftDeg = session.maxLeftDeg,
            maxRightDeg = session.maxRightDeg,
            sumSpeedKmh = session.sumSpeedKmh,
            sumAbsLeanDeg = session.sumAbsLeanDeg,
            pointCount = session.points.size
        )
        rideRepository.finishRide(session.rideId, session.endedAtMs, desc, stats)
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
    suspend fun saveFinishedRide(
        rideId: Long, 
        started: Long, 
        ended: Long, 
        points: List<TrackPoint>,
        stats: RideStats
    ): RideSession {
        val session = RideSession(
            rideId = rideId, 
            startedAtMs = started, 
            endedAtMs = ended, 
            points = points, 
            isFinished = true,
            accumulatedTimeMs = stats.accumulatedTimeMs,
            trackLengthMeters = stats.trackLengthMeters,
            maxLeftDeg = stats.maxLeftDeg,
            maxRightDeg = stats.maxRightDeg,
            sumSpeedKmh = stats.sumSpeedKmh,
            sumAbsLeanDeg = stats.sumAbsLeanDeg
        )
        val desc = calculateRouteDescription(application, session)
        rideRepository.finishRide(rideId, ended, desc, stats)
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
        
        val totalTime = sorted.sumOf { it.accumulatedTimeMs }
        val totalDist = sorted.sumOf { it.trackLengthMeters.toDouble() }.toFloat()
        val maxL = sorted.minOf { it.maxLeftDeg } // maxLeftDeg is negative
        val maxR = sorted.maxOf { it.maxRightDeg }
        val sumSpeed = sorted.sumOf { it.sumSpeedKmh.toDouble() }.toFloat()
        val sumLean = sorted.sumOf { it.sumAbsLeanDeg.toDouble() }.toFloat()
        
        // Create a new ride in Room
        val newRideId = rideRepository.startNewRide(sorted.first().startedAtMs)
        mergedPoints.forEach { rideRepository.recordPoint(it, newRideId) }
        
        val stats = RideStats(
            accumulatedTimeMs = totalTime,
            trackLengthMeters = totalDist,
            maxLeftDeg = maxL,
            maxRightDeg = maxR,
            sumSpeedKmh = sumSpeed,
            sumAbsLeanDeg = sumLean,
            pointCount = mergedPoints.size
        )
        
        val temp = RideSession(
            rideId = newRideId, 
            startedAtMs = sorted.first().startedAtMs, 
            endedAtMs = sorted.last().endedAtMs, 
            points = mergedPoints, 
            name = "Combined Ride", 
            isFinished = true,
            accumulatedTimeMs = totalTime,
            trackLengthMeters = totalDist,
            maxLeftDeg = maxL,
            maxRightDeg = maxR,
            sumSpeedKmh = sumSpeed,
            sumAbsLeanDeg = sumLean
        )
        val desc = calculateRouteDescription(application, temp)
        rideRepository.finishRide(newRideId, sorted.last().endedAtMs, desc, stats)
        
        // Delete old ones
        rideIds.forEach { rideRepository.deleteRide(it) }
        
        return temp.copy(routeDescription = desc)
    }
}
