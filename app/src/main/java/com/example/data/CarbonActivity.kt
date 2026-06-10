package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "carbon_activities")
data class CarbonActivity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val category: String, // Transport, Diet, Energy, Consumption
    val subCategory: String, // e.g. "Petrol Car", "Vegan Meal"
    val value: Double, // Quantity
    val unit: String, // e.g. "miles", "meals", "kWh"
    val co2Emitted: Double, // in kg of CO2e (negative for offsets/savings)
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val userEmail: String = "" // Tethers tracking record to a specific user
)
