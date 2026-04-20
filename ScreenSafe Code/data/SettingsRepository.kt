package com.example.screensafe.data

import android.content.Context
import com.example.screensafe.model.AppSettings
import com.example.screensafe.model.ReminderFrequency

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("screensafe_prefs", Context.MODE_PRIVATE)

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

    fun reset() {
        prefs.edit().clear().apply()
    }
}
