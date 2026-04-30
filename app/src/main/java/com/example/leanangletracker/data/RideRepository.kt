package com.example.leanangletracker.data

import android.content.Context
import android.util.Log
import com.example.leanangletracker.RideSession
import com.example.leanangletracker.TrackPoint
import com.google.gson.Gson
import java.io.File
import java.util.Base64

class RideRepository(private val context: Context) {
    private val gson = Gson()
    private val ridesDir = File(context.filesDir, "internal_tracks").apply { if (!exists()) mkdirs() }
    private val tempDir = File(context.cacheDir, "recording_temp").apply { if (!exists()) mkdirs() }

    fun saveRide(session: RideSession) {
        Log.d(TAG, "Saving ride: ${session.startedAtMs} with ${session.points.size} points")
        runCatching {
            val json = gson.toJson(session)
            val obfuscated = Base64.getEncoder().encodeToString(json.toByteArray())
            val file = File(ridesDir, "track_${session.startedAtMs}.dat")
            file.writeText(obfuscated)
            clearTempRide(session.startedAtMs)
        }.onFailure { e ->
            Log.e(TAG, "Failed to save ride ${session.startedAtMs}", e)
        }
    }

    fun saveTempPoints(startedAtMs: Long, points: List<TrackPoint>) {
        runCatching {
            val json = gson.toJson(points)
            val file = File(tempDir, "temp_${startedAtMs}.json")
            file.writeText(json)
        }.onFailure { e ->
            Log.e(TAG, "Failed to save temp points for ride $startedAtMs", e)
        }
    }

    fun loadTempPoints(startedAtMs: Long): List<TrackPoint> {
        val file = File(tempDir, "temp_${startedAtMs}.json")
        if (!file.exists()) return emptyList()
        return runCatching {
            val json = file.readText()
            val type = object : com.google.gson.reflect.TypeToken<List<TrackPoint>>() {}.type
            gson.fromJson<List<TrackPoint>>(json, type)
        }.getOrElse { e ->
            Log.e(TAG, "Failed to load temp points for ride $startedAtMs", e)
            emptyList()
        }
    }

    fun clearTempRide(startedAtMs: Long) {
        Log.d(TAG, "Clearing temp ride: $startedAtMs")
        File(tempDir, "temp_${startedAtMs}.json").delete()
    }

    fun getUnfinishedRideIds(): List<Long> {
        return tempDir.listFiles()
            ?.filter { it.name.startsWith("temp_") && it.extension == "json" }
            ?.mapNotNull { it.name.removePrefix("temp_").removeSuffix(".json").toLongOrNull() }
            ?: emptyList()
    }

    fun loadRides(): List<RideSession> {
        Log.d(TAG, "Loading all rides from storage")
        return ridesDir.listFiles()
            ?.filter { it.extension == "dat" }
            ?.mapNotNull { file ->
                runCatching {
                    val obfuscated = file.readText()
                    val json = String(Base64.getDecoder().decode(obfuscated))
                    gson.fromJson(json, RideSession::class.java)
                }.getOrElse { e ->
                    Log.e(TAG, "Failed to load ride file: ${file.name}", e)
                    null
                }
            }
            ?.sortedByDescending { it.startedAtMs }
            ?: emptyList()
    }

    fun deleteRide(session: RideSession) {
        Log.i(TAG, "Deleting ride: ${session.startedAtMs}")
        val file = File(ridesDir, "track_${session.startedAtMs}.dat")
        if (file.exists()) {
            file.delete()
        }
        clearTempRide(session.startedAtMs)
    }

    companion object {
        private const val TAG = "RideRepository"
    }
}
