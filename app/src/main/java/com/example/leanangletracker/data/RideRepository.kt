package com.example.leanangletracker.data

import android.content.Context
import com.example.leanangletracker.RideSession
import com.example.leanangletracker.TrackPoint
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.Base64

class RideRepository(private val context: Context) {
    private val gson = Gson()
    private val ridesDir = File(context.filesDir, "internal_tracks").apply { if (!exists()) mkdirs() }

    fun saveRide(session: RideSession) {
        val json = gson.toJson(session)
        // Obfuscate by Base64 encoding and using a custom file extension
        val obfuscated = Base64.getEncoder().encodeToString(json.toByteArray())
        val file = File(ridesDir, "track_${session.startedAtMs}.dat")
        file.writeText(obfuscated)
    }

    fun loadRides(): List<RideSession> {
        return ridesDir.listFiles()
            ?.filter { it.extension == "dat" }
            ?.mapNotNull { file ->
                runCatching {
                    val obfuscated = file.readText()
                    val json = String(Base64.getDecoder().decode(obfuscated))
                    gson.fromJson(json, RideSession::class.java)
                }.getOrNull()
            }
            ?.sortedByDescending { it.startedAtMs }
            ?: emptyList()
    }

    fun deleteRide(session: RideSession) {
        val file = File(ridesDir, "track_${session.startedAtMs}.dat")
        if (file.exists()) file.delete()
    }
}
