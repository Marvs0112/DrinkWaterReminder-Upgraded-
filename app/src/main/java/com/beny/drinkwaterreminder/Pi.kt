package com.beny.drinkwaterreminder

import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.beny.drinkwaterreminder.databinding.ActivityPiBinding
import java.util.*

class Pi : AppCompatActivity() {
    private lateinit var binding: ActivityPiBinding
    private var selectedWakeupTime: String = "00:00"
    private var selectedBedTime: String = "00:00"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPiBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        loadSavedData()
        setupClickListeners()
        setupLiveGoalCalculation()
    }
    
    private fun loadSavedData() {
        getSharedPreferences("first", Context.MODE_PRIVATE).apply {
            binding.weighttxt.setText(getString("weight", ""))
            binding.workoutid.setText(getString("workout", ""))
            selectedWakeupTime = getString("wakeupTime", "00:00") ?: "00:00"
            selectedBedTime = getString("bedtime", "00:00") ?: "00:00"
            
            updateTimeDisplays()
        }
    }
    
    private fun setupClickListeners() {
        binding.apply {
            selectWakeup.setOnClickListener { showTimePickerDialog(true) }
            selectBedtime.setOnClickListener { showTimePickerDialog(false) }
            
            startbtn.setOnClickListener {
                if (validateInputs()) {
                    saveData()
                    finish()
                }
            }
        }
    }
    
    private fun setupLiveGoalCalculation() {
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = updateCalculatedGoal()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        binding.weighttxt.addTextChangedListener(watcher)
        binding.workoutid.addTextChangedListener(watcher)
        // Also update when time is picked
        binding.selectWakeup.setOnClickListener {
            showTimePickerDialog(true)
            // updateCalculatedGoal() will be called after time is picked
        }
        binding.selectBedtime.setOnClickListener {
            showTimePickerDialog(false)
            // updateCalculatedGoal() will be called after time is picked
        }
    }
    
    private fun updateCalculatedGoal() {
        val weight = binding.weighttxt.text.toString().toDoubleOrNull() ?: 0.0
        val workout = binding.workoutid.text.toString().toIntOrNull() ?: 1
        val wakeup = binding.wakeUptxt.text.toString()
        val bed = binding.bedTimetxt.text.toString()
        if (weight > 0 && wakeup.isNotBlank() && bed.isNotBlank()) {
            val pi = PersonalInformation(workout, wakeup, bed, weight)
            val goal = pi.GoalCalculator().toInt()
            binding.calculatedGoal.text = "$goal ml"
        } else {
            binding.calculatedGoal.text = "—"
        }
    }
    
    private fun showTimePickerDialog(isWakeup: Boolean) {
        val calendar = Calendar.getInstance()
        val is24HourFormat = DateFormat.is24HourFormat(this)
        
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                val time = String.format("%02d:%02d", hourOfDay, minute)
                if (isWakeup) {
                    selectedWakeupTime = time
                    binding.wakeUptxt.text = time
                } else {
                    selectedBedTime = time
                    binding.bedTimetxt.text = time
                }
                updateCalculatedGoal()
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            is24HourFormat
        ).show()
    }
    
    private fun validateInputs(): Boolean {
        binding.apply {
            val weight = weighttxt.text.toString()
            val workout = workoutid.text.toString()
            
            when {
                weight.isEmpty() -> {
                    Toast.makeText(this@Pi, "Please enter your weight", Toast.LENGTH_SHORT).show()
                    return false
                }
                workout.isEmpty() -> {
                    Toast.makeText(this@Pi, "Please enter workout intensity", Toast.LENGTH_SHORT).show()
                    return false
                }
                selectedWakeupTime == "00:00" -> {
                    Toast.makeText(this@Pi, "Please select wake up time", Toast.LENGTH_SHORT).show()
                    return false
                }
                selectedBedTime == "00:00" -> {
                    Toast.makeText(this@Pi, "Please select bed time", Toast.LENGTH_SHORT).show()
                    return false
                }
            }
            return true
        }
    }
    
    private fun saveData() {
        binding.apply {
            val weight = weighttxt.text.toString()
            val workout = workoutid.text.toString()
            
            // Save personal information
            getSharedPreferences("first", Context.MODE_PRIVATE)
                .edit()
                .putString("weight", weight)
                .putString("workout", workout)
                .putString("wakeupTime", selectedWakeupTime)
                .putString("bedtime", selectedBedTime)
                .apply()
            
            // Calculate new goal
            val pi = PersonalInformation(
                workout.toInt(),
                selectedWakeupTime,
                selectedBedTime,
                weight.toDouble()
            )
            val newGoal = pi.GoalCalculator().toInt().toString()
            
            // Save goal and personal information state
            getSharedPreferences("progress", Context.MODE_PRIVATE)
                .edit()
                .putString("goalState", newGoal)
                .putBoolean("personal_Information", true)
                .apply()
            
            // --- Sync with DailyGoalDialog ---
            getSharedPreferences("dailyGoal", Context.MODE_PRIVATE)
                .edit()
                .putInt("goal", newGoal.toInt())
                .apply()
            
            // Show confirmation
            Toast.makeText(this@Pi, "Daily goal set to ${newGoal}ml", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun updateTimeDisplays() {
        binding.apply {
            wakeUptxt.text = selectedWakeupTime
            bedTimetxt.text = selectedBedTime
        }
    }
} 