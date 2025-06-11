package com.group7.drinkwaterreminder

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.group7.drinkwaterreminder.databinding.ActivityDrinkHistoryBinding
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log
import android.content.Intent

class DrinkHistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDrinkHistoryBinding
    private lateinit var adapter: DrinkHistoryAdapter
    private var dehydrationDialogShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDrinkHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        loadDrinkHistory()
        setupToolbar()
        setupRecyclerView()
        updateTotals()
        updateEmptyState()
    }

    private fun loadDrinkHistory() {
        val sharedPreferences = getSharedPreferences("drinked", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = sharedPreferences.getString("drinked_list", null)
        if (json != null) {
            val type = object : TypeToken<ArrayList<DrinkedList>>() {}.type
            DrinkedList.Drinked.clear()
            DrinkedList.Drinked.addAll(gson.fromJson(json, type))
        }
        Log.d("DrinkHistory", "Loaded drink history size: ${DrinkedList.Drinked.size}")
    }

    private fun setupToolbar() {
        binding.toolbar.apply {
            title = "Drink History"
            setNavigationOnClickListener {
                finish()
            }
            setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel)
        }
    }

    private fun setupRecyclerView() {
        adapter = DrinkHistoryAdapter(DrinkedList.Drinked)
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@DrinkHistoryActivity)
            addItemDecoration(
                MaterialDividerItemDecoration(
                    this@DrinkHistoryActivity,
                    MaterialDividerItemDecoration.VERTICAL
                )
            )
            adapter = this@DrinkHistoryActivity.adapter
        }
    }

    private fun updateTotals() {
        val sdf = SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.US)
        val now = Calendar.getInstance()
        val today = now.clone() as Calendar
        val week = now.clone() as Calendar
        week.firstDayOfWeek = Calendar.MONDAY
        week.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        week.set(Calendar.HOUR_OF_DAY, 0)
        week.set(Calendar.MINUTE, 0)
        week.set(Calendar.SECOND, 0)
        week.set(Calendar.MILLISECOND, 0)
        val month = now.clone() as Calendar
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)
        month.set(Calendar.DAY_OF_MONTH, 1)
        month.set(Calendar.HOUR_OF_DAY, 0)
        month.set(Calendar.MINUTE, 0)
        month.set(Calendar.SECOND, 0)
        month.set(Calendar.MILLISECOND, 0)

        var dailyTotal = 0
        var weeklyTotal = 0
        var monthlyTotal = 0
        var allTimeTotal = 0

        for (drink in DrinkedList.Drinked) {
            try {
                val date = sdf.parse(drink.time)
                if (date != null) {
                    val cal = Calendar.getInstance().apply { time = date }
                    if (cal.after(today)) dailyTotal += drink.amount
                    if (cal.after(week)) weeklyTotal += drink.amount
                    if (cal.after(month)) monthlyTotal += drink.amount
                    allTimeTotal += drink.amount
                }
            } catch (_: Exception) {
                allTimeTotal += drink.amount // fallback: always add to all time
            }
        }

        binding.tvDailyTotal.text = "$dailyTotal ml"
        binding.tvWeeklyTotal.text = "$weeklyTotal ml"
        binding.tvMonthlyTotal.text = "$monthlyTotal ml"
        binding.tvAllTimeTotal.text = "$allTimeTotal ml"

        // Show dehydration dialog if daily total is less than 50% of goal
        if (!dehydrationDialogShown) {
            val goal = getSharedPreferences("dailyGoal", Context.MODE_PRIVATE).getInt("goal", 0)
            if (goal > 0 && dailyTotal < goal / 2) {
                dehydrationDialogShown = true
                showDehydrationDialog()
            }
        }
    }

    private fun showDehydrationDialog() {
        val message = "Your water intake today is low.\n\n" +
            "Symptoms of dehydration include:\n" +
            "• Fatigue\n" +
            "• Confusion or short-term memory loss\n" +
            "• Mood changes like increased irritability or depression\n\n" +
            "Risks of dehydration include:\n" +
            "• Urinary tract infections\n" +
            "• Kidney stones\n" +
            "• Gallstones\n" +
            "• Constipation"
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Dehydration Warning")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun updateEmptyState() {
        binding.emptyHistoryText.visibility = if (DrinkedList.Drinked.isEmpty()) View.VISIBLE else View.GONE
    }

    fun refreshList() {
        adapter.notifyDataSetChanged()
        updateTotals()
        updateEmptyState()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        if (item.itemId == R.id.action_export_history) {
            exportHistory()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun saveDrinkHistory() {
        val sharedPreferences = getSharedPreferences("drinked", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val gson = Gson()
        val json = gson.toJson(DrinkedList.Drinked)
        editor.putString("drinked_list", json)
        editor.apply()
    }

    fun transfer_ml(amount: Int) {
        val progressState = getSharedPreferences("progress", Context.MODE_PRIVATE)
        val progress = progressState.getString("progressState", "0")?.toInt() ?: 0
        val newProgress = (progress - amount).coerceAtLeast(0)
        
        progressState.edit().apply {
            putString("progressState", newProgress.toString())
            apply()
        }
    }

    private fun exportHistory() {
        if (DrinkedList.Drinked.isEmpty()) {
            Snackbar.make(binding.root, "No history to export.", Snackbar.LENGTH_SHORT).show()
            return
        }
        // Calculate totals for export
        val sdf = SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.US)
        val now = Calendar.getInstance()
        val today = now.clone() as Calendar
        val week = now.clone() as Calendar
        week.firstDayOfWeek = Calendar.MONDAY
        week.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        week.set(Calendar.HOUR_OF_DAY, 0)
        week.set(Calendar.MINUTE, 0)
        week.set(Calendar.SECOND, 0)
        week.set(Calendar.MILLISECOND, 0)
        val month = now.clone() as Calendar
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)
        month.set(Calendar.DAY_OF_MONTH, 1)
        month.set(Calendar.HOUR_OF_DAY, 0)
        month.set(Calendar.MINUTE, 0)
        month.set(Calendar.SECOND, 0)
        month.set(Calendar.MILLISECOND, 0)

        var dailyTotal = 0
        var weeklyTotal = 0
        var monthlyTotal = 0
        var allTimeTotal = 0

        val sb = StringBuilder()
        sb.append("Drink Water History Report\n")
        sb.append("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}\n\n")
        for (drink in DrinkedList.Drinked) {
            try {
                val date = sdf.parse(drink.time)
                if (date != null) {
                    val cal = Calendar.getInstance().apply { time = date }
                    if (cal.after(today)) dailyTotal += drink.amount
                    if (cal.after(week)) weeklyTotal += drink.amount
                    if (cal.after(month)) monthlyTotal += drink.amount
                    allTimeTotal += drink.amount
                }
            } catch (_: Exception) {
                allTimeTotal += drink.amount
            }
        }
        sb.append("All Time Total: $allTimeTotal ml\n")
        sb.append("This Month: $monthlyTotal ml\n")
        sb.append("This Week: $weeklyTotal ml\n")
        sb.append("Today: $dailyTotal ml\n\n")
        sb.append("Date,Amount (ml)\n")
        for (drink in DrinkedList.Drinked) {
            sb.append("\"${drink.time}\",${drink.amount}\n")
        }
        sb.append("\nGenerated by Drink Water Reminder App\n")
        val exportText = sb.toString()

        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/csv"
        intent.putExtra(Intent.EXTRA_SUBJECT, "My Drink Water History")
        intent.putExtra(Intent.EXTRA_TEXT, exportText)
        startActivity(Intent.createChooser(intent, "Export History"))
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
} 