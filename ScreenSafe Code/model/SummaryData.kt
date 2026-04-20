package com.example.screensafe.model

data class SummaryData(
    val todayMinutes: Long,
    val yesterdayMinutes: Long,
    val thisWeekMinutes: Long,
    val weeklyMinutes: List<Long>
)
