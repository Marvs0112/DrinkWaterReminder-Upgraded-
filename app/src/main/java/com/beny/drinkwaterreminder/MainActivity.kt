package com.beny.drinkwaterreminder

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.beny.drinkwaterreminder.databinding.ActivityMainBinding
import com.google.android.material.tabs.TabLayoutMediator
import androidx.core.app.NotificationManagerCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.fragment.app.Fragment
import androidx.core.app.NotificationCompat
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.ComponentName
import android.app.AlarmManager
import android.app.KeyguardManager
import android.Manifest
import android.content.pm.PackageManager

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var permissionManager: PermissionManager
    
    companion object {
        private const val TAG = "MainActivity"
        const val PREF_TAG = "delCup"
        const val CHANNEL_ID = "water_reminder_channel"
        const val CHANNEL_NAME = "reminder"
        lateinit var context: Context
            private set
        private const val REQUEST_IGNORE_BATTERY_OPTIMIZATIONS = 1001
        private const val REQUEST_NOTIFICATION_PERMISSION = 123
        private const val REQUEST_EXACT_ALARM = 1003
        private const val WAKELOCK_TIMEOUT = 10*60*1000L // 10 minutes
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        context = applicationContext
        permissionManager = PermissionManager(this)
        
        // Request all permissions automatically
        permissionManager.requestAllPermissions {
            // This will be called when all permissions are granted
            onAllPermissionsGranted()
        }
    }
    
    private fun onAllPermissionsGranted() {
        // Initialize app components after permissions are granted
        checkFirstRun()
        NotificationUtils(this)
        handleIntent()
        createNotificationChannel()
        
        // Start the foreground service
        WaterReminderForegroundService.startService(this)
        
        // Check if launched from notification
        if (intent?.getBooleanExtra("FROM_NOTIFICATION", false) == true) {
            val reminderId = intent.getIntExtra("REMINDER_ID", -1)
            if (reminderId != -1) {
                NotificationManagerCompat.from(this).cancel(reminderId)
            }
        }
        
        // Setup navigation
        setupBottomNavigation()
        
        // Start with home fragment
        if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment.newInstance())
                .commit()
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        permissionManager.handleActivityResult(requestCode, resultCode, data)
    }
    
    private fun checkFirstRun() {
        val prefs = getSharedPreferences("FirstRun", Context.MODE_PRIVATE)
        if (prefs.getBoolean("firstrun", true)) {
            startActivity(Intent(this, Pi::class.java))
            prefs.edit().putBoolean("firstrun", false).apply()
        }
    }
    
    private fun handleIntent() {
        if (intent?.action == "OPEN_SETTINGS") {
            // Handle any specific settings navigation if needed
        }
    }
    
    private fun createNotificationChannel() {
        NotificationUtils(this)
    }
    
    private fun setupBottomNavigation() {
        // Set the default selected item
        binding.bottomNavigation.selectedItemId = R.id.navigation_home

        Log.d(TAG, "Setting up bottom navigation")
        
        // Start with home fragment
        if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment.newInstance())
                .commit()
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            Log.d(TAG, "Bottom navigation item selected: ${item.title}")
            
            // Get current fragment
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            
            // Only perform transaction if we're actually changing fragments
            when (item.itemId) {
                R.id.navigation_home -> {
                    if (currentFragment !is HomeFragment) {
                        Log.d(TAG, "Navigating to Home fragment")
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, HomeFragment.newInstance())
                            .commit()
                    }
                    true
                }
                R.id.navigation_settings -> {
                    if (currentFragment !is SettingsFragment) {
                        Log.d(TAG, "Navigating to Settings fragment")
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, SettingsFragment.newInstance())
                            .commit()
                    }
                    true
                }
                else -> false
            }
        }
    }
} 