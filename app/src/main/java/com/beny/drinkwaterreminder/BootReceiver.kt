package com.beny.drinkwaterreminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device booted, restoring water reminders")
            
            // Restore scheduled reminders
            val scheduler = WaterReminderScheduler(context)
            scheduler.restoreReminders()
        }
    }
} 