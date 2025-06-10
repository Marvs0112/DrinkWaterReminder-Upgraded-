package com.beny.drinkwaterreminder

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import android.widget.TimePicker
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.beny.drinkwaterreminder.databinding.ActivityNotificationBinding
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
            pendingTimePickerCallback = null
        } else {
            showPermissionExplanationDialog()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        try {
            alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            
            // Setup UI first
            setupRecyclerView()
            setupFloatingActionButton()
            
            // Then load data
            loadData()
            
            // Add a back button to the action bar
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                title = "Water Reminders"
            }
            
            // Check permissions
            checkPermissions()
        } catch (e: Exception) {
            Toast.makeText(this, "Error initializing: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onResume() {
        super.onResume()
        try {
            loadData() // Refresh the data when returning to the screen
            reminderAdapter.notifyDataSetChanged()
        } catch (e: Exception) {
            Toast.makeText(this, "Error refreshing data: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            showAlarmPermissionDialog()
        }
    }
    
    private fun showPermissionExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Notification Permission Required")
            .setMessage("This app needs notification permission to remind you to drink water. Please grant the permission in Settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showAlarmPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Exact Alarm Permission Required")
            .setMessage("This app needs permission to schedule exact alarms for water reminders. Please grant the permission in Settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun setupRecyclerView() {
        try {
            reminderAdapter = ReminderAdapter(this)
            binding.ReminderRecycleView.apply {
                adapter = reminderAdapter
                layoutManager = LinearLayoutManager(this@NotificationReminder)
                addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error setting up list: ${e.message}", Toast.LENGTH_SHORT).show()
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
        try {
            val calendar = Calendar.getInstance()
            TimePickerDialog(
                this,
                { _: TimePicker, hourOfDay: Int, minute: Int ->
                    try {
                        val time = String.format("%02d:%02d", hourOfDay, minute)
                        val id = Random().nextInt()
                        
                        scheduleAlarm(hourOfDay, minute, id)
                        times.add(ReminderHolder(id, time))
                        times.sortBy { it.time }
                        reminderAdapter.notifyDataSetChanged()
                        saveData()
                        
                        Toast.makeText(this, "Water reminder alarm set for $time", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Error scheduling alarm: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                android.text.format.DateFormat.is24HourFormat(this)
            ).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error showing time picker: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun scheduleAlarm(hourOfDay: Int, minute: Int, id: Int) {
        try {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hourOfDay)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                
                if (before(Calendar.getInstance())) {
                    add(Calendar.DATE, 1)
                }
            }
            
            val intent = Intent(this, WaterReminderReceiver::class.java).apply {
                putExtra("REMINDER_ID", id)
                putExtra("REMINDER_HOUR", hourOfDay)
                putExtra("REMINDER_MINUTE", minute)
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
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
        } catch (e: Exception) {
            Toast.makeText(this, "Error scheduling alarm: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun cancelAlarm(id: Int) {
        try {
            val intent = Intent(this, WaterReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Error canceling alarm: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadData() {
        try {
            val sharedPreferences = getSharedPreferences("reminder", Context.MODE_PRIVATE)
            val gson = Gson()
            val json = sharedPreferences.getString("times", null)
            
            times.clear()
            
            if (json != null) {
                val type = object : TypeToken<ArrayList<ReminderHolder>>() {}.type
                times.addAll(gson.fromJson(json, type))
                times.sortBy { it.time }
                
                times.forEach { reminder ->
                    val timeParts = reminder.time.split(":")
                    if (timeParts.size == 2) {
                        val hour = timeParts[0].toInt()
                        val minute = timeParts[1].toInt()
                        scheduleAlarm(hour, minute, reminder.id)
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading data: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun saveData() {
        try {
            getSharedPreferences("reminder", Context.MODE_PRIVATE)
                .edit()
                .putString("times", Gson().toJson(times))
                .apply()
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving data: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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
} 