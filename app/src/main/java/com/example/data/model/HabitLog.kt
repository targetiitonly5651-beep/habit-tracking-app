package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class HabitLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val habitId: String = "",
    val date: String = "", // Format: YYYY-MM-DD
    val value: Long = 0L,
    val isCompleted: Boolean = false,
    val note: String = "",
    val completedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
