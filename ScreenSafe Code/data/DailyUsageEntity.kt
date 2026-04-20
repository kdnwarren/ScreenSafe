package com.example.screensafe.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_usage")
data class DailyUsageEntity(
    @PrimaryKey val dateKey: String,
    val usageMinutes: Long
)
