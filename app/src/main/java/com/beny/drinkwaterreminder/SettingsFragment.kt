package com.beny.drinkwaterreminder

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.beny.drinkwaterreminder.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    
    private val settingList = listOf(
        "Notification Manager",
        "Cups Manager",
        "Personal information",
        "Daily goal",
        "Contact us",
        "Share"
    )

    companion object {
        private const val TAG = "SettingsFragment"
        
        @JvmStatic
        fun newInstance() = SettingsFragment()
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView")
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated")
        setupListView()
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        // Only refresh if needed (e.g., after returning from a settings activity)
        if (binding.ListView.adapter == null) {
            setupListView()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView")
        _binding = null
    }
    
    private fun setupListView() {
        Log.d(TAG, "Setting up ListView")
        val arrayAdapter = object : ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_list_item_1,
            settingList
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.textSize = 16f
                textView.setPadding(32, 32, 32, 32)
                return view
            }
        }
        
        binding.ListView.apply {
            adapter = arrayAdapter
            setOnItemClickListener { _, _, position, _ ->
                Log.d(TAG, "List item clicked: ${settingList[position]}")
                when (position) {
                    0 -> startActivity(Intent(context, NotificationReminder::class.java))
                    1 -> startActivity(Intent(context, CupsManager::class.java))
                    2 -> startActivity(Intent(context, Pi::class.java))
                    3 -> showDailyGoalDialog()
                    4 -> handleContactUs()
                    5 -> handleShare()
                }
            }
        }
    }
    
    private fun showDailyGoalDialog() {
        Log.d(TAG, "Showing daily goal dialog")
        val dialog = DailyGoalDialog()
        dialog.show(childFragmentManager, "DailyGoalDialog")
    }
    
    private fun handleContactUs() {
        Log.d(TAG, "Handling contact us")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf("support@drinkwaterreminder.com"))
            putExtra(Intent.EXTRA_SUBJECT, "Drink Water Reminder Support")
        }
        startActivity(Intent.createChooser(intent, "Send Email"))
    }
    
    private fun handleShare() {
        Log.d(TAG, "Handling share")
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Drink Water Reminder")
            putExtra(Intent.EXTRA_TEXT, "Stay hydrated with Drink Water Reminder!")
        }
        startActivity(Intent.createChooser(shareIntent, "Share via"))
    }
} 