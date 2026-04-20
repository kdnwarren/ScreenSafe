package com.example.screensafe

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.screensafe.data.AppDatabase
import com.example.screensafe.data.SettingsRepository
import com.example.screensafe.data.UsageRepository
import com.example.screensafe.model.AppSettings
import com.example.screensafe.model.ReminderFrequency
import com.example.screensafe.model.SummaryData
import com.example.screensafe.notifications.BreakReminderWorker
import com.example.screensafe.notifications.DailySummaryWorker
import com.example.screensafe.notifications.NotificationHelper
import com.example.screensafe.ui.ScreenSafeTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = AppDatabase.getInstance(this)
        val settingsRepository = SettingsRepository(this)
        val usageRepository = UsageRepository(this, db)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            ScreenSafeRoot(
                settingsRepository = settingsRepository,
                usageRepository = usageRepository,
                onOpenUsageAccess = {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                },
                onScheduleWorkers = { settings -> scheduleWorkers(settings) }
            )
        }
    }

    private fun scheduleWorkers(settings: AppSettings) {
        val workManager = WorkManager.getInstance(this)

        if (settings.breakRemindersEnabled) {
            val hours = when (settings.reminderFrequency) {
                ReminderFrequency.EVERY_HOUR -> 1L
                ReminderFrequency.EVERY_2_HOURS -> 2L
                ReminderFrequency.CUSTOM -> 3L
            }
            val breakReminder = PeriodicWorkRequestBuilder<BreakReminderWorker>(hours, TimeUnit.HOURS).build()
            workManager.enqueueUniquePeriodicWork(
                "break_reminder",
                ExistingPeriodicWorkPolicy.UPDATE,
                breakReminder
            )
        } else {
            workManager.cancelUniqueWork("break_reminder")
        }

        if (settings.dailySummaryEnabled) {
            val dailySummary = PeriodicWorkRequestBuilder<DailySummaryWorker>(24, TimeUnit.HOURS).build()
            workManager.enqueueUniquePeriodicWork(
                "daily_summary",
                ExistingPeriodicWorkPolicy.UPDATE,
                dailySummary
            )
        } else {
            workManager.cancelUniqueWork("daily_summary")
        }
    }
}

private enum class Screen {
    WELCOME,
    DASHBOARD,
    SET_LIMIT,
    WARNING,
    SUMMARY,
    DETAILS,
    SETTINGS
}

@Composable
fun ScreenSafeRoot(
    settingsRepository: SettingsRepository,
    usageRepository: UsageRepository,
    onOpenUsageAccess: () -> Unit,
    onScheduleWorkers: (AppSettings) -> Unit
) {
    var settings by remember { mutableStateOf(settingsRepository.loadSettings()) }
    var currentScreen by remember { mutableStateOf(Screen.WELCOME) }
    var todayUsageMinutes by remember { mutableLongStateOf(0L) }
    var hasUsagePermission by remember { mutableStateOf(usageRepository.hasUsagePermission()) }
    var summaryData by remember { mutableStateOf(SummaryData(0, 0, 0, listOf(0, 0, 0, 0, 0, 0, 0))) }
    var warningDismissedUntil by remember { mutableLongStateOf(0L) }

    ScreenSafeTheme(darkTheme = settings.theme == "Dark") {
        LaunchedEffect(settings) {
            onScheduleWorkers(settings)
        }

        LaunchedEffect(Unit, settings) {
            while (true) {
                hasUsagePermission = usageRepository.hasUsagePermission()
                if (hasUsagePermission) {
                    todayUsageMinutes = usageRepository.getTodayScreenTimeMinutes()
                    usageRepository.saveTodayUsage(todayUsageMinutes)
                    summaryData = usageRepository.loadSummaryData()

                    if (settings.notificationsEnabled &&
                        todayUsageMinutes >= settings.dailyLimitMinutes &&
                        System.currentTimeMillis() > warningDismissedUntil
                    ) {
                        NotificationHelper.showLimitReached(androidx.compose.ui.platform.LocalContext.current)
                    }
                }

                if (hasUsagePermission &&
                    todayUsageMinutes >= settings.dailyLimitMinutes &&
                    System.currentTimeMillis() > warningDismissedUntil &&
                    currentScreen != Screen.WELCOME &&
                    currentScreen != Screen.SETTINGS
                ) {
                    currentScreen = Screen.WARNING
                }
                delay(5000)
            }
        }

        when (currentScreen) {
            Screen.WELCOME -> WelcomeScreen(onGetStarted = { currentScreen = Screen.DASHBOARD })
            Screen.DASHBOARD -> DashboardScreen(
                todayUsageMinutes = todayUsageMinutes,
                dailyLimitMinutes = settings.dailyLimitMinutes,
                hasUsagePermission = hasUsagePermission,
                onOpenUsageAccess = onOpenUsageAccess,
                onSetLimit = { currentScreen = Screen.SET_LIMIT },
                onViewReport = { currentScreen = Screen.SUMMARY },
                onTakeBreak = { currentScreen = Screen.WARNING },
                onOpenSettings = { currentScreen = Screen.SETTINGS },
                onOpenSummary = { currentScreen = Screen.SUMMARY }
            )
            Screen.SET_LIMIT -> SetLimitScreen(
                settings = settings,
                onBack = { currentScreen = Screen.DASHBOARD },
                onSave = {
                    settings = it
                    settingsRepository.saveSettings(it)
                    currentScreen = Screen.DASHBOARD
                }
            )
            Screen.WARNING -> WarningScreen(
                onDismiss = {
                    warningDismissedUntil = System.currentTimeMillis() + 15 * 60 * 1000
                    currentScreen = Screen.DASHBOARD
                },
                onSnooze = {
                    warningDismissedUntil = System.currentTimeMillis() + 30 * 60 * 1000
                    currentScreen = Screen.DASHBOARD
                }
            )
            Screen.SUMMARY -> SummaryScreen(
                summaryData = summaryData,
                onBack = { currentScreen = Screen.DASHBOARD },
                onViewMore = { currentScreen = Screen.DETAILS },
                onOpenSettings = { currentScreen = Screen.SETTINGS }
            )
            Screen.DETAILS -> SummaryDetailsScreen(summaryData = summaryData, onBack = { currentScreen = Screen.SUMMARY })
            Screen.SETTINGS -> SettingsScreen(
                settings = settings,
                onBack = { currentScreen = Screen.DASHBOARD },
                onReset = {
                    settingsRepository.reset()
                    settings = settingsRepository.loadSettings()
                },
                onSave = {
                    settings = it
                    settingsRepository.saveSettings(it)
                    currentScreen = Screen.DASHBOARD
                }
            )
        }
    }
}

@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("ScreenSafe", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(20.dp))
        Text("Manage your screen time and build healthier habits")
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onGetStarted) { Text("Get Started") }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onGetStarted) { Text("Sign In") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    todayUsageMinutes: Long,
    dailyLimitMinutes: Long,
    hasUsagePermission: Boolean,
    onOpenUsageAccess: () -> Unit,
    onSetLimit: () -> Unit,
    onViewReport: () -> Unit,
    onTakeBreak: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSummary: () -> Unit
) {
    val percent = if (dailyLimitMinutes > 0) {
        ((todayUsageMinutes.toFloat() / dailyLimitMinutes.toFloat()) * 100).toInt().coerceAtLeast(0)
    } else 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ScreenSafe") },
                actions = { TextButton(onClick = onOpenSettings) { Text("Menu") } }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = true, onClick = {}, icon = {}, label = { Text("Home") })
                NavigationBarItem(selected = false, onClick = onOpenSummary, icon = {}, label = { Text("Summary") })
                NavigationBarItem(selected = false, onClick = onOpenSettings, icon = {}, label = { Text("Settings") })
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Today's Screen Time")
            Text(formatMinutes(todayUsageMinutes))
            Text("Progress: $percent%")
            Text("Daily Limit: ${formatMinutes(dailyLimitMinutes)}")

            if (!hasUsagePermission) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Usage access is required to track your device time.")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onOpenUsageAccess) { Text("Grant Permission") }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onSetLimit) { Text("Set Limit") }
                OutlinedButton(onClick = onViewReport) { Text("View Report") }
            }
            Button(onClick = onTakeBreak) { Text("Take a Break") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetLimitScreen(settings: AppSettings, onBack: () -> Unit, onSave: (AppSettings) -> Unit) {
    var hoursText by remember { mutableStateOf((settings.dailyLimitMinutes / 60).toString()) }
    var minutesText by remember { mutableStateOf((settings.dailyLimitMinutes % 60).toString().padStart(2, '0')) }
    var reminderFrequency by remember { mutableStateOf(settings.reminderFrequency) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Set Daily Limit") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Choose your daily limit")
            OutlinedTextField(value = hoursText, onValueChange = { hoursText = it.filter(Char::isDigit) }, label = { Text("Hours") })
            OutlinedTextField(value = minutesText, onValueChange = { minutesText = it.filter(Char::isDigit) }, label = { Text("Minutes") })

            ReminderChoice("Every hour", reminderFrequency == ReminderFrequency.EVERY_HOUR) { reminderFrequency = ReminderFrequency.EVERY_HOUR }
            ReminderChoice("Every 2 hours", reminderFrequency == ReminderFrequency.EVERY_2_HOURS) { reminderFrequency = ReminderFrequency.EVERY_2_HOURS }
            ReminderChoice("Custom", reminderFrequency == ReminderFrequency.CUSTOM) { reminderFrequency = ReminderFrequency.CUSTOM }

            Button(onClick = {
                val totalMinutes = ((hoursText.toLongOrNull() ?: 0L) * 60 + (minutesText.toLongOrNull() ?: 0L)).coerceAtLeast(15)
                onSave(settings.copy(dailyLimitMinutes = totalMinutes, reminderFrequency = reminderFrequency))
            }) { Text("Save Limit") }
        }
    }
}

@Composable
fun ReminderChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (selected) "(•)" else "( )")
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(onClick = onClick) { Text(label) }
    }
}

@Composable
fun WarningScreen(onDismiss: () -> Unit, onSnooze: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Warning", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("You have reached your daily screen time limit.")
        Spacer(modifier = Modifier.height(8.dp))
        Text("Consider taking a break.")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onDismiss) { Text("Dismiss") }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onSnooze) { Text("Snooze Alert") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(summaryData: SummaryData, onBack: () -> Unit, onViewMore: () -> Unit, onOpenSettings: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Summary") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = false, onClick = onBack, icon = {}, label = { Text("Home") })
                NavigationBarItem(selected = true, onClick = {}, icon = {}, label = { Text("Summary") })
                NavigationBarItem(selected = false, onClick = onOpenSettings, icon = {}, label = { Text("Settings") })
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Today: ${formatMinutes(summaryData.todayMinutes)}")
            Text("Yesterday: ${formatMinutes(summaryData.yesterdayMinutes)}")
            Text("This Week: ${formatMinutes(summaryData.thisWeekMinutes)}")
            Button(onClick = onViewMore) { Text("View More") }
        }
    }
}

@Composable
fun SummaryDetailsScreen(summaryData: SummaryData, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Detailed Weekly Breakdown", fontWeight = FontWeight.Bold)
        val labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        labels.zip(summaryData.weeklyMinutes).forEach { (label, value) ->
            Text("$label: ${formatMinutes(value)}")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: AppSettings, onBack: () -> Unit, onReset: () -> Unit, onSave: (AppSettings) -> Unit) {
    var notificationsEnabled by remember { mutableStateOf(settings.notificationsEnabled) }
    var breakRemindersEnabled by remember { mutableStateOf(settings.breakRemindersEnabled) }
    var dailySummaryEnabled by remember { mutableStateOf(settings.dailySummaryEnabled) }
    var theme by remember { mutableStateOf(settings.theme) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingToggle("Notifications", notificationsEnabled) { notificationsEnabled = it }
            SettingToggle("Break Reminders", breakRemindersEnabled) { breakRemindersEnabled = it }
            SettingToggle("Daily Summary", dailySummaryEnabled) { dailySummaryEnabled = it }

            Text("Theme: $theme")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { theme = "Light" }) { Text("Light") }
                OutlinedButton(onClick = { theme = "Dark" }) { Text("Dark") }
            }

            OutlinedButton(onClick = onReset) { Text("Reset Data") }
            Button(onClick = {
                onSave(
                    settings.copy(
                        notificationsEnabled = notificationsEnabled,
                        breakRemindersEnabled = breakRemindersEnabled,
                        dailySummaryEnabled = dailySummaryEnabled,
                        theme = theme
                    )
                )
            }) { Text("Save Settings") }
        }
    }
}

@Composable
fun SettingToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

fun formatMinutes(totalMinutes: Long): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) String.format(Locale.US, "%dh %02dm", hours, minutes) else String.format(Locale.US, "%dm", minutes)
}
