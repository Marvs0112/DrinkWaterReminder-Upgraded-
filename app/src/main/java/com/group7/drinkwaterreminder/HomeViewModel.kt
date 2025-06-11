package com.group7.drinkwaterreminder

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class HomeViewModel : ViewModel() {
    private val _waterIntake = MutableLiveData<Int>(0)
    val waterIntake: LiveData<Int> = _waterIntake
    
    fun addWaterIntake() {
        _waterIntake.value = (_waterIntake.value ?: 0) + 1
    }
} 