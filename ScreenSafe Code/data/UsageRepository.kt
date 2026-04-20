package com.example.screensafe.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.example.screensafe.model.SummaryData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class UsageRepository(
    private val context: Context,
    private val database: AppDatabase
) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun hasUsagePermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            "android:get_usage_stats",
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun getTodayScreenTimeMinutes(): Long {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        val totalForegroundMillis = stats.sumOf { it.totalTimeInForeground }
        return totalForegroundMillis / 1000 / 60
    }

    suspend fun saveTodayUsage(todayMinutes: Long) = withContext(Dispatchers.IO) {
        val todayKey = dateFormat.format(Calendar.getInstance().time)
        database.dailyUsageDao().upsert(DailyUsageEntity(todayKey, todayMinutes))
    }

    suspend fun loadSummaryData(): SummaryData = withContext(Dispatchers.IO) {
        val today = Calendar.getInstance()
        val todayKey = dateFormat.format(today.time)

        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yesterdayKey = dateFormat.format(yesterday.time)

        val todayMinutes = database.dailyUsageDao().getByDate(todayKey)?.usageMinutes ?: 0L
        val yesterdayMinutes = database.dailyUsageDao().getByDate(yesterdayKey)?.usageMinutes ?: 0L

        val weekly = buildList {
            val start = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }
            repeat(7) {
                val key = dateFormat.format(start.time)
                add(database.dailyUsageDao().getByDate(key)?.usageMinutes ?: 0L)
                start.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        SummaryData(
            todayMinutes = todayMinutes,
            yesterdayMinutes = yesterdayMinutes,
            thisWeekMinutes = weekly.sum(),
            weeklyMinutes = weekly
        )
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        database.dailyUsageDao().clearAll()
    }
}
