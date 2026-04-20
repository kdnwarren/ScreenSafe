package com.example.screensafe.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.screensafe.R

object NotificationHelper {

    const val CHANNEL_ALERTS = "screensafe_alerts"
    const val CHANNEL_SUMMARY = "screensafe_summary"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val alerts = NotificationChannel(
                CHANNEL_ALERTS,
                "Screen Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )

            val summary = NotificationChannel(
                CHANNEL_SUMMARY,
                "Daily Summary",
                NotificationManager.IMPORTANCE_DEFAULT
            )

            manager.createNotificationChannel(alerts)
            manager.createNotificationChannel(summary)
        }
    }

    fun showLimitReached(context: Context) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("ScreenSafe Warning")
            .setContentText("You have reached your daily screen time limit.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(1001, notification)
    }

    fun showDailySummary(context: Context, summaryText: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_SUMMARY)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("ScreenSafe Summary")
            .setContentText(summaryText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(1002, notification)
    }
}
