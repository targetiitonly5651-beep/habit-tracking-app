package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.model.Habit
import com.example.data.repository.HabitRepository
import com.example.data.repository.SettingsRepository
import com.example.ui.components.AddEditHabitDialog
import com.example.ui.theme.AccentCoral
import com.example.ui.viewmodel.HabitViewModel
import com.google.firebase.firestore.FirebaseFirestore

enum class NavigationTab(val title: String) {
    TODAY("Today"),
    HABITS("Habits"),
    ANALYTICS("Analytics"),
    SETTINGS("Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainFeatureScreen(
    currentUserId: String,
    onSignOut: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val databaseId = remember { context.getString(R.string.firestore_database_id) }
    val firestore = remember(databaseId) { FirebaseFirestore.getInstance(databaseId) }

    val viewModel: HabitViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val habitRepo = HabitRepository(firestore)
                val settingsRepo = SettingsRepository(firestore)
                HabitViewModel(habitRepo, settingsRepo, currentUserId)
            }
        }
    )

    var currentTab by remember { mutableStateOf(NavigationTab.TODAY) }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var habitToEdit by remember { mutableStateOf<Habit?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val habits by viewModel.habits.collectAsStateWithLifecycle()
    val todayLogs by viewModel.selectedDateLogs.collectAsStateWithLifecycle()
    val allLogs by viewModel.allLogs.collectAsStateWithLifecycle()
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
    val analytics by viewModel.analytics.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    // BackHandler to return to Today tab if on secondary tab
    BackHandler(enabled = currentTab != NavigationTab.TODAY) {
        currentTab = NavigationTab.TODAY
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_habit_logo),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = when (currentTab) {
                                NavigationTab.TODAY -> "Today's Habits"
                                NavigationTab.HABITS -> "All Habits"
                                NavigationTab.ANALYTICS -> "Progress & Analytics"
                                NavigationTab.SETTINGS -> "Settings"
                            },
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                actions = {
                    if (analytics.currentStreak > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = "Streak",
                                tint = AccentCoral,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${analytics.currentStreak}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = AccentCoral
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == NavigationTab.TODAY,
                    onClick = { currentTab = NavigationTab.TODAY },
                    icon = { Icon(Icons.Default.CheckCircle, contentDescription = "Today") },
                    label = { Text("Today") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.testTag("nav_tab_today")
                )
                NavigationBarItem(
                    selected = currentTab == NavigationTab.HABITS,
                    onClick = { currentTab = NavigationTab.HABITS },
                    icon = { Icon(Icons.Default.FormatListBulleted, contentDescription = "Habits") },
                    label = { Text("Habits") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.testTag("nav_tab_habits")
                )
                NavigationBarItem(
                    selected = currentTab == NavigationTab.ANALYTICS,
                    onClick = { currentTab = NavigationTab.ANALYTICS },
                    icon = { Icon(Icons.Default.BarChart, contentDescription = "Analytics") },
                    label = { Text("Analytics") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.testTag("nav_tab_analytics")
                )
                NavigationBarItem(
                    selected = currentTab == NavigationTab.SETTINGS,
                    onClick = { currentTab = NavigationTab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.testTag("nav_tab_settings")
                )
            }
        },
        floatingActionButton = {
            if (currentTab == NavigationTab.TODAY || currentTab == NavigationTab.HABITS) {
                FloatingActionButton(
                    onClick = {
                        habitToEdit = null
                        showAddEditDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("fab_add_habit")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Habit")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                NavigationTab.TODAY -> {
                    TodayHabitsScreen(
                        habits = habits,
                        todayLogs = todayLogs,
                        analytics = analytics,
                        selectedDate = selectedDate,
                        todayDate = viewModel.todayDate,
                        onSelectDate = { viewModel.selectDate(it) },
                        onToggleHabit = { viewModel.toggleHabitCompletion(it) },
                        onIncrementHabit = { habit, delta -> viewModel.incrementHabitProgress(habit, delta) },
                        onAddNewHabit = {
                            habitToEdit = null
                            showAddEditDialog = true
                        },
                        onAddStarterHabits = { viewModel.createStarterHabits() },
                        onEditHabit = { habit ->
                            habitToEdit = habit
                            showAddEditDialog = true
                        }
                    )
                }
                NavigationTab.HABITS -> {
                    HabitsListScreen(
                        habits = habits,
                        onAddNewHabit = {
                            habitToEdit = null
                            showAddEditDialog = true
                        },
                        onEditHabit = { habit ->
                            habitToEdit = habit
                            showAddEditDialog = true
                        },
                        onDeleteHabit = { habitId -> viewModel.deleteHabit(habitId) }
                    )
                }
                NavigationTab.ANALYTICS -> {
                    AnalyticsScreen(
                        analytics = analytics,
                        habits = habits,
                        allLogs = allLogs
                    )
                }
                NavigationTab.SETTINGS -> {
                    SettingsScreen(
                        userSettings = userSettings,
                        onUpdateReminder = { enabled, time ->
                            viewModel.updateReminderPreferences(enabled, time)
                        },
                        onSignOut = onSignOut
                    )
                }
            }
        }
    }

    if (showAddEditDialog) {
        AddEditHabitDialog(
            habitToEdit = habitToEdit,
            onDismiss = { showAddEditDialog = false },
            onSave = { title, description, category, colorHex, iconName, frequency, targetPerDay, unit, reminderTime, reminderEnabled ->
                val currentEditing = habitToEdit
                if (currentEditing == null) {
                    viewModel.createHabit(
                        title = title,
                        description = description,
                        category = category,
                        colorHex = colorHex,
                        iconName = iconName,
                        frequency = frequency,
                        targetPerDay = targetPerDay,
                        unit = unit,
                        reminderTime = reminderTime,
                        reminderEnabled = reminderEnabled,
                        onSuccess = { showAddEditDialog = false }
                    )
                } else {
                    val updated = currentEditing.copy(
                        title = title,
                        description = description,
                        category = category,
                        colorHex = colorHex,
                        iconName = iconName,
                        frequency = frequency,
                        targetPerDay = targetPerDay,
                        unit = unit,
                        reminderTime = reminderTime,
                        reminderEnabled = reminderEnabled
                    )
                    viewModel.updateHabit(
                        habit = updated,
                        onSuccess = { showAddEditDialog = false }
                    )
                }
            }
        )
    }
}
