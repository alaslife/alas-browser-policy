package com.sun.alasbrowser.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sun.alasbrowser.MainActivity
import com.sun.alasbrowser.downloads.AlasDownloadManager
import com.sun.alasbrowser.ui.DownloadsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AlasDownloadService : Service() {

    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, createNotification("Alas Browser", "Download Service Active"), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, createNotification("Alas Browser", "Download Service Active"))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // In a real app, this would coordinate with DownloadManager to show specific download progress
        val title = intent?.getStringExtra("title") ?: "Downloading..."
        val progress = intent?.getIntExtra("progress", -1) ?: -1
        val speed = intent?.getStringExtra("speed") ?: ""
        val eta = intent?.getStringExtra("eta") ?: ""
        
        if (progress >= 0) {
             updateNotification(title, progress, speed, eta)
        }
        
        return START_NOT_STICKY
    }
    
    private fun updateNotification(title: String, progress: Int, speed: String, eta: String) {
        val content = if (speed.isNotEmpty()) "$progress% • $speed • $eta" else "Progress: $progress%"
        notificationManager.notify(1, createNotification(title, content))
    }

    private fun createNotification(title: String, content: String): Notification {
        val intent = Intent(this, DownloadsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "alas_downloads"
        
        fun start(context: Context) {
            val intent = Intent(context, AlasDownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            context.stopService(Intent(context, AlasDownloadService::class.java))
        }
    }
}
