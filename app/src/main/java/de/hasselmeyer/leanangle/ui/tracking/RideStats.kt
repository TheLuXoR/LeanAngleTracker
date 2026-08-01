package de.hasselmeyer.leanangle.ui.tracking

import de.hasselmeyer.leanangle.TrackPoint
import kotlin.math.abs

data class RideStats(
    val distanceKm: Double,
    val maxLeanIndex: Int,
    val maxLeanVal: Float,
    val maxSpeedIndex: Int,
    val maxSpeedVal: Float,
    val avgSpeed: Float,
)

internal data class MaxLeanPoint(
    val index: Int,
    val absoluteAngleDeg: Float,
)

internal fun findMaxLeanPoint(points: List<TrackPoint>): MaxLeanPoint? {
    if (points.isEmpty()) return null

    var maxIndex = 0
    var maxAbsoluteAngleDeg = abs(points.first().leanAngleDeg)

    for (index in 1..points.lastIndex) {
        val absoluteAngleDeg = abs(points[index].leanAngleDeg)
        if (absoluteAngleDeg > maxAbsoluteAngleDeg) {
            maxIndex = index
            maxAbsoluteAngleDeg = absoluteAngleDeg
        }
    }

    return MaxLeanPoint(
        index = maxIndex,
        absoluteAngleDeg = maxAbsoluteAngleDeg,
    )
}
