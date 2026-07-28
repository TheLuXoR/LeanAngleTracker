package com.example.leanangletracker.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.leanangletracker.RideSession
import com.example.leanangletracker.TrackPoint
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RideRepositoryImportTest {
    private lateinit var database: RideDatabase
    private lateinit var repository: RideRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, RideDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RideRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun importedRideAndPointsArePersistedTogether() = runBlocking {
        val session = importedSession(points = listOf(trackPoint(latitude = 48.0)))

        val saved = repository.importRide(session)
        val loaded = repository.loadFullSession(saved.rideId)

        requireNotNull(loaded)
        assertEquals("Imported route", loaded.name)
        assertEquals("1.2 km: Munich", loaded.routeDescription)
        assertEquals(1, loaded.points.size)
        assertEquals(48.0, loaded.points.single().latitude, 0.0)
        assertEquals(1_200f, loaded.trackLengthMeters, 0f)
    }

    @Test
    fun validationFailureRollsBackPreviouslyInsertedBatches() {
        val validPoints = List(1_000) { index ->
            trackPoint(latitude = 48.0 + index * 0.000001)
        }
        val session = importedSession(
            points = validPoints + trackPoint(latitude = Double.NaN)
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.importRide(session) }
        }

        runBlocking {
            assertEquals(emptyList<RideEntity>(), database.rideDao().getAllRides())
        }
    }

    private fun importedSession(points: List<TrackPoint>) = RideSession(
        startedAtMs = 1_700_000_000_000L,
        endedAtMs = 1_700_000_001_000L,
        points = points,
        name = "Imported route",
        routeDescription = "1.2 km: Munich",
        isFinished = true,
        accumulatedTimeMs = 1_000L,
        trackLengthMeters = 1_200f,
        maxLeftDeg = -20f,
        maxRightDeg = 25f,
        sumSpeedKmh = 42f,
        sumAbsLeanDeg = 20f
    )

    private fun trackPoint(latitude: Double) = TrackPoint(
        timestampMs = 1_700_000_000_000L,
        latitude = latitude,
        longitude = 11.0,
        speedKmh = 42f,
        leanAngleDeg = 20f,
        leanFreshnessMs = 0L,
        gpsFreshnessMs = 0L,
        hasFreshGps = true
    )
}
