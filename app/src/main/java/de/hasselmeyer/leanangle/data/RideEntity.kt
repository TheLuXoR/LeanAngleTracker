package de.hasselmeyer.leanangle.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rides")
data class RideEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val name: String? = null,
    val routeDescription: String? = null,
    val isFinished: Boolean = false,
    
    // Session Stats (Running totals)
    val accumulatedTimeMs: Long = 0L,
    val trackLengthMeters: Float = 0f,
    val maxLeftDeg: Float = 0f,
    val maxRightDeg: Float = 0f,
    val sumSpeedKmh: Float = 0f,
    val sumAbsLeanDeg: Float = 0f,
    val pointCount: Int = 0
)
