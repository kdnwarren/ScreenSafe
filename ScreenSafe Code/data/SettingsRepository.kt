package com.example.safescreen.data

import android.content.Context
import com.example.safescreen.model.AppSettings
import com.example.safescreen.model.ReminderFrequency
import com.example.safescreen.model.SummaryData
import java.util.Calendar

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("safescreen_prefs", Context.MODE_PRIVATE)

    fun loadSettings(): AppSettings {
        return AppSettings(
            dailyLimitMinutes = prefs.getLong("daily_limit_minutes", 300L),
            reminderFrequency = ReminderFrequency.valueOf(
                prefs.getString("reminder_frequency", ReminderFrequency.EVERY_HOUR.name)
                    ?: ReminderFrequency.EVERY_HOUR.name
            ),
            notificationsEnabled = prefs.getBoolean("notifications_enabled", true),
            breakRemindersEnabled = prefs.getBoolean("break_reminders_enabled", true),
            dailySummaryEnabled = prefs.getBoolean("daily_summary_enabled", false),
            theme = prefs.getString("theme", "Light") ?: "Light"
        )
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit()
            .putLong("daily_limit_minutes", settings.dailyLimitMinutes)
            .putString("reminder_frequency", settings.reminderFrequency.name)
            .putBoolean("notifications_enabled", settings.notificationsEnabled)
            .putBoolean("break_reminders_enabled", settings.breakRemindersEnabled)
            .putBoolean("daily_summary_enabled", settings.dailySummaryEnabled)
            .putString("theme", settings.theme)
            .apply()
    }

    fun storeTodayUsage(todayMinutes: Long) {
        val dayIndex = getDayIndex()
        val previousDayIndex = (dayIndex + 6) % 7

        val savedDayMarker = prefs.getInt("saved_day_marker", -1)
        if (savedDayMarker != dayIndex) {
            // New day rollover. Shift previous today's value into yesterday.
            val oldToday = prefs.getLong("today_minutes", 0L)
            prefs.edit()
                .putLong("yesterday_minutes", oldToday)
                .putInt("saved_day_marker", dayIndex)
                .apply()
        }

        prefs.edit()
            .putLong("today_minutes", todayMinutes)
            .putLong("week_day_$dayIndex", todayMinutes)
            .apply()
    }

    fun loadSummaryData(): SummaryData {
        val today = prefs.getLong("today_minutes", 0L)
        val yesterday = prefs.getLong("yesterday_minutes", 0L)
        val weekly = (0..6).map { prefs.getLong("week_day_$it", 0L) }
        return SummaryData(
            todayMinutes = today,
            yesterdayMinutes = yesterday,
            thisWeekMinutes = weekly.sum(),
            weeklyMinutes = weekly
        )
    }

    fun resetAllData() {
        prefs.edit().clear().apply()
    }

    private fun getDayIndex(): Int {
        val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return when (day) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }
    }
}
