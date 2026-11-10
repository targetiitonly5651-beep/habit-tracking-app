package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Habit(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "Health",
    val colorHex: String = "#6366F1",
    val iconName: String = "star",
    val frequency: String = "DAILY",
    val targetPerDay: Long = 1L,
    val unit: String = "times",
    val reminderTime: String? = null,
    val reminderEnabled: Boolean = false,
    val isArchived: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
