package com.example.leanangletracker.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.osmdroid.util.MapTileIndex

class OpenStreetMapConfigTest {
    @Test
    fun `user agent clearly identifies the app`() {
        assertEquals(
            "LeanAngleTracker/1.2.3 (Android; com.example.leanangletracker)",
            OpenStreetMapConfig.userAgent(
                versionName = "1.2.3",
                applicationId = "com.example.leanangletracker"
            )
        )
    }

    @Test
    fun `tile source preserves configured user agent and prohibits prefetching`() {
        val policy = OpenStreetMapConfig.tileSource.tileSourcePolicy

        assertEquals(
            "https://tile.openstreetmap.org/0/0/0.png",
            OpenStreetMapConfig.tileSource.getTileURLString(
                MapTileIndex.getTileIndex(0, 0, 0)
            )
        )
        assertFalse(policy.normalizesUserAgent())
        assertFalse(policy.acceptsBulkDownload())
        assertFalse(policy.acceptsPreventive())
    }
}
