package com.example.leanangletracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface RideDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRide(ride: RideEntity): Long

    @Update
    suspend fun updateRide(ride: RideEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: TrackPointEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<TrackPointEntity>)

    @Query("SELECT * FROM track_points WHERE rideId = :rideId ORDER BY timestamp ASC")
    suspend fun getPointsForRide(rideId: Long): List<TrackPointEntity>

    @Query("SELECT COUNT(*) FROM track_points WHERE rideId = :rideId")
    suspend fun getPointCountForRide(rideId: Long): Int

    @Query("SELECT * FROM rides WHERE id = :rideId")
    suspend fun getRideById(rideId: Long): RideEntity?

    @Query("SELECT * FROM rides WHERE startTime = :startTime LIMIT 1")
    suspend fun getRideByStartTime(startTime: Long): RideEntity?

    @Query("SELECT * FROM rides ORDER BY startTime DESC")
    suspend fun getAllRides(): List<RideEntity>

    @Query("SELECT * FROM rides WHERE isFinished = 0 ORDER BY startTime DESC LIMIT 1")
    suspend fun getLatestUnfinishedRide(): RideEntity?

    @Query("DELETE FROM rides WHERE id = :rideId")
    suspend fun deleteRide(rideId: Long)
    
    @Query("DELETE FROM rides WHERE startTime = :startTime")
    suspend fun deleteRideByStartTime(startTime: Long)
}
