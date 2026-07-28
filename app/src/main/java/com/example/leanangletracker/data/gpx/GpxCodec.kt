package com.example.leanangletracker.data.gpx

import com.example.leanangletracker.RideSession
import com.example.leanangletracker.TrackPoint
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.time.Instant
import javax.xml.parsers.SAXParserFactory
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

internal const val GPX_MAX_TRACK_POINTS = 250_000

internal sealed class GpxImportException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

internal class InvalidGpxException(cause: Throwable? = null) :
    GpxImportException("The selected file is not a valid GPX document.", cause)

internal class EmptyGpxException :
    GpxImportException("The GPX document does not contain valid track points.")

internal class GpxTooLargeException :
    GpxImportException("The GPX document contains too many track points.")

internal object GpxCodec {
    private const val GPX_NAMESPACE = "http://www.topografix.com/GPX/1/1"

    fun decode(
        inputStream: InputStream,
        sourceFileName: String?,
        fallbackName: String,
        importedAtMs: Long
    ): RideSession {
        val handler = GpxHandler()
        try {
            val factory = SAXParserFactory.newInstance().apply {
                isNamespaceAware = true
                isValidating = false
            }
            disableUnsafeXmlFeatures(factory)
            factory.newSAXParser().xmlReader.apply {
                contentHandler = handler
                errorHandler = handler
                parse(InputSource(inputStream))
            }
        } catch (error: GpxImportException) {
            throw error
        } catch (error: SAXException) {
            when (val cause = error.cause) {
                is GpxImportException -> throw cause
                else -> throw InvalidGpxException(error)
            }
        } catch (error: Exception) {
            throw InvalidGpxException(error)
        }

        if (!handler.hasGpxRoot) throw InvalidGpxException()
        if (handler.points.isEmpty()) throw EmptyGpxException()

        return buildRideSession(
            rawPoints = handler.points,
            trackName = handler.firstTrackName,
            sourceFileName = sourceFileName,
            fallbackName = fallbackName,
            importedAtMs = importedAtMs
        )
    }

    fun encode(rideSession: RideSession, defaultName: String): String = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        appendLine("<gpx version=\"1.1\" creator=\"LeanAngleTracker\" xmlns=\"$GPX_NAMESPACE\">")
        appendLine("  <trk>")
        appendLine("    <name>${escapeXml(rideSession.name?.takeIf(String::isNotBlank) ?: defaultName)}</name>")

        val pointGroups = rideSession.points
            .fold(mutableListOf<MutableList<TrackPoint>>()) { groups, point ->
                val currentGroup = groups.lastOrNull()
                if (currentGroup == null || currentGroup.last().lapIndex != point.lapIndex) {
                    groups += mutableListOf(point)
                } else {
                    currentGroup += point
                }
                groups
            }

        pointGroups.forEach { points ->
            appendLine("    <trkseg>")
            points.forEach { point ->
                appendLine("      <trkpt lat=\"${point.latitude}\" lon=\"${point.longitude}\">")
                appendLine("        <time>${Instant.ofEpochMilli(point.timestampMs)}</time>")
                appendLine("        <extensions>")
                appendLine("          <speedKmh>${point.speedKmh}</speedKmh>")
                appendLine("          <leanDeg>${point.leanAngleDeg}</leanDeg>")
                appendLine("        </extensions>")
                appendLine("      </trkpt>")
            }
            appendLine("    </trkseg>")
        }

        appendLine("  </trk>")
        appendLine("</gpx>")
    }

    private fun disableUnsafeXmlFeatures(factory: SAXParserFactory) {
        listOf(
            "http://apache.org/xml/features/disallow-doctype-decl",
            "http://xml.org/sax/features/external-general-entities",
            "http://xml.org/sax/features/external-parameter-entities",
            "http://apache.org/xml/features/nonvalidating/load-external-dtd"
        ).forEach { feature ->
            runCatching {
                factory.setFeature(
                    feature,
                    feature == "http://apache.org/xml/features/disallow-doctype-decl"
                )
            }
        }
    }

    private fun buildRideSession(
        rawPoints: List<RawTrackPoint>,
        trackName: String?,
        sourceFileName: String?,
        fallbackName: String,
        importedAtMs: Long
    ): RideSession {
        var previousNormalizedTimestamp = Long.MIN_VALUE
        var trackLengthMeters = 0f
        var accumulatedTimeMs = 0L
        var sumSpeedKmh = 0f
        var sumAbsLeanDeg = 0f
        var maxLeftDeg = 0f
        var maxRightDeg = 0f

        val points = rawPoints.mapIndexed { index, rawPoint ->
            val sourceTimestampMs = rawPoint.timestampMs
            val normalizedTimestamp = if (previousNormalizedTimestamp == Long.MIN_VALUE) {
                sourceTimestampMs ?: importedAtMs
            } else {
                sourceTimestampMs
                    ?.takeIf { it > previousNormalizedTimestamp }
                    ?: previousNormalizedTimestamp + 1L
            }
            previousNormalizedTimestamp = normalizedTimestamp

            val previous = rawPoints.getOrNull(index - 1)
            val isSameSegment = previous?.segmentIndex == rawPoint.segmentIndex
            val segmentDistanceMeters = if (previous != null && isSameSegment) {
                distanceMeters(
                    previous.latitude,
                    previous.longitude,
                    rawPoint.latitude,
                    rawPoint.longitude
                )
            } else {
                0f
            }
            trackLengthMeters += segmentDistanceMeters

            val previousSourceTimestampMs = previous?.timestampMs
            val sourceDeltaMs = if (
                previous != null &&
                isSameSegment &&
                previousSourceTimestampMs != null &&
                sourceTimestampMs != null
            ) {
                sourceTimestampMs - previousSourceTimestampMs
            } else {
                0L
            }
            if (sourceDeltaMs > 0L) accumulatedTimeMs += sourceDeltaMs

            val explicitSpeedKmh = rawPoint.speedKmh
            val speedMetersPerSecond = rawPoint.speedMetersPerSecond
            val speedKmh = when {
                explicitSpeedKmh?.isFinite() == true && explicitSpeedKmh >= 0f ->
                    explicitSpeedKmh
                speedMetersPerSecond?.isFinite() == true && speedMetersPerSecond >= 0f ->
                    speedMetersPerSecond * 3.6f
                sourceDeltaMs > 0L ->
                    segmentDistanceMeters / (sourceDeltaMs / 1_000f) * 3.6f
                else -> 0f
            }
            val explicitLeanAngleDeg = rawPoint.leanAngleDeg
            val leanAngleDeg = explicitLeanAngleDeg?.takeIf(Float::isFinite) ?: 0f

            sumSpeedKmh += speedKmh
            sumAbsLeanDeg += abs(leanAngleDeg)
            maxLeftDeg = minOf(maxLeftDeg, leanAngleDeg)
            maxRightDeg = maxOf(maxRightDeg, leanAngleDeg)

            TrackPoint(
                timestampMs = normalizedTimestamp,
                latitude = rawPoint.latitude,
                longitude = rawPoint.longitude,
                speedKmh = speedKmh,
                leanAngleDeg = leanAngleDeg,
                leanFreshnessMs = 0L,
                gpsFreshnessMs = 0L,
                hasFreshGps = true,
                lapIndex = rawPoint.segmentIndex
            )
        }

        val name = trackName?.trim()?.takeIf(String::isNotEmpty)
            ?: sourceFileName
                ?.substringAfterLast('/')
                ?.removeGpxSuffix()
                ?.trim()
                ?.takeIf(String::isNotEmpty)
            ?: fallbackName

        return RideSession(
            startedAtMs = points.first().timestampMs,
            endedAtMs = points.last().timestampMs,
            points = points,
            name = name,
            isFinished = true,
            accumulatedTimeMs = accumulatedTimeMs,
            trackLengthMeters = trackLengthMeters,
            maxLeftDeg = maxLeftDeg,
            maxRightDeg = maxRightDeg,
            sumSpeedKmh = sumSpeedKmh,
            sumAbsLeanDeg = sumAbsLeanDeg
        )
    }

    private fun String.removeGpxSuffix(): String =
        if (endsWith(".gpx", ignoreCase = true)) dropLast(4) else this

    private fun escapeXml(value: String): String = buildString(value.length) {
        value.forEach { character ->
            append(
                when (character) {
                    '&' -> "&amp;"
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '"' -> "&quot;"
                    '\'' -> "&apos;"
                    else -> character
                }
            )
        }
    }

    private fun distanceMeters(
        latitude1: Double,
        longitude1: Double,
        latitude2: Double,
        longitude2: Double
    ): Float {
        val earthRadiusMeters = 6_371_000.0
        val latitudeDelta = Math.toRadians(latitude2 - latitude1)
        val longitudeDelta = Math.toRadians(longitude2 - longitude1)
        val latitude1Radians = Math.toRadians(latitude1)
        val latitude2Radians = Math.toRadians(latitude2)
        val haversine = sin(latitudeDelta / 2.0) * sin(latitudeDelta / 2.0) +
            cos(latitude1Radians) * cos(latitude2Radians) *
            sin(longitudeDelta / 2.0) * sin(longitudeDelta / 2.0)
        val angle = 2.0 * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
        return max(0.0, earthRadiusMeters * angle).toFloat()
    }
}

private data class RawTrackPoint(
    val latitude: Double,
    val longitude: Double,
    val segmentIndex: Int,
    var timestampMs: Long? = null,
    var speedMetersPerSecond: Float? = null,
    var speedKmh: Float? = null,
    var leanAngleDeg: Float? = null
)

private class GpxHandler : DefaultHandler() {
    val points = mutableListOf<RawTrackPoint>()
    var hasGpxRoot = false
        private set
    var firstTrackName: String? = null
        private set

    private var rootSeen = false
    private var insideTrack = false
    private var insideTrackPoint = false
    private var segmentIndex = -1
    private var trackPointCount = 0
    private var currentPoint: RawTrackPoint? = null
    private var capturedElement: String? = null
    private val capturedText = StringBuilder()

    override fun startElement(
        uri: String?,
        localName: String?,
        qName: String?,
        attributes: Attributes
    ) {
        val elementName = normalizedName(localName, qName)
        if (!rootSeen) {
            rootSeen = true
            hasGpxRoot = elementName == "gpx"
            if (!hasGpxRoot) throw InvalidGpxException()
        }

        when (elementName) {
            "trk" -> insideTrack = true
            "trkseg" -> if (insideTrack) segmentIndex++
            "trkpt" -> {
                if (!insideTrack) return
                trackPointCount++
                if (trackPointCount > GPX_MAX_TRACK_POINTS) throw GpxTooLargeException()
                insideTrackPoint = true
                val latitude = attributes.valueByName("lat")?.toDoubleOrNull()
                val longitude = attributes.valueByName("lon")?.toDoubleOrNull()
                currentPoint = if (
                    latitude != null &&
                    longitude != null &&
                    latitude.isFinite() &&
                    longitude.isFinite() &&
                    latitude in -90.0..90.0 &&
                    longitude in -180.0..180.0
                ) {
                    RawTrackPoint(
                        latitude = latitude,
                        longitude = longitude,
                        segmentIndex = segmentIndex.coerceAtLeast(0)
                    )
                } else {
                    null
                }
            }
        }

        val shouldCapture = when {
            elementName == "name" && insideTrack && firstTrackName == null && !insideTrackPoint -> true
            insideTrackPoint && elementName in setOf("time", "speed", "speedKmh", "leanDeg") -> true
            else -> false
        }
        if (shouldCapture) {
            capturedElement = elementName
            capturedText.clear()
        }
    }

    override fun characters(characters: CharArray, start: Int, length: Int) {
        if (capturedElement != null) capturedText.append(characters, start, length)
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        val elementName = normalizedName(localName, qName)
        if (capturedElement == elementName) {
            val value = capturedText.toString().trim()
            when (elementName) {
                "name" -> if (value.isNotEmpty() && firstTrackName == null) firstTrackName = value
                "time" -> currentPoint?.timestampMs = value.toInstantMillisecondsOrNull()
                "speed" -> currentPoint?.speedMetersPerSecond = value.toFloatOrNull()
                "speedKmh" -> currentPoint?.speedKmh = value.toFloatOrNull()
                "leanDeg" -> currentPoint?.leanAngleDeg = value.toFloatOrNull()
            }
            capturedElement = null
            capturedText.clear()
        }

        when (elementName) {
            "trkpt" -> {
                currentPoint?.let(points::add)
                currentPoint = null
                insideTrackPoint = false
            }
            "trk" -> insideTrack = false
        }
    }

    private fun Attributes.valueByName(expectedName: String): String? {
        for (index in 0 until length) {
            if (normalizedName(getLocalName(index), getQName(index)) == expectedName) {
                return getValue(index)
            }
        }
        return null
    }

    private fun String.toInstantMillisecondsOrNull(): Long? =
        runCatching { Instant.parse(this).toEpochMilli() }.getOrNull()

    private fun normalizedName(localName: String?, qName: String?): String =
        localName?.takeIf(String::isNotEmpty)
            ?: qName.orEmpty().substringAfter(':')
}
