package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Habit
import com.example.data.model.HabitLog
import com.example.data.model.UserSettings
import com.example.data.repository.HabitRepository
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DayCompletion(
    val date: String,
    val dayOfWeek: String,
    val completedCount: Int,
    val totalCount: Int,
    val percentage: Float
)

data class HeatmapCell(
    val date: String,
    val count: Int,
    val level: Int // 0 to 4
)

data class HabitAnalytics(
    val totalHabits: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val totalCompletions: Int = 0,
    val completionRate: Float = 0f,
    val weeklyTrend: List<DayCompletion> = emptyList(),
    val monthlyHeatmap: List<HeatmapCell> = emptyList()
)

class HabitViewModel(
    private val habitRepository: HabitRepository,
    private val settingsRepository: SettingsRepository,
    private val currentUserId: String
) : ViewModel() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    val todayDate: String = dateFormat.format(Date())

    private val _selectedDate = MutableStateFlow(todayDate)
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    val habits: StateFlow<List<Habit>> = habitRepository.observeHabits(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val selectedDateLogs: StateFlow<List<HabitLog>> = _selectedDate
        .flatMapLatest { date ->
            habitRepository.observeHabitLogsForDate(date, currentUserId)
        }
        .catch { emit(emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val allLogs: StateFlow<List<HabitLog>> = habitRepository.observeAllHabitLogs(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val userSettings: StateFlow<UserSettings?> = settingsRepository.observeUserSettings(currentUserId)
        .catch { emit(null) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    val analytics: StateFlow<HabitAnalytics> = combine(habits, allLogs) { habitList, logsList ->
        computeAnalytics(habitList, logsList)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = HabitAnalytics()
    )

    fun selectDate(date: String) {
        _selectedDate.value = date
    }

    fun clearMessage() {
        _userMessage.value = null
    }

    fun toggleHabitCompletion(habit: Habit) {
        viewModelScope.launch {
            val date = _selectedDate.value
            val existingLog = selectedDateLogs.value.find { it.habitId == habit.id }
            if (existingLog != null && existingLog.isCompleted) {
                // Uncomplete
                habitRepository.deleteHabitLog(habit.id, date, currentUserId)
            } else {
                // Complete
                habitRepository.recordHabitProgress(
                    habitId = habit.id,
                    date = date,
                    value = habit.targetPerDay,
                    targetPerDay = habit.targetPerDay,
                    userId = currentUserId
                )
            }
        }
    }

    fun incrementHabitProgress(habit: Habit, delta: Long) {
        viewModelScope.launch {
            val date = _selectedDate.value
            val existingLog = selectedDateLogs.value.find { it.habitId == habit.id }
            val currentValue = existingLog?.value ?: 0L
            val newValue = (currentValue + delta).coerceAtLeast(0L)

            if (newValue == 0L && existingLog != null) {
                habitRepository.deleteHabitLog(habit.id, date, currentUserId)
            } else {
                habitRepository.recordHabitProgress(
                    habitId = habit.id,
                    date = date,
                    value = newValue,
                    targetPerDay = habit.targetPerDay,
                    userId = currentUserId
                )
            }
        }
    }

    fun createHabit(
        title: String,
        description: String,
        category: String,
        colorHex: String,
        iconName: String,
        frequency: String,
        targetPerDay: Long,
        unit: String,
        reminderTime: String?,
        reminderEnabled: Boolean,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val habit = Habit(
                title = title.trim(),
                description = description.trim(),
                category = category,
                colorHex = colorHex,
                iconName = iconName,
                frequency = frequency,
                targetPerDay = targetPerDay.coerceAtLeast(1L),
                unit = unit.ifBlank { "times" },
                reminderTime = reminderTime,
                reminderEnabled = reminderEnabled
            )

            val result = habitRepository.createHabit(habit, currentUserId)
            _isSaving.value = false
            if (result.isSuccess) {
                _userMessage.value = "Habit created successfully!"
                onSuccess()
            } else {
                _userMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to create habit"
            }
        }
    }

    fun updateHabit(
        habit: Habit,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = habitRepository.updateHabit(habit, currentUserId)
            _isSaving.value = false
            if (result.isSuccess) {
                _userMessage.value = "Habit updated!"
                onSuccess()
            } else {
                _userMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to update habit"
            }
        }
    }

    fun deleteHabit(habitId: String) {
        viewModelScope.launch {
            val result = habitRepository.deleteHabit(habitId, currentUserId)
            if (result.isSuccess) {
                _userMessage.value = "Habit deleted"
            }
        }
    }

    fun updateReminderPreferences(enabled: Boolean, time: String) {
        viewModelScope.launch {
            settingsRepository.updateReminderSettings(enabled, time, currentUserId)
        }
    }

    fun createStarterHabits() {
        viewModelScope.launch {
            _isSaving.value = true
            val starters = listOf(
                Habit(
                    title = "Drink 8 Glasses of Water",
                    description = "Stay hydrated throughout the day",
                    category = "Health",
                    colorHex = "#06B6D4",
                    iconName = "water",
                    targetPerDay = 8L,
                    unit = "glasses",
                    reminderEnabled = true,
                    reminderTime = "09:00"
                ),
                Habit(
                    title = "Read 20 Pages",
                    description = "Expand mind with daily focused reading",
                    category = "Learning",
                    colorHex = "#6366F1",
                    iconName = "book",
                    targetPerDay = 20L,
                    unit = "pages",
                    reminderEnabled = true,
                    reminderTime = "21:00"
                ),
                Habit(
                    title = "Morning Workout",
                    description = "30 minutes of stretching, cardio, or lifting",
                    category = "Fitness",
                    colorHex = "#10B981",
                    iconName = "fitness",
                    targetPerDay = 30L,
                    unit = "minutes",
                    reminderEnabled = true,
                    reminderTime = "07:30"
                )
            )

            starters.forEach { habit ->
                habitRepository.createHabit(habit, currentUserId)
            }
            _isSaving.value = false
            _userMessage.value = "Starter habits added!"
        }
    }

    private fun computeAnalytics(habits: List<Habit>, logs: List<HabitLog>): HabitAnalytics {
        val completedLogs = logs.filter { it.isCompleted }
        val datesWithCompletions = completedLogs.map { it.date }.toSet()

        // Streak calculation
        val currentStreak = calculateCurrentStreak(datesWithCompletions)
        val bestStreak = calculateBestStreak(datesWithCompletions)

        // 7-day weekly trend
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val weeklyTrend = mutableListOf<DayCompletion>()
        val cal = Calendar.getInstance()
        cal.time = Date()
        cal.add(Calendar.DAY_OF_YEAR, -6)

        for (i in 0..6) {
            val dStr = dateFormat.format(cal.time)
            val dName = dayFormat.format(cal.time)
            val dayLogs = logs.filter { it.date == dStr && it.isCompleted }
            val completed = dayLogs.size
            val total = habits.size.coerceAtLeast(1)
            val pct = (completed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            weeklyTrend.add(DayCompletion(dStr, dName, completed, habits.size, pct))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        // Monthly Heatmap (last 28 days)
        val monthlyHeatmap = mutableListOf<HeatmapCell>()
        val heatCal = Calendar.getInstance()
        heatCal.time = Date()
        heatCal.add(Calendar.DAY_OF_YEAR, -27)

        val maxHabits = habits.size.coerceAtLeast(1)
        for (i in 0..27) {
            val dStr = dateFormat.format(heatCal.time)
            val count = logs.count { it.date == dStr && it.isCompleted }
            val ratio = count.toFloat() / maxHabits.toFloat()
            val level = when {
                count == 0 -> 0
                ratio <= 0.25f -> 1
                ratio <= 0.5f -> 2
                ratio <= 0.75f -> 3
                else -> 4
            }
            monthlyHeatmap.add(HeatmapCell(dStr, count, level))
            heatCal.add(Calendar.DAY_OF_YEAR, 1)
        }

        val totalExpected = (habits.size * 7).coerceAtLeast(1)
        val recent7Completed = weeklyTrend.sumOf { it.completedCount }
        val rate = (recent7Completed.toFloat() / totalExpected.toFloat()).coerceIn(0f, 1f)

        return HabitAnalytics(
            totalHabits = habits.size,
            currentStreak = currentStreak,
            bestStreak = bestStreak,
            totalCompletions = completedLogs.size,
            completionRate = rate,
            weeklyTrend = weeklyTrend,
            monthlyHeatmap = monthlyHeatmap
        )
    }

    private fun calculateCurrentStreak(completedDates: Set<String>): Int {
        if (completedDates.isEmpty()) return 0

        val cal = Calendar.getInstance()
        val todayStr = dateFormat.format(cal.time)
        var streak = 0

        if (completedDates.contains(todayStr)) {
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        } else {
            // Check if streak was active up to yesterday
            cal.add(Calendar.DAY_OF_YEAR, -1)
            val yesterdayStr = dateFormat.format(cal.time)
            if (!completedDates.contains(yesterdayStr)) {
                return 0
            }
        }

        while (true) {
            val dStr = dateFormat.format(cal.time)
            if (completedDates.contains(dStr)) {
                streak++
                cal.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }
        return streak
    }

    private fun calculateBestStreak(completedDates: Set<String>): Int {
        if (completedDates.isEmpty()) return 0
        val sortedDates = completedDates.sorted()
        var maxStreak = 1
        var current = 1

        val cal = Calendar.getInstance()
        for (i in 0 until sortedDates.size - 1) {
            val d1 = dateFormat.parse(sortedDates[i]) ?: continue
            val d2 = dateFormat.parse(sortedDates[i + 1]) ?: continue
            cal.time = d1
            cal.add(Calendar.DAY_OF_YEAR, 1)
            if (dateFormat.format(cal.time) == sortedDates[i + 1]) {
                current++
                if (current > maxStreak) maxStreak = current
            } else {
                current = 1
            }
        }
        return maxStreak
    }
}
