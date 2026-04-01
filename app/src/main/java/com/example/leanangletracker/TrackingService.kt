package com.example.leanangletracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TrackingService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    
    private var lastDistance: Float? = null
    private var lastTimeMs: Long? = null

    inner class LocalBinder : Binder() {
        fun getService(): TrackingService = this@TrackingService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_NOTIF_DISMISSED) {
            
            return START_STICKY
        }

        val distance = intent?.getFloatExtra(EXTRA_DISTANCE, -1f)?.takeIf { it >= 0 }
        val timeMs = intent?.getLongExtra(EXTRA_TIME, -1L)?.takeIf { it >= 0 }
        
        if (distance != null) lastDistance = distance
        if (timeMs != null) lastTimeMs = timeMs

        val notification = createNotification(lastDistance, lastTimeMs)
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    private fun scheduleNotificationReturn() {
        serviceScope.launch {
            delay(15000)
            val notification = createNotification(lastDistance, lastTimeMs)
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ride Tracking",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(distance: Float? = null, timeMs: Long? = null): Notification {
        val content = if (distance != null && timeMs != null) {
            val hours = (timeMs / 3600000).toInt()
            val minutes = ((timeMs % 3600000) / 60000).toInt()
            val seconds = ((timeMs % 60000) / 1000).toInt()
            val timeStr = if (hours > 0) {
                "%d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                "%02d:%02d".format(minutes, seconds)
            }
            "%.2f km | %s".format(distance, timeStr)
        } else {
            "Recording ride data..."
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val deleteIntent = Intent(this, TrackingService::class.java).apply {
            action = ACTION_NOTIF_DISMISSED
        }
        val deletePendingIntent = PendingIntent.getService(
            this, 0, deleteIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Lean Angle Tracker")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(false) // Allow swiping
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPendingIntent)
            .setDeleteIntent(deletePendingIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "tracking_channel"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_NOTIF_DISMISSED = "com.example.leanangletracker.NOTIF_DISMISSED"
        const val EXTRA_DISTANCE = "extra_distance"
        const val EXTRA_TIME = "extra_time"
    }
}
