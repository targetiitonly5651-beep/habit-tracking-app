package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.Habit
import com.example.data.model.HabitLog
import com.example.data.util.OperationType
import com.example.data.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class HabitRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeHabits(userId: String = requireUserId()): Flow<List<Habit>> {
        val path = "users/$userId/habits"
        return db.collection("users").document(userId).collection("habits")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Habit::class.java).filter { !it.isArchived }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeHabitLogsForDate(date: String, userId: String = requireUserId()): Flow<List<HabitLog>> {
        val path = "users/$userId/habit_logs"
        return db.collection("users").document(userId).collection("habit_logs")
            .whereEqualTo("date", date)
            .snapshots()
            .map { snapshot -> snapshot.toObjects(HabitLog::class.java) }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeAllHabitLogs(userId: String = requireUserId()): Flow<List<HabitLog>> {
        val path = "users/$userId/habit_logs"
        return db.collection("users").document(userId).collection("habit_logs")
            .snapshots()
            .map { snapshot -> snapshot.toObjects(HabitLog::class.java) }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun createHabit(habit: Habit, userId: String = requireUserId()): Result<String> {
        val docRef = db.collection("users").document(userId).collection("habits").document()
        val payload = mutableMapOf<String, Any>(
            "userId" to userId,
            "title" to habit.title.trim(),
            "category" to habit.category,
            "colorHex" to habit.colorHex,
            "iconName" to habit.iconName,
            "frequency" to habit.frequency,
            "targetPerDay" to habit.targetPerDay,
            "unit" to habit.unit,
            "reminderEnabled" to habit.reminderEnabled,
            "isArchived" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (habit.description.isNotBlank()) {
            payload["description"] = habit.description.trim()
        }
        if (!habit.reminderTime.isNullOrBlank()) {
            payload["reminderTime"] = habit.reminderTime
        }

        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateHabit(habit: Habit, userId: String = requireUserId()): Result<Unit> {
        require(habit.id.isNotBlank()) { "Habit ID must not be blank for update" }
        val docRef = db.collection("users").document(userId).collection("habits").document(habit.id)
        val payload = mutableMapOf<String, Any>(
            "userId" to userId,
            "title" to habit.title.trim(),
            "category" to habit.category,
            "colorHex" to habit.colorHex,
            "iconName" to habit.iconName,
            "frequency" to habit.frequency,
            "targetPerDay" to habit.targetPerDay,
            "unit" to habit.unit,
            "reminderEnabled" to habit.reminderEnabled,
            "isArchived" to habit.isArchived,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (habit.description.isNotBlank()) {
            payload["description"] = habit.description.trim()
        }
        if (!habit.reminderTime.isNullOrBlank()) {
            payload["reminderTime"] = habit.reminderTime
        }

        return try {
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteHabit(habitId: String, userId: String = requireUserId()): Result<Unit> {
        val docRef = db.collection("users").document(userId).collection("habits").document(habitId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun recordHabitProgress(
        habitId: String,
        date: String,
        value: Long,
        targetPerDay: Long,
        note: String = "",
        userId: String = requireUserId()
    ): Result<Unit> {
        val logDocId = "${habitId}_${date}"
        val docRef = db.collection("users").document(userId).collection("habit_logs").document(logDocId)
        val isCompleted = value >= targetPerDay

        val payload = mutableMapOf<String, Any>(
            "userId" to userId,
            "habitId" to habitId,
            "date" to date,
            "value" to value,
            "isCompleted" to isCompleted,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (isCompleted) {
            payload["completedAt"] = Timestamp.now()
        }
        if (note.isNotBlank()) {
            payload["note"] = note.trim()
        }

        return try {
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteHabitLog(habitId: String, date: String, userId: String = requireUserId()): Result<Unit> {
        val logDocId = "${habitId}_${date}"
        val docRef = db.collection("users").document(userId).collection("habit_logs").document(logDocId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }
}
