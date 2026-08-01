package de.hasselmeyer.leanangle.data

import android.content.Context
import de.hasselmeyer.leanangle.RideSession
import de.hasselmeyer.leanangle.TrackPoint
import de.hasselmeyer.leanangle.RideSummary
import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RideRepository internal constructor(
    private val db: RideDatabase
) {
    constructor(context: Context) : this(RideDatabase.getDatabase(context))

    companion object {
        private const val POINT_BATCH_SIZE = 20
        private const val POINT_BATCH_INTERVAL_MS = 2_000L
        private const val IMPORT_POINT_BATCH_SIZE = 1_000
    }

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
     * Updates the running totals for a ride in the database.
     */
    suspend fun updateRideStats(
        rideId: Long,
        accumulatedTimeMs: Long,
        trackLengthMeters: Float,
        maxLeftDeg: Float,
        maxRightDeg: Float,
        sumSpeedKmh: Float,
        sumAbsLeanDeg: Float,
        pointCount: Int
    ) {
        val ride = rideDao.getRideById(rideId)
        if (ride != null) {
            rideDao.updateRide(ride.copy(
                accumulatedTimeMs = accumulatedTimeMs,
                trackLengthMeters = trackLengthMeters,
                maxLeftDeg = maxLeftDeg,
                maxRightDeg = maxRightDeg,
                sumSpeedKmh = sumSpeedKmh,
                sumAbsLeanDeg = sumAbsLeanDeg,
                pointCount = pointCount
            ))
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
    suspend fun finishRide(
        rideId: Long,
        endTimeMs: Long,
        routeDescription: String? = null,
        stats: RideStats? = null
    ) {
        flushRidePoints(rideId)
        val ride = rideDao.getRideById(rideId)
        if (ride != null) {
            rideDao.updateRide(ride.copy(
                endTime = endTimeMs,
                routeDescription = routeDescription ?: ride.routeDescription,
                isFinished = true,
                accumulatedTimeMs = stats?.accumulatedTimeMs ?: ride.accumulatedTimeMs,
                trackLengthMeters = stats?.trackLengthMeters ?: ride.trackLengthMeters,
                maxLeftDeg = stats?.maxLeftDeg ?: ride.maxLeftDeg,
                maxRightDeg = stats?.maxRightDeg ?: ride.maxRightDeg,
                sumSpeedKmh = stats?.sumSpeedKmh ?: ride.sumSpeedKmh,
                sumAbsLeanDeg = stats?.sumAbsLeanDeg ?: ride.sumAbsLeanDeg,
                pointCount = stats?.pointCount ?: ride.pointCount
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
        return RideSummary(
            rideId = entity.id,
            startedAtMs = entity.startTime,
            endedAtMs = entity.endTime,
            name = entity.name,
            routeDescription = entity.routeDescription,
            pointCount = entity.pointCount,
            isFinished = entity.isFinished,
            accumulatedTimeMs = entity.accumulatedTimeMs,
            trackLengthMeters = entity.trackLengthMeters,
            maxLeftDeg = entity.maxLeftDeg,
            maxRightDeg = entity.maxRightDeg,
            averageSpeedKmh = if (entity.pointCount > 0) entity.sumSpeedKmh / entity.pointCount else 0f,
            averageLeanAngleDeg = if (entity.pointCount > 0) entity.sumAbsLeanDeg / entity.pointCount else 0f
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
                pointCount = entity.pointCount,
                isFinished = entity.isFinished,
                accumulatedTimeMs = entity.accumulatedTimeMs,
                trackLengthMeters = entity.trackLengthMeters,
                maxLeftDeg = entity.maxLeftDeg,
                maxRightDeg = entity.maxRightDeg,
                averageSpeedKmh = if (entity.pointCount > 0) entity.sumSpeedKmh / entity.pointCount else 0f,
                averageLeanAngleDeg = if (entity.pointCount > 0) entity.sumAbsLeanDeg / entity.pointCount else 0f
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
            isFinished = ride.isFinished,
            accumulatedTimeMs = ride.accumulatedTimeMs,
            trackLengthMeters = ride.trackLengthMeters,
            maxLeftDeg = ride.maxLeftDeg,
            maxRightDeg = ride.maxRightDeg,
            sumSpeedKmh = ride.sumSpeedKmh,
            sumAbsLeanDeg = ride.sumAbsLeanDeg
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

    /**
     * Stores a parsed GPX ride and all of its points as one atomic operation.
     */
    suspend fun importRide(session: RideSession): RideSession = db.withTransaction {
        require(session.points.isNotEmpty()) { "An imported ride must contain track points." }

        val rideId = rideDao.insertRide(
            RideEntity(
                startTime = session.startedAtMs,
                endTime = session.endedAtMs,
                name = session.name,
                routeDescription = session.routeDescription,
                isFinished = true,
                accumulatedTimeMs = session.accumulatedTimeMs,
                trackLengthMeters = session.trackLengthMeters,
                maxLeftDeg = session.maxLeftDeg,
                maxRightDeg = session.maxRightDeg,
                sumSpeedKmh = session.sumSpeedKmh,
                sumAbsLeanDeg = session.sumAbsLeanDeg,
                pointCount = session.points.size
            )
        )

        session.points.chunked(IMPORT_POINT_BATCH_SIZE).forEach { pointBatch ->
            require(
                pointBatch.all {
                    it.latitude.isFinite() &&
                        it.longitude.isFinite() &&
                        it.speedKmh.isFinite() &&
                        it.leanAngleDeg.isFinite()
                }
            ) { "Imported track points must contain finite values." }
            rideDao.insertPoints(
                pointBatch.map { point ->
                    TrackPointEntity(
                        rideId = rideId,
                        latitude = point.latitude,
                        longitude = point.longitude,
                        speed = point.speedKmh,
                        leanAngle = point.leanAngleDeg,
                        timestamp = point.timestampMs
                    )
                }
            )
        }

        session.copy(rideId = rideId)
    }
}

data class RideStats(
    val accumulatedTimeMs: Long,
    val trackLengthMeters: Float,
    val maxLeftDeg: Float,
    val maxRightDeg: Float,
    val sumSpeedKmh: Float,
    val sumAbsLeanDeg: Float,
    val pointCount: Int
)
