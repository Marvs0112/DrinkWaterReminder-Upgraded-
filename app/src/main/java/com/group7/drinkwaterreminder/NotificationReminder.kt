package com.group7.drinkwaterreminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.TimePicker
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.group7.drinkwaterreminder.databinding.ActivityNotificationBinding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.Serializable
import java.util.*

class NotificationReminder : AppCompatActivity() {
    private lateinit var binding: ActivityNotificationBinding
    private lateinit var reminderAdapter: ReminderAdapter
    private lateinit var alarmManager: AlarmManager
    private var pendingTimePickerCallback: (() -> Unit)? = null
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            pendingTimePickerCallback?.invoke()
        } else {
            showPermissionExplanationDialog()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        createNotificationChannel()  // Create channel on startup
        loadData()
        setupRecyclerView()
        setupFloatingActionButton()
        checkPermissions()
        
        // Add test button for auto-schedule
        binding.autoScheduleButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                showAlarmPermissionDialog()
                return@setOnClickListener
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    pendingTimePickerCallback = { autoScheduleReminders() }
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return@setOnClickListener
                }
            }
            
            autoScheduleReminders()
        }
    }
    
    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                showAlarmPermissionDialog()
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    
    private fun showAlarmPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Alarm Permission Required")
            .setMessage("To set water reminders, this app needs permission to schedule exact alarms. Please enable this in Settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showPermissionExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Notification Permission Required")
            .setMessage("To receive water reminders, this app needs notification permission. Would you like to grant it in Settings?")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.fromParts("package", packageName, null)
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun setupRecyclerView() {
        reminderAdapter = ReminderAdapter(this)
        binding.ReminderRecycleView.apply {
            adapter = reminderAdapter
            layoutManager = LinearLayoutManager(this@NotificationReminder)
        }
    }
    
    private fun setupFloatingActionButton() {
        binding.floatingActionButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                showAlarmPermissionDialog()
                return@setOnClickListener
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    pendingTimePickerCallback = { showTimePickerDialog() }
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return@setOnClickListener
                }
            }
            
            showTimePickerDialog()
        }
    }
    
    private fun showTimePickerDialog() {
        val calendar = Calendar.getInstance()
        val timePickerDialog = TimePickerDialog(
            this,
            { _: TimePicker, hourOfDay: Int, minute: Int ->
                val time = String.format("%02d:%02d", hourOfDay, minute)
                val id = Random().nextInt()
                
                scheduleAlarm(hourOfDay, minute, id)
                times.add(ReminderHolder(id, time))
                reminderAdapter.notifyDataSetChanged()
                saveData()
                
                Toast.makeText(this, "Water reminder alarm set for $time", Toast.LENGTH_SHORT).show()
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            android.text.format.DateFormat.is24HourFormat(this)
        )
        timePickerDialog.show()
    }
    
     fun scheduleAlarm(hourOfDay: Int, minute: Int, id: Int) {
        val context = applicationContext
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            
            // If time has already passed today, set for tomorrow
            if (before(Calendar.getInstance())) {
                add(Calendar.DATE, 1)
            }
        }
        
        val intent = Intent(context, WaterReminderReceiver::class.java).apply {
            putExtra("REMINDER_ID", id)
            putExtra("REMINDER_HOUR", hourOfDay)
            putExtra("REMINDER_MINUTE", minute)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Set exact repeating alarm
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(calendar.timeInMillis, pendingIntent),
                pendingIntent
            )
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }
    
    fun cancelAlarm(id: Int) {
        val context = applicationContext
        val intent = Intent(context, WaterReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
    
    private fun loadData() {
        val sharedPreferences = getSharedPreferences("reminder", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = sharedPreferences.getString("times", null)
        
        if (json != null) {
            val type = object : TypeToken<ArrayList<ReminderHolder>>() {}.type
            times.clear()
            times.addAll(gson.fromJson(json, type))
            
            // Reschedule all alarms after app restart
            times.forEach { reminder ->
                val timeParts = reminder.time.split(":")
                if (timeParts.size == 2) {
                    val hour = timeParts[0].toInt()
                    val minute = timeParts[1].toInt()
                    scheduleAlarm(hour, minute, reminder.id)
                }
            }
        }
    }
    
    private fun saveData() {
        getSharedPreferences("reminder", Context.MODE_PRIVATE)
            .edit()
            .putString("times", Gson().toJson(times))
            .apply()
    }
    
    data class ReminderHolder(
        val id: Int,
        val time: String
    ) : Serializable {
        companion object {
            private const val serialVersionUID = 1L
        }
    }
    
    companion object {
        val times = ArrayList<ReminderHolder>()
    }
    
    // --- AUTO-SCHEDULE REMINDERS BASED ON USER GOAL, WAKE/BED TIME, CUP SIZE ---
    fun autoScheduleReminders() {
        val context = applicationContext
        Log.d("NotificationReminder", "Starting auto-schedule reminders")
        
        // 1. Get user preferences
        val goal = getSharedPreferences("dailyGoal", Context.MODE_PRIVATE).getInt("goal", 0)
        val wakeupTime = getSharedPreferences("first", Context.MODE_PRIVATE).getString("wakeupTime", "07:00") ?: "07:00"
        val bedtime = getSharedPreferences("first", Context.MODE_PRIVATE).getString("bedtime", "22:00") ?: "22:00"
        val cupSize = getSharedPreferences("cupsize", Context.MODE_PRIVATE).getString("cupsize", "250")?.toIntOrNull() ?: 250

        Log.d("NotificationReminder", "Loaded preferences - Goal: $goal, Wake: $wakeupTime, Bed: $bedtime, Cup: $cupSize")

        if (goal <= 0 || cupSize <= 0) {
            Log.w("NotificationReminder", "Invalid goal or cup size - Goal: $goal, Cup: $cupSize")
            Toast.makeText(this, "Please set a valid daily goal and cup size first", Toast.LENGTH_SHORT).show()
            return
        }

        // 2. Calculate number of reminders
        val numCups = Math.ceil(goal.toDouble() / cupSize).toInt()
        if (numCups <= 0) {
            Log.w("NotificationReminder", "Invalid number of cups: $numCups")
            return
        }
        Log.d("NotificationReminder", "Will schedule $numCups reminders")

        // 3. Parse wake and bed time
        fun parseTime(str: String): Pair<Int, Int> {
            val parts = str.split(":")
            return if (parts.size == 2) {
                val hour = parts[0].toIntOrNull() ?: 7
                val minute = parts[1].toIntOrNull() ?: 0
                Log.d("NotificationReminder", "Parsed time $str to $hour:$minute")
                Pair(hour, minute)
            } else {
                Log.w("NotificationReminder", "Invalid time format: $str, using default")
                Pair(7, 0)
            }
        }
        val (wakeHour, wakeMinute) = parseTime(wakeupTime)
        val (bedHour, bedMinute) = parseTime(bedtime)

        // 4. Calculate interval in millis
        val calWake = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, wakeHour)
            set(Calendar.MINUTE, wakeMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val calBed = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, bedHour)
            set(Calendar.MINUTE, bedMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // If bedtime is before wakeup, assume next day
        if (calBed.before(calWake)) calBed.add(Calendar.DATE, 1)
        val totalMillis = calBed.timeInMillis - calWake.timeInMillis
        val intervalMillis = totalMillis / numCups

        Log.d("NotificationReminder", "Time interval between reminders: ${intervalMillis/1000/60} minutes")

        // 5. Remove previous auto-scheduled reminders
        times.forEach { reminder ->
            Log.d("NotificationReminder", "Cancelling previous alarm: ${reminder.time}")
            cancelAlarm(reminder.id)
        }
        times.clear()
        saveData()

        // 6. Schedule reminders
        for (i in 0 until numCups) {
            val reminderTime = Calendar.getInstance().apply {
                timeInMillis = calWake.timeInMillis + i * intervalMillis
            }
            val hour = reminderTime.get(Calendar.HOUR_OF_DAY)
            val minute = reminderTime.get(Calendar.MINUTE)
            val id = (hour * 100 + minute) + i * 10000 + Random().nextInt(1000) // unique-ish
            
            Log.d("NotificationReminder", "Scheduling reminder #$i for $hour:$minute with ID $id")
            scheduleAlarm(hour, minute, id)
            
            val timeStr = String.format("%02d:%02d", hour, minute)
            times.add(ReminderHolder(id, timeStr))
        }
        
        reminderAdapter.notifyDataSetChanged()
        saveData()
        
        // Show confirmation to user
        Toast.makeText(this, "Scheduled ${numCups} water reminders", Toast.LENGTH_SHORT).show()
        
        // Verify notification channel
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(MainActivity.CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(true)
                enableVibration(true)
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d("NotificationReminder", "Created/Updated notification channel")
        }
    }
} 