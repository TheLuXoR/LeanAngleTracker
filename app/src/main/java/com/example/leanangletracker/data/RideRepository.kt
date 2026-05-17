package com.example.leanangletracker.data

import android.content.Context
import com.example.leanangletracker.RideSession
import com.example.leanangletracker.TrackPoint
import com.example.leanangletracker.RideSummary
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RideRepository(context: Context) {
    companion object {
        private const val POINT_BATCH_SIZE = 20
        private const val POINT_BATCH_INTERVAL_MS = 2_000L
    }

    private val db = RideDatabase.getDatabase(context)
    private val rideDao = db.rideDao()
    private val pointBufferLock = Mutex()
    private val pointBuffersByRideId = mutableMapOf<Long, MutableList<TrackPointEntity>>()
    private val lastFlushAtMsByRideId = mutableMapOf<Long, Long>()

    /**
     * Creates a new ride record and returns its unique database ID.
     */
    suspend fun startNewRide(startTimeMs: Long): Long {
        val ride = RideEntity(
            startTime = startTimeMs,
            endTime = startTimeMs,
            name = null,
            routeDescription = null,
            isFinished = false
        )
        return rideDao.insertRide(ride)
    }

    /**
     * Buffers track points and writes in batches to reduce write frequency.
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

        val pointsToFlush: List<TrackPointEntity>? = pointBufferLock.withLock {
            val buffer = pointBuffersByRideId.getOrPut(rideId) { mutableListOf() }
            buffer.add(entity)
            val nowMs = System.currentTimeMillis()
            val lastFlushAtMs = lastFlushAtMsByRideId[rideId] ?: nowMs
            val shouldFlush = buffer.size >= POINT_BATCH_SIZE || (nowMs - lastFlushAtMs) >= POINT_BATCH_INTERVAL_MS

            if (shouldFlush) {
                lastFlushAtMsByRideId[rideId] = nowMs
                val snapshot = buffer.toList()
                buffer.clear()
                snapshot
            } else {
                null
            }
        }

        if (!pointsToFlush.isNullOrEmpty()) {
            rideDao.insertPoints(pointsToFlush)
        }
    }

    /**
     * Flushes any buffered points for the given ride.
     */
    suspend fun flushRidePoints(rideId: Long) {
        val pointsToFlush = pointBufferLock.withLock {
            val snapshot = pointBuffersByRideId[rideId]?.toList().orEmpty()
            pointBuffersByRideId[rideId]?.clear()
            lastFlushAtMsByRideId[rideId] = System.currentTimeMillis()
            snapshot
        }
        if (pointsToFlush.isNotEmpty()) {
            rideDao.insertPoints(pointsToFlush)
        }
    }

    /**
     * Updates the ride metadata when tracking is finished.
     */
    suspend fun finishRide(rideId: Long, endTimeMs: Long, routeDescription: String? = null) {
        flushRidePoints(rideId)
        val ride = rideDao.getRideById(rideId)
        if (ride != null) {
            rideDao.updateRide(ride.copy(
                endTime = endTimeMs,
                routeDescription = routeDescription ?: ride.routeDescription,
                isFinished = true
            ))
        }
    }

    suspend fun getLatestUnfinishedRide(): RideSummary? {
        val entity = rideDao.getLatestUnfinishedRide() ?: return null
        return getRideSummary(entity.id)
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
            pointCount = pointCount,
            isFinished = entity.isFinished
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
                pointCount = rideDao.getPointCountForRide(entity.id),
                isFinished = entity.isFinished
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
            routeDescription = ride.routeDescription,
            isFinished = ride.isFinished
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
