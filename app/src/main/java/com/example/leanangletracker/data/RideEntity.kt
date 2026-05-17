package com.example.leanangletracker.data

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
    val isFinished: Boolean = false
)
