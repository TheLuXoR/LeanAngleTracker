package com.example.leanangletracker

data class LeanExtrema(
    val maxLeftDeg: Float = 0f,
    val maxRightDeg: Float = 0f
) {
    fun include(angleDeg: Float): LeanExtrema {
        if (!angleDeg.isFinite()) return this

        return copy(
            maxLeftDeg = minOf(maxLeftDeg, angleDeg.coerceAtMost(0f)),
            maxRightDeg = maxOf(maxRightDeg, angleDeg.coerceAtLeast(0f))
        )
    }

    fun inverted(): LeanExtrema = LeanExtrema(
        maxLeftDeg = -maxRightDeg,
        maxRightDeg = -maxLeftDeg
    )

    companion object {
        val ZERO = LeanExtrema()

        fun fromAngles(anglesDeg: Iterable<Float>): LeanExtrema =
            anglesDeg.fold(ZERO) { extrema, angleDeg -> extrema.include(angleDeg) }
    }
}
