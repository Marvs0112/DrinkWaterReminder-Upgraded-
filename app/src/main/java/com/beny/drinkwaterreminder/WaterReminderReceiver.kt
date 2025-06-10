package com.beny.drinkwaterreminder

import android.Manifest
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

class WaterReminderReceiver : BroadcastReceiver() {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    
    companion object {
        private const val TAG = "WaterReminderReceiver"
        private const val CHANNEL_ID = "water_reminder_channel"
        private var currentAlarmId: Int = -1
        private var currentMediaPlayer: MediaPlayer? = null
        private var currentVibrator: Vibrator? = null
        private const val WAKELOCK_TIMEOUT = 10 * 60 * 1000L // 10 minutes
        private const val DEBOUNCE_INTERVAL = 30000L // 30 seconds
        private var lastNotificationTime = 0L
        
        fun stopCurrentAlarm() {
            Log.d(TAG, "Stopping current alarm")
            currentMediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            currentMediaPlayer = null
            
            currentVibrator?.cancel()
            currentVibrator = null
            
            currentAlarmId = -1
        }
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(TAG, "onReceive called with intent: ${intent?.action}")
        
        // Implement debounce
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastNotificationTime < DEBOUNCE_INTERVAL) {
            Log.d(TAG, "Debouncing notification, too soon after last one")
            return
        }
        lastNotificationTime = currentTime
        
        var wakeLock: PowerManager.WakeLock? = null
        try {
            context?.let { ctx ->
                // Acquire wake lock to ensure device processes the notification
                val powerManager = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK or PowerManager.SCREEN_BRIGHT_WAKE_LOCK,
                    "DrinkWaterReminder:NotificationWakeLock"
                ).apply {
                    acquire(10000) // 10 seconds
                }

                when (intent?.action) {
                    Intent.ACTION_BOOT_COMPLETED -> {
                        Log.d(TAG, "Handling boot completed")
                        val scheduler = WaterReminderScheduler(ctx)
                        scheduler.restoreReminders()
                    }
                    "DRINK_WATER_ACTION" -> {
                        Log.d(TAG, "Handling drink water action")
                        val reminderId = intent.getIntExtra("REMINDER_ID", -1)
                        stopCurrentAlarm(ctx)
                        NotificationManagerCompat.from(ctx).cancel(reminderId)
                        Toast.makeText(ctx, "Great job staying hydrated!", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        val reminderId = intent?.getIntExtra("REMINDER_ID", -1) ?: -1
                        val cupSize = intent?.getIntExtra("CUP_SIZE", 250) ?: 250
                        
                        // Create notification channel
                        createNotificationChannel(ctx)
                        
                        // Show notification with vibration
                        showNotification(ctx, reminderId, cupSize)
                        
                        // Start foreground service to keep app alive
                        // Only start if not already running
                        if (!isServiceRunning(ctx, WaterReminderForegroundService::class.java)) {
                            WaterReminderForegroundService.startService(ctx)
                        }
                    }
                }
            }
        } finally {
            wakeLock?.release()
        }
    }
    
    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val services = activityManager.getRunningServices(Int.MAX_VALUE)
        
        // Check if our service is in the list of running services
        return services.any { 
            it.service.className == serviceClass.name &&
            it.foreground // Only consider foreground services
        }
    }
    
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Water Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminds you to drink water"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setShowBadge(true)
                enableLights(true)
                setBypassDnd(true) // Bypass Do Not Disturb
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                importance = NotificationManager.IMPORTANCE_HIGH // Ensure high importance
            }
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun showNotification(context: Context, reminderId: Int, cupSize: Int) {
        try {
            // Create an intent for when user taps the notification
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                reminderId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Create "Drink" action
            val drinkIntent = Intent(context, WaterReminderReceiver::class.java).apply {
                action = "DRINK_WATER_ACTION"
                putExtra("REMINDER_ID", reminderId)
            }
            val drinkPendingIntent = PendingIntent.getBroadcast(
                context,
                reminderId,
                drinkIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Build the notification
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Time to Drink Water!")
                .setContentText("Stay hydrated! Drink ${cupSize}ml of water now.")
                .setSmallIcon(R.drawable.ic_baseline_local_drink_24)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true) // Show as heads-up notification
                .addAction(
                    R.drawable.ic_baseline_local_drink_24,
                    "I Drank Water",
                    drinkPendingIntent
                )
                .setVibrate(longArrayOf(0, 500, 200, 500))

            // Show the notification
            NotificationManagerCompat.from(context).notify(reminderId, builder.build())
            
            // Ensure vibration happens
            vibrate(context)
            
            // Schedule next reminder
            scheduleNextReminder(context, reminderId, cupSize)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing notification: ${e.message}")
        }
    }
    
    private fun vibrate(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 500, 200, 500),
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 500, 200, 500), -1)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error vibrating: ${e.message}")
        }
    }
    
    private fun scheduleNextReminder(context: Context, reminderId: Int, cupSize: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WaterReminderReceiver::class.java).apply {
            putExtra("REMINDER_ID", reminderId)
            putExtra("CUP_SIZE", cupSize)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }

        // Create multiple PendingIntents with different request codes
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, reminderId, intent, flags)
        val backupPendingIntent = PendingIntent.getBroadcast(context, reminderId + 1000, intent, flags)
        
        // Calculate next alarm time
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            add(Calendar.MINUTE, 1) // Add 1 minute buffer
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    // Set exact alarm with RTC_WAKEUP
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(
                            calendar.timeInMillis,
                            pendingIntent
                        ),
                        pendingIntent
                    )
                    
                    // Set backup alarm 1 minute later
                    calendar.add(Calendar.MINUTE, 1)
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        backupPendingIntent
                    )
                } else {
                    // Fallback for devices without exact alarm permission
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } else {
                // For older Android versions
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(
                        calendar.timeInMillis,
                        pendingIntent
                    ),
                    pendingIntent
                )
            }
            
            Log.d(TAG, "Scheduled next reminder for: ${calendar.time}")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling alarm: ${e.message}")
            // Fallback to less precise alarm
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }

    private fun stopCurrentAlarm(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, WaterReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
            
            // Cancel backup alarm if exists
            val backupPendingIntent = PendingIntent.getBroadcast(
                context,
                1000,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            backupPendingIntent?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping alarm: ${e.message}")
        }
    }
} 