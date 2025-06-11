package com.beny.drinkwaterreminder

class PersonalInformation(
    private val workout: Int,
    private val wakeupTime: String,
    private val bedTime: String,
    private val weight: Double
) {
    fun GoalCalculator(): Double {
        // Base calculation: weight * 0.033 gives liters, * 1000 converts to ml
        val baseAmount = weight * 0.033 * 1000
        
        // Add extra water based on workout intensity
        val workoutExtra = when (workout) {
            1 -> 0.0     // No workout
            2 -> 350.0   // Light workout
            3 -> 500.0   // Moderate workout
            4 -> 1000.0  // Intense workout
            else -> 0.0
        }
        
        return baseAmount + workoutExtra
    }
    
    fun getWorkout(): Int = workout
    
    fun getWakeupTime(): String = wakeupTime
    
    fun getBedTime(): String = bedTime
    
    fun getWeight(): Double = weight

    companion object {
        // Map category index to guideline ml (same order as spinner in DailyGoalDialog)
        val guidelineMl = listOf(
            946,   // 1-3 years
            1183,  // 4-8 years
            1657,  // 9-13 years
            1893,  // 14-18 years
            3073,  // Men, 19+
            2130,  // Women, 19+
            2366,  // Pregnant women
            3073   // Breastfeeding women
        )
    }

    fun personalizedGoal(categoryIndex: Int): Double {
        val base = (guidelineMl.getOrNull(categoryIndex) ?: 2000).toDouble() // fallback to 2000ml
        val extra = GoalCalculator() // This is the total from weight/workout
        // Only add extra if weight/workout is provided (e.g., weight > 0 or workout > 1)
        return if (weight > 0 || workout > 1) base + (extra - 2000.0) else base
    }
} 