package com.beny.drinkwaterreminder

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import android.app.TimePickerDialog
import android.util.Log

class ReminderAdapter(private val context: Context) : 
    RecyclerView.Adapter<ReminderAdapter.ReminderViewHolder>() {
    
    companion object {
        private const val TAG = "ReminderAdapter"
    }
    
    class ReminderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtTime: TextView = view.findViewById(R.id.txtTime)
        val txtType: TextView = view.findViewById(R.id.txtType)
        val delBtn: Button = view.findViewById(R.id.delBtn)
        val editBtn: Button = view.findViewById(R.id.editBtn)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        return try {
            val view = LayoutInflater.from(context).inflate(R.layout.reminder_row, parent, false)
            ReminderViewHolder(view)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating ViewHolder: ${e.message}")
            throw e
        }
    }
    
    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        try {
            if (position >= NotificationReminder.times.size) {
                Log.e(TAG, "Invalid position: $position, size: ${NotificationReminder.times.size}")
                return
            }
            
            val reminder = NotificationReminder.times[position]
            
            // Format the time nicely
            holder.txtTime.text = reminder.time
            
            // Show if this is an automatic or manual reminder
            val cupSize = context.getSharedPreferences("cupsize", Context.MODE_PRIVATE)
                .getString("cupsize", "250")?.toIntOrNull() ?: 250
            holder.txtType.text = "Drink ${cupSize}ml of water"
            
            holder.delBtn.setOnClickListener {
                try {
                    // Cancel the alarm
                    (context as? NotificationReminder)?.cancelAlarm(reminder.id)
                    
                    // Remove from list and update UI
                    if (position < NotificationReminder.times.size) {
                        NotificationReminder.times.removeAt(position)
                        notifyItemRemoved(position)
                        notifyDataSetChanged()
                        saveData()
                        
                        Toast.makeText(context, "Reminder deleted", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting reminder: ${e.message}")
                    Toast.makeText(context, "Error deleting reminder", Toast.LENGTH_SHORT).show()
                }
            }
            
            holder.editBtn.setOnClickListener {
                try {
                    // Show time picker to edit the reminder
                    val timeParts = reminder.time.split(":")
                    if (timeParts.size == 2) {
                        val hour = timeParts[0].toInt()
                        val minute = timeParts[1].toInt()
                        
                        TimePickerDialog(
                            context,
                            { _, newHour, newMinute ->
                                try {
                                    // Cancel old alarm
                                    (context as? NotificationReminder)?.cancelAlarm(reminder.id)
                                    
                                    // Schedule new alarm
                                    val newTime = String.format("%02d:%02d", newHour, newMinute)
                                    
                                    if (position < NotificationReminder.times.size) {
                                        NotificationReminder.times[position] = 
                                            NotificationReminder.ReminderHolder(reminder.id, newTime)
                                        
                                        (context as? NotificationReminder)?.scheduleAlarm(newHour, newMinute, reminder.id)
                                        
                                        // Sort and update UI
                                        NotificationReminder.times.sortBy { it.time }
                                        notifyDataSetChanged()
                                        saveData()
                                        
                                        Toast.makeText(context, 
                                            "Reminder updated to $newTime", 
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error updating reminder: ${e.message}")
                                    Toast.makeText(context, "Error updating reminder", Toast.LENGTH_SHORT).show()
                                }
                            },
                            hour,
                            minute,
                            android.text.format.DateFormat.is24HourFormat(context)
                        ).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error showing time picker: ${e.message}")
                    Toast.makeText(context, "Error editing reminder", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error binding ViewHolder: ${e.message}")
            Toast.makeText(context, "Error displaying reminder", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun getItemCount(): Int = NotificationReminder.times.size
    
    private fun saveData() {
        try {
            context.getSharedPreferences("reminder", Context.MODE_PRIVATE)
                .edit()
                .putString("times", Gson().toJson(NotificationReminder.times))
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving data: ${e.message}")
            Toast.makeText(context, "Error saving reminder", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun hourExtracter(time: String): Int {
        return try {
            time.split(":")[0].toInt()
        } catch (e: Exception) {
            0
        }
    }
    
    private fun minuteExtracter(time: String): Int {
        return try {
            time.split(":")[1].toInt()
        } catch (e: Exception) {
            0
        }
    }
} 