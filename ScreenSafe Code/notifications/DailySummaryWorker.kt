package com.example.screensafe.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DailySummaryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        NotificationHelper.showDailySummary(applicationContext, "Check your daily screen time summary in ScreenSafe.")
        return Result.success()
    }
}
