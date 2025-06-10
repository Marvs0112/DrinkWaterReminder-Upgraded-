package com.beny.drinkwaterreminder

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class WaterReminderService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    companion object {
        private const val TAG = "WaterReminderService"
        private const val NOTIFICATION_ID = 1
        private const val WAKELOCK_TIMEOUT = 3 * 60 * 1000L // 3 minutes
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        // Create notification channel
        createNotificationChannel()
        
        // Start foreground immediately
        startForeground(NOTIFICATION_ID, createForegroundNotification())
        
        // Acquire wake lock
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "DrinkWaterReminder:WaterReminderServiceLock"
        ).apply {
            setReferenceCounted(false)
            acquire(WAKELOCK_TIMEOUT)
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        
        val reminderId = intent?.getIntExtra("REMINDER_ID", -1) ?: -1
        val cupSize = intent?.getIntExtra("CUP_SIZE", 250) ?: 250
        
        // Show the actual reminder notification
        showReminderNotification(reminderId, cupSize)
        
        // Play sound and vibrate
        playAlarmSound()
        vibrate()
        
        // Stop service after delay
        Handler(Looper.getMainLooper()).postDelayed({
            stopSelf()
        }, 30000) // 30 seconds
        
        // If service is killed, restart it
        return START_STICKY
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MainActivity.CHANNEL_ID,
                "Water Reminder",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Water reminder notifications"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, MainActivity.CHANNEL_ID)
            .setContentTitle("Water Reminder Active")
            .setContentText("Monitoring your water intake schedule")
            .setSmallIcon(R.drawable.ic_baseline_local_drink_24)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun showReminderNotification(reminderId: Int, cupSize: Int) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("FROM_NOTIFICATION", true)
            putExtra("REMINDER_ID", reminderId)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            reminderId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val drinkActionIntent = Intent(this, WaterReminderReceiver::class.java).apply {
            action = "DRINK_WATER_ACTION"
            putExtra("REMINDER_ID", reminderId)
        }
        
        val drinkActionPendingIntent = PendingIntent.getBroadcast(
            this,
            reminderId + 1000,
            drinkActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, MainActivity.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_baseline_local_drink_24)
            .setContentTitle("Time to Drink Water!")
            .setContentText("Drink ${cupSize}ml of water now to stay hydrated")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .addAction(R.drawable.ic_baseline_local_drink_24, "I Drank Water", drinkActionPendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
        
        NotificationManagerCompat.from(this).notify(reminderId, notification)
    }
    
    private fun playAlarmSound() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setVolume(1.0f, 1.0f)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing alarm sound", e)
        }
    }
    
    private fun vibrate() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 500, 500, 500, 500, 500),
                    intArrayOf(0, 255, 0, 255, 0, 255),
                    -1
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 500, 500, 500, 500, 500), -1)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
        
        vibrator?.cancel()
        vibrator = null
        
        wakeLock?.release()
        wakeLock = null
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
} 