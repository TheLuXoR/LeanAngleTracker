package com.example.leanangletracker.ui.tracking

data class RideStats(
    val distanceKm: Double,
    val maxLeanIndex: Int,
    val maxLeanVal: Float,
    val maxSpeedIndex: Int,
    val maxSpeedVal: Float,
    val avgSpeed: Float,
)