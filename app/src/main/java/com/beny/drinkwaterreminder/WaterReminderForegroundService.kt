package com.beny.drinkwaterreminder

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat

class WaterReminderForegroundService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    
    companion object {
        private const val TAG = "WaterReminderService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "WaterReminderServiceChannel"
        private const val WAKELOCK_TIMEOUT = 10 * 60 * 1000L // 10 minutes
        
        @Volatile
        private var isServiceRunning = false
        
        fun startService(context: Context) {
            if (isServiceRunning) {
                Log.d(TAG, "Service already running, skipping start")
                return
            }
            
            synchronized(this) {
                if (!isServiceRunning) {
                    val startIntent = Intent(context, WaterReminderForegroundService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(startIntent)
                    } else {
                        context.startService(startIntent)
                    }
                    isServiceRunning = true
                }
            }
        }
        
        fun stopService(context: Context) {
            synchronized(this) {
                if (isServiceRunning) {
                    val stopIntent = Intent(context, WaterReminderForegroundService::class.java)
                    context.stopService(stopIntent)
                    isServiceRunning = false
                }
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        // Create notification channel
        createNotificationChannel()
        
        // Acquire wake lock
        acquireWakeLock()
        
        // Start foreground immediately to prevent ANR
        startForeground(NOTIFICATION_ID, createNotification())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        releaseWakeLock()
        isServiceRunning = false
        
        // Schedule service restart if it wasn't explicitly stopped
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isInteractive) {
            startService(this)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "DrinkWaterReminder:ServiceWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(WAKELOCK_TIMEOUT)
            }
        }
    }
    
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Water Reminder Service",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps water reminder notifications active"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
                setSound(null, null)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Water Reminder")
            .setContentText("Monitoring your water intake")
            .setSmallIcon(R.drawable.ic_baseline_local_drink_24)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(pendingIntent)
            .build()
    }
} 