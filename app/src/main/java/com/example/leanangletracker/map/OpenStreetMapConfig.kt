package com.example.leanangletracker.map

import android.content.Context
import com.example.leanangletracker.BuildConfig
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.tileprovider.tilesource.XYTileSource

internal object OpenStreetMapConfig {
    const val COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"

    val tileSource: OnlineTileSourceBase = XYTileSource(
        "OpenStreetMap",
        0,
        19,
        256,
        ".png",
        arrayOf("https://tile.openstreetmap.org/"),
        "© OpenStreetMap contributors",
        TileSourcePolicy(
            2,
            TileSourcePolicy.FLAG_NO_BULK or
                TileSourcePolicy.FLAG_NO_PREVENTIVE or
                TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL
        )
    )

    fun initialize(context: Context) {
        val configuration = Configuration.getInstance()
        configuration.load(
            context,
            context.getSharedPreferences(
                "${context.packageName}_preferences",
                Context.MODE_PRIVATE
            )
        )
        configuration.userAgentValue = userAgent(
            versionName = BuildConfig.VERSION_NAME,
            applicationId = BuildConfig.APPLICATION_ID
        )
    }

    internal fun userAgent(versionName: String, applicationId: String): String =
        "LeanAngleTracker/$versionName (Android; $applicationId)"
}
