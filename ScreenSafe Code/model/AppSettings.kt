package com.example.safescreen.model

enum class ReminderFrequency {
    EVERY_HOUR,
    EVERY_2_HOURS,
    CUSTOM
}

data class AppSettings(
    val dailyLimitMinutes: Long = 300,
    val reminderFrequency: ReminderFrequency = ReminderFrequency.EVERY_HOUR,
    val notificationsEnabled: Boolean = true,
    val breakRemindersEnabled: Boolean = true,
    val dailySummaryEnabled: Boolean = false,
    val theme: String = "Light"
)
