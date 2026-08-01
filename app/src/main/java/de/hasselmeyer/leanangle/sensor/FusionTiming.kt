package de.hasselmeyer.leanangle.sensor

internal fun fusionDeltaSeconds(previousTimestampNs: Long, timestampNs: Long): Float? {
    val deltaNs = timestampNs - previousTimestampNs
    if (deltaNs <= 0L) return null
    val deltaSeconds = deltaNs / 1_000_000_000f
    return deltaSeconds.takeIf { it <= 0.05f }
}
