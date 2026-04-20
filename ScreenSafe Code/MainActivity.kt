package com.example.safescreen

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.safescreen.data.SettingsRepository
import com.example.safescreen.data.UsageRepository
import com.example.safescreen.model.AppSettings
import com.example.safescreen.model.ReminderFrequency
import com.example.safescreen.model.SummaryData
import com.example.safescreen.ui.SafeScreenTheme
import kotlinx.coroutines.delay
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settingsRepository = SettingsRepository(this)
        val usageRepository = UsageRepository(this)

        setContent {
            SafeScreenRoot(
                settingsRepository = settingsRepository,
                usageRepository = usageRepository,
                onOpenUsageAccess = {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            )
        }
    }
}

private enum class Screen {
    WELCOME,
    DASHBOARD,
    SET_LIMIT,
    WARNING,
    SUMMARY,
    SETTINGS
}

@Composable
fun SafeScreenRoot(
    settingsRepository: SettingsRepository,
    usageRepository: UsageRepository,
    onOpenUsageAccess: () -> Unit
) {
    var settings by remember { mutableStateOf(settingsRepository.loadSettings()) }
    var currentScreen by remember { mutableStateOf(Screen.WELCOME) }
    var todayUsageMinutes by remember { mutableLongStateOf(0L) }
    var hasUsagePermission by remember { mutableStateOf(usageRepository.hasUsagePermission()) }
    var warningDismissedUntil by remember { mutableLongStateOf(0L) }

    SafeScreenTheme(darkTheme = settings.theme == "Dark") {
        LaunchedEffect(settings) {
            while (true) {
                hasUsagePermission = usageRepository.hasUsagePermission()
                if (hasUsagePermission) {
                    todayUsageMinutes = usageRepository.getTodayScreenTimeMinutes()
                    settingsRepository.storeTodayUsage(todayUsageMinutes)
                }

                val shouldShowWarning = hasUsagePermission &&
                    todayUsageMinutes >= settings.dailyLimitMinutes &&
                    System.currentTimeMillis() > warningDismissedUntil &&
                    currentScreen != Screen.WELCOME &&
                    currentScreen != Screen.SETTINGS

                if (shouldShowWarning) {
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
                onSave = { updated ->
                    settings = updated
                    settingsRepository.saveSettings(updated)
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
                summaryData = settingsRepository.loadSummaryData(),
                onBack = { currentScreen = Screen.DASHBOARD },
                onOpenSettings = { currentScreen = Screen.SETTINGS }
            )

            Screen.SETTINGS -> SettingsScreen(
                settings = settings,
                onBack = { currentScreen = Screen.DASHBOARD },
                onReset = {
                    settingsRepository.resetAllData()
                    settings = settingsRepository.loadSettings()
                    todayUsageMinutes = 0L
                },
                onSave = { updated ->
                    settings = updated
                    settingsRepository.saveSettings(updated)
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
        Text(
            text = "ScreenSafe",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Manage your screen time and build healthier habits",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onGetStarted) {
            Text("Get Started")
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onGetStarted) {
            Text("Sign In")
        }
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
                actions = {
                    TextButton(onClick = onOpenSettings) {
                        Text("Menu")
                    }
                }
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
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("Today's Screen Time", style = MaterialTheme.typography.headlineSmall)
            Text(formatMinutes(todayUsageMinutes), style = MaterialTheme.typography.displaySmall)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("|######----- $percent% |".replace("######", "#".repeat((percent / 10).coerceIn(0, 10))))
                Spacer(modifier = Modifier.height(4.dp))
                Text("Daily Limit: ${formatMinutes(dailyLimitMinutes)}")
            }

            if (!hasUsagePermission) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Usage access is required to track screen time accurately.")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onOpenUsageAccess) {
                            Text("Grant Permission")
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onSetLimit) { Text("Set Limit") }
                OutlinedButton(onClick = onViewReport) { Text("View Report") }
            }

            Button(onClick = onTakeBreak) {
                Text("Take a Break")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetLimitScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSave: (AppSettings) -> Unit
) {
    var hoursText by remember { mutableStateOf((settings.dailyLimitMinutes / 60).toString()) }
    var minutesText by remember { mutableStateOf((settings.dailyLimitMinutes % 60).toString().padStart(2, '0')) }
    var expanded by remember { mutableStateOf(false) }
    var reminderFrequency by remember { mutableStateOf(settings.reminderFrequency) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Set Daily Limit") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
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
            Text("Choose your daily limit", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = hoursText,
                onValueChange = { hoursText = it.filter(Char::isDigit) },
                label = { Text("Hours") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = minutesText,
                onValueChange = { minutesText = it.filter(Char::isDigit) },
                label = { Text("Minutes") },
                modifier = Modifier.fillMaxWidth()
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Reminder Frequency:")
                Spacer(modifier = Modifier.height(8.dp))

                ReminderChoice(
                    label = "Every hour",
                    selected = reminderFrequency == ReminderFrequency.EVERY_HOUR,
                    onClick = { reminderFrequency = ReminderFrequency.EVERY_HOUR }
                )
                ReminderChoice(
                    label = "Every 2 hours",
                    selected = reminderFrequency == ReminderFrequency.EVERY_2_HOURS,
                    onClick = { reminderFrequency = ReminderFrequency.EVERY_2_HOURS }
                )
                ReminderChoice(
                    label = "Custom",
                    selected = reminderFrequency == ReminderFrequency.CUSTOM,
                    onClick = { reminderFrequency = ReminderFrequency.CUSTOM }
                )
            }

            Button(onClick = {
                val hours = hoursText.toLongOrNull() ?: 0L
                val minutes = minutesText.toLongOrNull() ?: 0L
                val totalMinutes = (hours * 60 + minutes).coerceAtLeast(15)
                onSave(settings.copy(dailyLimitMinutes = totalMinutes, reminderFrequency = reminderFrequency))
            }) {
                Text("Save Limit")
            }
        }
    }
}

@Composable
private fun ReminderChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (selected) "(•)" else "( )")
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(onClick = onClick) { Text(label) }
    }
}

@Composable
fun WarningScreen(onDismiss: () -> Unit, onSnooze: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Warning", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        Text("You have reached your daily screen time limit.")
        Spacer(modifier = Modifier.height(16.dp))
        Text("Consider taking a break.")
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onDismiss) {
            Text("Dismiss")
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onSnooze) {
            Text("Snooze Alert")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    summaryData: SummaryData,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Summary") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
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
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Today:      ${formatMinutes(summaryData.todayMinutes)}", style = MaterialTheme.typography.titleMedium)
            Text("Yesterday:  ${formatMinutes(summaryData.yesterdayMinutes)}", style = MaterialTheme.typography.titleMedium)
            Text("This Week:  ${formatMinutes(summaryData.thisWeekMinutes)}", style = MaterialTheme.typography.titleMedium)

            Spacer(modifier = Modifier.height(8.dp))
            Text("Weekly Progress", style = MaterialTheme.typography.headlineSmall)

            WeeklyBarRow("Mon", summaryData.weeklyMinutes[0])
            WeeklyBarRow("Tue", summaryData.weeklyMinutes[1])
            WeeklyBarRow("Wed", summaryData.weeklyMinutes[2])
            WeeklyBarRow("Thu", summaryData.weeklyMinutes[3])
            WeeklyBarRow("Fri", summaryData.weeklyMinutes[4])
            WeeklyBarRow("Sat", summaryData.weeklyMinutes[5])
            WeeklyBarRow("Sun", summaryData.weeklyMinutes[6])

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = {}) {
                Text("View More")
            }
        }
    }
}

@Composable
private fun WeeklyBarRow(day: String, minutes: Long) {
    val barCount = (minutes / 60).coerceIn(0, 8).toInt()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(day, modifier = Modifier.width(42.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("|".repeat(barCount).ifBlank { "-" })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onReset: () -> Unit,
    onSave: (AppSettings) -> Unit
) {
    var notificationsEnabled by remember { mutableStateOf(settings.notificationsEnabled) }
    var breakRemindersEnabled by remember { mutableStateOf(settings.breakRemindersEnabled) }
    var dailySummaryEnabled by remember { mutableStateOf(settings.dailySummaryEnabled) }
    var themeExpanded by remember { mutableStateOf(false) }
    var selectedTheme by remember { mutableStateOf(settings.theme) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SettingToggleRow("Notifications", notificationsEnabled) { notificationsEnabled = it }
            SettingToggleRow("Break Reminders", breakRemindersEnabled) { breakRemindersEnabled = it }
            SettingToggleRow("Daily Summary", dailySummaryEnabled) { dailySummaryEnabled = it }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Theme:")
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { themeExpanded = true }) {
                    Text(selectedTheme)
                }
                DropdownMenu(expanded = themeExpanded, onDismissRequest = { themeExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Light") },
                        onClick = {
                            selectedTheme = "Light"
                            themeExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Dark") },
                        onClick = {
                            selectedTheme = "Dark"
                            themeExpanded = false
                        }
                    )
                }
            }

            OutlinedButton(onClick = onReset) {
                Text("Reset Data")
            }

            Button(onClick = {
                onSave(
                    settings.copy(
                        notificationsEnabled = notificationsEnabled,
                        breakRemindersEnabled = breakRemindersEnabled,
                        dailySummaryEnabled = dailySummaryEnabled,
                        theme = selectedTheme
                    )
                )
            }) {
                Text("Save Settings")
            }
        }
    }
}

@Composable
private fun SettingToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun formatMinutes(totalMinutes: Long): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        String.format(Locale.US, "%dh %02dm", hours, minutes)
    } else {
        String.format(Locale.US, "%dm", minutes)
    }
}
