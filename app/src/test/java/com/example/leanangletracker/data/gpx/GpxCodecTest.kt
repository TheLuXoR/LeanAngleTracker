package com.example.leanangletracker.data.gpx

import com.example.leanangletracker.RideSession
import com.example.leanangletracker.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.Instant

class GpxCodecTest {

    @Test
    fun `LeanAngleTracker export round trips values and escaped name`() {
        val original = RideSession(
            startedAtMs = 1_700_000_000_000L,
            endedAtMs = 1_700_000_001_000L,
            name = "A & B <Tour>",
            points = listOf(
                trackPoint(
                    timestampMs = 1_700_000_000_000L,
                    latitude = 48.1,
                    longitude = 11.5,
                    speedKmh = 42.5f,
                    leanAngleDeg = -31.5f
                ),
                trackPoint(
                    timestampMs = 1_700_000_001_000L,
                    latitude = 48.1001,
                    longitude = 11.5001,
                    speedKmh = 45f,
                    leanAngleDeg = 28f
                )
            )
        )

        val encoded = GpxCodec.encode(original, defaultName = "Fallback")
        val decoded = decode(encoded)

        assertTrue(encoded.contains("<name>A &amp; B &lt;Tour&gt;</name>"))
        assertEquals(original.name, decoded.name)
        assertEquals(original.points.map { it.latitude }, decoded.points.map { it.latitude })
        assertEquals(original.points.map { it.longitude }, decoded.points.map { it.longitude })
        assertEquals(original.points.map { it.speedKmh }, decoded.points.map { it.speedKmh })
        assertEquals(original.points.map { it.leanAngleDeg }, decoded.points.map { it.leanAngleDeg })
    }

    @Test
    fun `standard GPX speed is converted from meters per second`() {
        val decoded = decode(
            """
            <gpx version="1.0" xmlns="http://www.topografix.com/GPX/1/0">
              <trk><name>Standard</name><trkseg>
                <trkpt lat="48.0" lon="11.0">
                  <time>2024-01-01T12:00:00Z</time>
                  <speed>10</speed>
                </trkpt>
              </trkseg></trk>
            </gpx>
            """.trimIndent()
        )

        assertEquals(36f, decoded.points.single().speedKmh, 0.001f)
    }

    @Test
    fun `LeanAngleTracker speed takes precedence over standard speed`() {
        val decoded = decode(
            """
            <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
              <trk><trkseg>
                <trkpt lat="48.0" lon="11.0">
                  <time>2024-01-01T12:00:00Z</time>
                  <speed>10</speed>
                  <extensions><speedKmh>81</speedKmh><leanDeg>-44</leanDeg></extensions>
                </trkpt>
              </trkseg></trk>
            </gpx>
            """.trimIndent()
        )

        assertEquals(81f, decoded.points.single().speedKmh, 0.001f)
        assertEquals(-44f, decoded.points.single().leanAngleDeg, 0.001f)
    }

    @Test
    fun `missing speed is derived from distance and source time`() {
        val decoded = decode(
            """
            <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
              <trk><trkseg>
                <trkpt lat="0.0" lon="0.0"><time>2024-01-01T12:00:00Z</time></trkpt>
                <trkpt lat="0.0" lon="0.001"><time>2024-01-01T12:00:10Z</time></trkpt>
              </trkseg></trk>
            </gpx>
            """.trimIndent()
        )

        assertEquals(0f, decoded.points.first().speedKmh, 0.001f)
        assertEquals(40.03f, decoded.points.last().speedKmh, 0.2f)
        assertEquals(10_000L, decoded.accumulatedTimeMs)
        assertEquals(111.2f, decoded.trackLengthMeters, 0.5f)
    }

    @Test
    fun `segment boundaries do not add distance duration or derived speed`() {
        val decoded = decode(
            """
            <gpx version="1.1">
              <trk>
                <trkseg>
                  <trkpt lat="0" lon="0"><time>2024-01-01T12:00:00Z</time></trkpt>
                </trkseg>
                <trkseg>
                  <trkpt lat="50" lon="50"><time>2024-01-01T13:00:00Z</time></trkpt>
                </trkseg>
              </trk>
            </gpx>
            """.trimIndent()
        )

        assertEquals(0f, decoded.trackLengthMeters, 0f)
        assertEquals(0L, decoded.accumulatedTimeMs)
        assertEquals(listOf(0, 1), decoded.points.map { it.lapIndex })
        assertEquals(listOf(0f, 0f), decoded.points.map { it.speedKmh })
    }

    @Test
    fun `missing timestamps use monotonic import timestamps without adding duration`() {
        val importedAtMs = 1_800_000_000_000L
        val decoded = decode(
            """
            <gpx version="1.1">
              <trk><trkseg>
                <trkpt lat="48" lon="11"/>
                <trkpt lat="48.1" lon="11.1"/>
              </trkseg></trk>
            </gpx>
            """.trimIndent(),
            importedAtMs = importedAtMs
        )

        assertEquals(importedAtMs, decoded.points[0].timestampMs)
        assertEquals(importedAtMs + 1L, decoded.points[1].timestampMs)
        assertEquals(0L, decoded.accumulatedTimeMs)
        assertEquals(0f, decoded.points[1].speedKmh, 0f)
    }

    @Test
    fun `invalid coordinate points are skipped`() {
        val decoded = decode(
            """
            <gpx version="1.1"><trk><trkseg>
              <trkpt lat="91" lon="11"/>
              <trkpt lat="48" lon="11"/>
            </trkseg></trk></gpx>
            """.trimIndent()
        )

        assertEquals(1, decoded.points.size)
        assertEquals(48.0, decoded.points.single().latitude, 0.0)
    }

    @Test
    fun `file name is used when track has no name`() {
        val decoded = decode(
            "<gpx version=\"1.1\"><trk><trkseg><trkpt lat=\"48\" lon=\"11\"/></trkseg></trk></gpx>",
            sourceFileName = "Sunday Ride.GPX"
        )

        assertEquals("Sunday Ride", decoded.name)
    }

    @Test
    fun `wrong root and empty tracks fail with specific errors`() {
        assertThrows(InvalidGpxException::class.java) {
            decode("<not-gpx/>")
        }
        assertThrows(EmptyGpxException::class.java) {
            decode("<gpx version=\"1.1\"><trk><trkseg/></trk></gpx>")
        }
    }

    @Test
    fun `malformed XML is rejected`() {
        assertThrows(InvalidGpxException::class.java) {
            decode("<gpx><trk>")
        }
    }

    @Test
    fun `point limit is enforced`() {
        val xml = buildString {
            append("<gpx><trk><trkseg>")
            repeat(GPX_MAX_TRACK_POINTS + 1) {
                append("<trkpt lat=\"48\" lon=\"11\"/>")
            }
            append("</trkseg></trk></gpx>")
        }

        assertThrows(GpxTooLargeException::class.java) {
            decode(xml)
        }
    }

    private fun decode(
        xml: String,
        sourceFileName: String? = "track.gpx",
        importedAtMs: Long = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()
    ): RideSession {
        return GpxCodec.decode(
            inputStream = ByteArrayInputStream(xml.toByteArray()),
            sourceFileName = sourceFileName,
            fallbackName = "Imported ride",
            importedAtMs = importedAtMs
        )
    }

    private fun trackPoint(
        timestampMs: Long,
        latitude: Double,
        longitude: Double,
        speedKmh: Float,
        leanAngleDeg: Float
    ) = TrackPoint(
        timestampMs = timestampMs,
        latitude = latitude,
        longitude = longitude,
        speedKmh = speedKmh,
        leanAngleDeg = leanAngleDeg,
        leanFreshnessMs = 0L,
        gpsFreshnessMs = 0L,
        hasFreshGps = true
    )
}
