package com.group7.drinkwaterreminder

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.group7.drinkwaterreminder.databinding.ActivityTipsBinding

class Tips : AppCompatActivity() {
    private lateinit var binding: ActivityTipsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTipsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupRecyclerView()
    }
    
    private fun setupRecyclerView() {
        binding.tipsRecyclerView.apply {
            adapter = TipsAdapter(TipsData.tips)
            layoutManager = LinearLayoutManager(this@Tips)
        }
    }
    
    companion object TipsData {
        val tips = listOf(
            "Drink water first thing in the morning",
            "Keep a reusable water bottle with you",
            "Set reminders to drink water throughout the day",
            "Drink water before, during, and after exercise",
            "Have a glass of water before each meal",
            "Track your daily water intake",
            "Dehydration: Symptoms & Risks\n\nSymptoms of dehydration:\n• Fatigue\n• Confusion or short-term memory loss\n• Mood changes like increased irritability or depression\n\nRisks of dehydration:\n• Urinary tract infections\n• Kidney stones\n• Gallstones\n• Constipation"
        )
    }
} 