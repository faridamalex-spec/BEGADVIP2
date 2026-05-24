package com.example

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class NotificationHelper(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "begad_vip_downloads"
        private const val CHANNEL_NAME = "BEGAD VIP Media Downloads"
        private const val CHANNEL_DESC = "Notifications for theater plays and movies downloading offline in BEGAD VIP"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission")
    fun showProgressNotification(id: String, filename: String, progress: Int, isDone: Boolean = false) {
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id.hashCode(),
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("🍿 BEGAD VIP Premium Downloader")
            .setColor(0xFFE50914.toInt()) // Cinema Red Theme
            .setOngoing(!isDone)
            .setAutoCancel(isDone)
            .setContentIntent(pendingIntent)

        if (isDone) {
            builder.setContentText("🎬 Complete! $filename matches local cache and is ready to stream.")
                .setProgress(0, 0, false)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
        } else {
            builder.setContentText("📥 Buffering Offline Payload: $filename ($progress%)")
                .setProgress(100, progress, false)
                .setSmallIcon(android.R.drawable.stat_sys_download)
        }

        try {
            NotificationManagerCompat.from(context).notify(id.hashCode(), builder.build())
        } catch (e: SecurityException) {
            // Occurs when permissions aren't fully prompted on post-13 yet
        }
    }
}
