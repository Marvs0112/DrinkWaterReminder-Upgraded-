package com.group7.drinkwaterreminder

data class Cups(
    val size: Int,
    val image: Int
) {
    override fun toString(): String = "$size ml"
} 