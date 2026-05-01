package com.example.leanangletracker.data

import android.content.Context
import com.example.leanangletracker.RideSession
import com.example.leanangletracker.TrackPoint
import com.example.leanangletracker.RideSummary

class RideRepository(context: Context) {
    private val db = RideDatabase.getDatabase(context)
    private val rideDao = db.rideDao()

    /**
     * Creates a new ride record and returns its unique database ID.
     */
    suspend fun startNewRide(startTimeMs: Long): Long {
        val ride = RideEntity(
            startTime = startTimeMs,
            endTime = startTimeMs,
            name = null,
            routeDescription = null
        )
        return rideDao.insertRide(ride)
    }

    /**
     * Persists a single track point to the database immediately.
     */
    suspend fun recordPoint(point: TrackPoint, rideId: Long) {
        val entity = TrackPointEntity(
            rideId = rideId,
            latitude = point.latitude,
            longitude = point.longitude,
            speed = point.speedKmh,
            leanAngle = point.leanAngleDeg,
            timestamp = point.timestampMs
        )
        rideDao.insertPoint(entity)
    }

    /**
     * Updates the ride metadata when tracking is finished.
     */
    suspend fun finishRide(rideId: Long, endTimeMs: Long, routeDescription: String? = null) {
        val ride = rideDao.getRideById(rideId)
        if (ride != null) {
            rideDao.updateRide(ride.copy(
                endTime = endTimeMs,
                routeDescription = routeDescription ?: ride.routeDescription
            ))
        }
    }

    /**
     * Loads only the ride metadata without the coordinate list.
     */
    suspend fun getRideSummary(rideId: Long): RideSummary? {
        val entity = rideDao.getRideById(rideId) ?: return null
        val pointCount = rideDao.getPointCountForRide(rideId)
        return RideSummary(
            rideId = entity.id,
            startedAtMs = entity.startTime,
            endedAtMs = entity.endTime,
            name = entity.name,
            routeDescription = entity.routeDescription,
            pointCount = pointCount
        )
    }

    suspend fun loadRideHistory(): List<RideSummary> {
        return rideDao.getAllRides().map { entity ->
            RideSummary(
                rideId = entity.id,
                startedAtMs = entity.startTime,
                endedAtMs = entity.endTime,
                name = entity.name,
                routeDescription = entity.routeDescription,
                pointCount = rideDao.getPointCountForRide(entity.id)
            )
        }
    }

    suspend fun loadFullSession(rideId: Long): RideSession? {
        val ride = rideDao.getRideById(rideId) ?: return null
        val points = rideDao.getPointsForRide(rideId).map { entity ->
            TrackPoint(
                timestampMs = entity.timestamp,
                latitude = entity.latitude,
                longitude = entity.longitude,
                speedKmh = entity.speed,
                leanAngleDeg = entity.leanAngle,
                leanFreshnessMs = 0,
                gpsFreshnessMs = 0,
                hasFreshGps = true
            )
        }
        return RideSession(
            rideId = ride.id,
            startedAtMs = ride.startTime,
            endedAtMs = ride.endTime,
            points = points,
            name = ride.name,
            routeDescription = ride.routeDescription
        )
    }

    suspend fun updateRideName(rideId: Long, name: String) {
        val ride = rideDao.getRideById(rideId)
        if (ride != null) {
            rideDao.updateRide(ride.copy(name = name))
        }
    }

    suspend fun deleteRide(rideId: Long) {
        rideDao.deleteRide(rideId)
    }
}
