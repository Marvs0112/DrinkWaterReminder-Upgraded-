package com.beny.drinkwaterreminder

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class PermissionManager(private val activity: AppCompatActivity) {
    
    companion object {
        private const val TAG = "PermissionManager"
        private const val PERMISSION_REQUEST_CODE = 123
    }
    
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var settingsLauncher: ActivityResultLauncher<Intent>
    private var onPermissionsGrantedCallback: (() -> Unit)? = null
    
    init {
        setupPermissionLaunchers()
    }
    
    private fun setupPermissionLaunchers() {
        requestPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                checkAndRequestNextPermission()
            } else {
                showPermissionExplanationDialog()
            }
        }
        
        settingsLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                checkAndRequestNextPermission()
            } else {
                showPermissionExplanationDialog()
            }
        }
    }
    
    fun requestAllPermissions(onPermissionsGranted: () -> Unit) {
        onPermissionsGrantedCallback = onPermissionsGranted
        checkAndRequestNextPermission()
    }
    
    private fun checkAndRequestNextPermission() {
        when {
            !isNotificationPermissionGranted() -> {
                requestNotificationPermission()
            }
            !isExactAlarmPermissionGranted() -> {
                requestExactAlarmPermission()
            }
            !isBatteryOptimizationExempted() -> {
                requestBatteryOptimizationExemption()
            }
            !isAutoStartEnabled() -> {
                requestAutoStartPermission()
            }
            else -> {
                Log.d(TAG, "All permissions granted")
                onPermissionsGrantedCallback?.invoke()
            }
        }
    }
    
    private fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(activity).areNotificationsEnabled()
        }
    }
    
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                showNotificationPermissionRationale()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
            settingsLauncher.launch(intent)
        }
    }
    
    private fun isExactAlarmPermissionGranted(): Boolean {
        val alarmManager = activity.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }
    
    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.parse("package:${activity.packageName}"))
            settingsLauncher.launch(intent)
        } else {
            checkAndRequestNextPermission()
        }
    }
    
    private fun isBatteryOptimizationExempted(): Boolean {
        val powerManager = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(activity.packageName)
    }
    
    private fun requestBatteryOptimizationExemption() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${activity.packageName}")
        }
        settingsLauncher.launch(intent)
    }
    
    private fun isAutoStartEnabled(): Boolean {
        // This is manufacturer specific and can't be checked programmatically
        // We'll assume it's not enabled by default
        return getSharedPreferences().getBoolean("autostart_enabled", false)
    }
    
    private fun requestAutoStartPermission() {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val intent = when {
            manufacturer.contains("xiaomi") -> Intent().apply {
                component = android.content.ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            }
            manufacturer.contains("infinix") || manufacturer.contains("tecno") -> Intent().apply {
                component = android.content.ComponentName(
                    "com.transsion.phonemanager",
                    "com.itel.autobootmanager.activity.AutoBootMgrActivity"
                )
            }
            else -> null
        }
        
        if (intent != null && intent.resolveActivity(activity.packageManager) != null) {
            showAutoStartDialog(intent)
        } else {
            // Mark as enabled if device doesn't need special handling
            getSharedPreferences().edit().putBoolean("autostart_enabled", true).apply()
            checkAndRequestNextPermission()
        }
    }
    
    private fun showAutoStartDialog(intent: Intent) {
        AlertDialog.Builder(activity)
            .setTitle("Enable Auto-Start")
            .setMessage("This app needs to be allowed to auto-start to ensure reliable water reminders. Please enable auto-start in the next screen.")
            .setPositiveButton("Enable") { _, _ ->
                try {
                    activity.startActivity(intent)
                    getSharedPreferences().edit().putBoolean("autostart_enabled", true).apply()
                } catch (e: Exception) {
                    Log.e(TAG, "Error opening auto-start settings: ${e.message}")
                    // Mark as enabled if we can't open settings
                    getSharedPreferences().edit().putBoolean("autostart_enabled", true).apply()
                }
                checkAndRequestNextPermission()
            }
            .setNegativeButton("Skip") { _, _ ->
                getSharedPreferences().edit().putBoolean("autostart_enabled", true).apply()
                checkAndRequestNextPermission()
            }
            .show()
    }
    
    private fun showNotificationPermissionRationale() {
        AlertDialog.Builder(activity)
            .setTitle("Notification Permission Required")
            .setMessage("This app needs notification permission to remind you to drink water regularly. Please grant this permission for the app to work properly.")
            .setPositiveButton("Grant") { _, _ ->
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            .setNegativeButton("Cancel") { _, _ ->
                showPermissionExplanationDialog()
            }
            .show()
    }
    
    private fun showPermissionExplanationDialog() {
        AlertDialog.Builder(activity)
            .setTitle("Permissions Required")
            .setMessage("This app requires certain permissions to function properly. Without these permissions, water reminders may not work reliably. Would you like to grant the permissions?")
            .setPositiveButton("Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Cancel") { _, _ ->
                activity.finish()
            }
            .show()
    }
    
    private fun openAppSettings() {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
            activity.startActivity(this)
        }
    }
    
    private fun getSharedPreferences() = 
        activity.getSharedPreferences("permissions", Context.MODE_PRIVATE)
    
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            when (resultCode) {
                Activity.RESULT_OK -> checkAndRequestNextPermission()
                else -> showPermissionExplanationDialog()
            }
        }
    }
} 