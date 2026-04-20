package com.example.screensafe

import android.app.Application
import com.example.screensafe.notifications.NotificationHelper

class ScreenSafeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
    }
}
