package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

object HabitIcons {
    val iconsMap = mapOf<String, ImageVector>(
        "water" to Icons.Default.LocalDrink,
        "fitness" to Icons.Default.FitnessCenter,
        "run" to Icons.AutoMirrored.Filled.DirectionsRun,
        "book" to Icons.Default.Book,
        "meditation" to Icons.Default.SelfImprovement,
        "code" to Icons.Default.Code,
        "sleep" to Icons.Default.Nightlight,
        "sun" to Icons.Default.WbSunny,
        "star" to Icons.Default.Star,
        "spa" to Icons.Default.Spa,
        "fire" to Icons.Default.LocalFireDepartment,
        "check" to Icons.Default.Check
    )

    fun getIcon(name: String): ImageVector {
        return iconsMap[name] ?: Icons.Default.Star
    }

    val availableColors = listOf(
        "#6366F1", // Electric Indigo
        "#10B981", // Emerald
        "#06B6D4", // Cyan
        "#F59E0B", // Amber
        "#F97316", // Coral Orange
        "#EC4899", // Pink
        "#8B5CF6", // Purple
        "#3B82F6"  // Blue
    )

    val availableCategories = listOf(
        "Health",
        "Productivity",
        "Fitness",
        "Mindfulness",
        "Learning",
        "Lifestyle"
    )

    fun parseColor(hex: String, fallback: Color = Color(0xFF6366F1)): Color {
        return try {
            Color(android.graphics.Color.parseColor(hex))
        } catch (e: Exception) {
            fallback
        }
    }
}
