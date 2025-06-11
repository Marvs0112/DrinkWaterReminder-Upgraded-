package com.group7.drinkwaterreminder

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.group7.drinkwaterreminder.databinding.DialogDailyGoalBinding

class DailyGoalDialog : DialogFragment() {
    private var _binding: DialogDailyGoalBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogDailyGoalBinding.inflate(layoutInflater)
        
        // Category list and suggested ml values
        val categories = listOf(
            "1-3 years (946 ml)" to 946,
            "4-8 years (1183 ml)" to 1183,
            "9-13 years (1657-1893 ml)" to 1657,
            "14-18 years (1893-2602 ml)" to 1893,
            "Men, 19+ (3073 ml)" to 3073,
            "Women, 19+ (2130 ml)" to 2130,
            "Pregnant women (2366 ml)" to 2366,
            "Breastfeeding women (3073 ml)" to 3073
        )
        val categoryNames = categories.map { it.first }
        val categoryValues = categories.map { it.second }

        // Set up spinner
        val spinner = binding.spinnerCategory
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categoryNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        // Load current goal
        val currentGoal = context?.getSharedPreferences("dailyGoal", Context.MODE_PRIVATE)
            ?.getInt("goal", 0) ?: 0
        binding.goalInput.setText(if (currentGoal > 0) currentGoal.toString() else "")
        
        // Spinner selection listener
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                // Try to get personal info from SharedPreferences
                val sp = context?.getSharedPreferences("first", Context.MODE_PRIVATE)
                val weight = sp?.getString("weight", "0")?.toDoubleOrNull() ?: 0.0
                val workout = sp?.getString("workout", "1")?.toIntOrNull() ?: 1
                val wakeup = sp?.getString("wakeupTime", "07:00") ?: "07:00"
                val bed = sp?.getString("bedtime", "22:00") ?: "22:00"
                val pi = PersonalInformation(workout, wakeup, bed, weight)
                val personalized = pi.personalizedGoal(position).toInt()
                binding.goalInput.setText(personalized.toString())
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Reference link
        binding.tvReferenceLink.setOnClickListener {
            val url = "https://nutritionsource.hsph.harvard.edu/water/"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }

        return AlertDialog.Builder(requireContext())
            .setTitle("Set Daily Goal")
            .setView(binding.root)
            .setPositiveButton("Save") { _, _ ->
                handleSave()
            }
            .setNegativeButton("Cancel", null)
            .create()
    }

    private fun handleSave() {
        val goalInput = binding.goalInput.text.toString()
        if (goalInput.isNotEmpty()) {
            try {
                val goal = goalInput.toInt()
                if (goal > 0) {
                    saveGoal(goal)
                    Toast.makeText(context, "Daily goal updated successfully", Toast.LENGTH_SHORT).show()
                    
                    // Auto-schedule reminders
                    (activity as? NotificationReminder)?.autoScheduleReminders()
                    
                    // Refresh the home fragment
                    (activity as? MainActivity)?.let { activity ->
                        activity.supportFragmentManager.fragments.forEach { fragment ->
                            if (fragment is HomeFragment) {
                                fragment.onResume()
                            }
                        }
                    }
                } else {
                    Toast.makeText(context, "Please enter a valid goal greater than 0", Toast.LENGTH_SHORT).show()
                }
            } catch (e: NumberFormatException) {
                Toast.makeText(context, "Please enter a valid number", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Please enter a goal", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveGoal(goal: Int) {
        context?.let { ctx ->
            // Save in dailyGoal preferences
            ctx.getSharedPreferences("dailyGoal", Context.MODE_PRIVATE)?.edit()?.apply {
                putInt("goal", goal)
                apply()
            }
            
            // Also save in progress preferences for backward compatibility
            ctx.getSharedPreferences("progress", Context.MODE_PRIVATE)?.edit()?.apply {
                putString("goalState", goal.toString())
                apply()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 