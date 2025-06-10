package com.beny.drinkwaterreminder

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.util.Log
import java.util.*
import kotlin.math.ceil
import kotlin.math.max
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.provider.Settings

class WaterReminderScheduler(private val context: Context) {
    companion object {
        private const val TAG = "WaterReminderScheduler"
        private const val MIN_INTERVAL_MINUTES = 30 // Minimum time between reminders
        private const val ALARM_TYPE = AlarmManager.RTC_WAKEUP
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleReminders() {
        Log.d(TAG, "Starting scheduleReminders()")
        // Check notification permission first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Notification permission status: $permissionGranted")
            
            if (!permissionGranted) {
                Log.e(TAG, "Notification permission not granted")
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        "Please enable notifications to receive water reminders",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return
            }
        }

        // Get user preferences
        val sp = context.getSharedPreferences("first", Context.MODE_PRIVATE)
        val wakeupTime = sp.getString("wakeupTime", "00:00") ?: "00:00"
        val bedTime = sp.getString("bedtime", "00:00") ?: "00:00"
        val weight = sp.getString("weight", "0")?.toDoubleOrNull() ?: 0.0
        val workout = sp.getString("workout", "0")?.toIntOrNull() ?: 0

        Log.d(TAG, "User preferences loaded - Wake: $wakeupTime, Bed: $bedTime, Weight: $weight, Workout: $workout")

        // Get cup size
        val cupSize = context.getSharedPreferences("cupsize", Context.MODE_PRIVATE)
            .getString("cupsize", "250")?.toIntOrNull() ?: 250
        Log.d(TAG, "Cup size loaded: $cupSize ml")

        // Calculate daily goal
        val pi = PersonalInformation(workout, wakeupTime, bedTime, weight)
        val dailyGoal = pi.GoalCalculator().toInt()
        Log.d(TAG, "Daily goal calculated: $dailyGoal ml")

        // Calculate number of reminders needed
        val numberOfDrinks = ceil(dailyGoal.toDouble() / cupSize).toInt()
        Log.d(TAG, "Number of drinks needed: $numberOfDrinks")
        
        // Parse wake up and bed time
        val wakeHour = HomeFragment.hourExtracter(wakeupTime)
        val bedHour = HomeFragment.hourExtracter(bedTime)
        
        if (wakeHour == -1 || bedHour == -1) {
            Log.e(TAG, "Invalid wake up or bed time - Wake: $wakeHour, Bed: $bedHour")
            return
        }

        // Calculate active hours
        val activeHours = if (bedHour > wakeHour) {
            bedHour - wakeHour
        } else {
            (24 - wakeHour) + bedHour
        }
        Log.d(TAG, "Active hours calculated: $activeHours")

        // Calculate interval between reminders in minutes
        val totalMinutes = activeHours * 60
        val intervalMinutes = max(totalMinutes / numberOfDrinks, MIN_INTERVAL_MINUTES)
        Log.d(TAG, "Interval between reminders: $intervalMinutes minutes")

        // Cancel existing alarms
        Log.d(TAG, "Cancelling existing alarms")
        cancelAllReminders()

        // Clear existing reminders from NotificationReminder
        NotificationReminder.times.clear()

        // Schedule new alarms
        val calendar = Calendar.getInstance()
        val currentTimeMillis = calendar.timeInMillis
        
        // Set calendar to wake up time
        calendar.apply {
            val wakeMinute = wakeupTime.split(":")[1].toInt()
            set(Calendar.HOUR_OF_DAY, wakeHour)
            set(Calendar.MINUTE, wakeMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            
            // If wake time has passed for today, schedule for tomorrow
            if (timeInMillis < currentTimeMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // Schedule reminders throughout the day
        repeat(numberOfDrinks) { index ->
            val reminderId = Random().nextInt()
            val reminderTime = calendar.clone() as Calendar
            
            // Create PendingIntent for the reminder
            val intent = Intent(context, WaterReminderReceiver::class.java).apply {
                putExtra("REMINDER_ID", reminderId)
                putExtra("CUP_SIZE", cupSize)
                // Add flags to ensure delivery
                flags = Intent.FLAG_INCLUDE_STOPPED_PACKAGES or
                        Intent.FLAG_RECEIVER_FOREGROUND
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reminderId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Create a show alarm intent
            val showAlarmIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val showAlarmPendingIntent = PendingIntent.getActivity(
                context,
                reminderId,
                showAlarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Schedule using setAlarmClock for reliable delivery
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(reminderTime.timeInMillis, showAlarmPendingIntent),
                pendingIntent
            )
            
            // Also schedule a backup alarm using setExactAndAllowWhileIdle
            alarmManager.setExactAndAllowWhileIdle(
                ALARM_TYPE,
                reminderTime.timeInMillis,
                pendingIntent
            )
            
            // Save reminder info for UI and persistence
            val timeString = String.format("%02d:%02d", 
                reminderTime.get(Calendar.HOUR_OF_DAY),
                reminderTime.get(Calendar.MINUTE)
            )
            NotificationReminder.times.add(NotificationReminder.ReminderHolder(reminderId, timeString))
            
            // Save reminder details to SharedPreferences for persistence
            saveReminderToPrefs(reminderId, reminderTime.timeInMillis, cupSize)
            
            Log.d(TAG, "Scheduled reminder #$index for $timeString")
            
            // Add interval for next reminder
            calendar.add(Calendar.MINUTE, intervalMinutes)
        }

        // Save the updated reminders list
        context.getSharedPreferences("reminder", Context.MODE_PRIVATE)
            .edit()
            .putString("times", com.google.gson.Gson().toJson(NotificationReminder.times))
            .apply()

        Log.d(TAG, "All reminders scheduled successfully")
        
        // Show confirmation toast
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                context,
                "Scheduled $numberOfDrinks water reminders throughout the day",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun saveReminderToPrefs(reminderId: Int, timeMillis: Long, cupSize: Int) {
        context.getSharedPreferences("water_reminders", Context.MODE_PRIVATE)
            .edit()
            .putLong("reminder_$reminderId", timeMillis)
            .putInt("cup_size_$reminderId", cupSize)
            .apply()
    }

    fun cancelAllReminders() {
        Log.d(TAG, "Cancelling all reminders")
        
        // Cancel existing alarms
        NotificationReminder.times.forEach { reminder ->
            val intent = Intent(context, WaterReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reminder.id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
        
        // Clear saved reminders
        NotificationReminder.times.clear()
        context.getSharedPreferences("water_reminders", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    fun restoreReminders() {
        Log.d(TAG, "Restoring saved reminders")
        
        val prefs = context.getSharedPreferences("water_reminders", Context.MODE_PRIVATE)
        val currentTime = System.currentTimeMillis()
        
        // Restore each saved reminder that hasn't passed yet
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("reminder_")) {
                val reminderId = key.removePrefix("reminder_").toInt()
                val timeMillis = value as Long
                
                if (timeMillis > currentTime) {
                    val cupSize = prefs.getInt("cup_size_$reminderId", 250)
                    
                    // Recreate the intent and schedule the alarm
                    val intent = Intent(context, WaterReminderReceiver::class.java).apply {
                        putExtra("REMINDER_ID", reminderId)
                        putExtra("CUP_SIZE", cupSize)
                        flags = Intent.FLAG_INCLUDE_STOPPED_PACKAGES or
                                Intent.FLAG_RECEIVER_FOREGROUND
                    }
                    
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        reminderId,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    
                    // Schedule using both methods for reliability
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(timeMillis, pendingIntent),
                        pendingIntent
                    )
                    alarmManager.setExactAndAllowWhileIdle(ALARM_TYPE, timeMillis, pendingIntent)
                    
                    // Add to UI list
                    val calendar = Calendar.getInstance().apply { timeInMillis = timeMillis }
                    val timeString = String.format("%02d:%02d",
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE)
                    )
                    NotificationReminder.times.add(NotificationReminder.ReminderHolder(reminderId, timeString))
                }
            }
        }
    }
} 