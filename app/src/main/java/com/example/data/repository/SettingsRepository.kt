package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.UserSettings
import com.example.data.util.OperationType
import com.example.data.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class SettingsRepository(private val db: FirebaseFirestore) {

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

    fun observeUserSettings(userId: String = requireUserId()): Flow<UserSettings?> {
        val path = "users/$userId"
        return db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserSettings::class.java)
                } else {
                    null
                }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.GET, path)
                throw error
            }
    }

    suspend fun saveUserSettings(settings: UserSettings, userId: String = requireUserId()): Result<Unit> {
        val docRef = db.collection("users").document(userId)
        val payload = mapOf(
            "userId" to userId,
            "dailyReminderEnabled" to settings.dailyReminderEnabled,
            "dailyReminderTime" to settings.dailyReminderTime,
            "userName" to settings.userName,
            "email" to settings.email,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

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

    suspend fun updateReminderSettings(enabled: Boolean, time: String, userId: String = requireUserId()): Result<Unit> {
        val docRef = db.collection("users").document(userId)
        val payload = mapOf(
            "dailyReminderEnabled" to enabled,
            "dailyReminderTime" to time,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        return try {
            val exists = docRef.get().await().exists()
            if (!exists) {
                docRef.set(
                    mapOf(
                        "userId" to userId,
                        "dailyReminderEnabled" to enabled,
                        "dailyReminderTime" to time,
                        "userName" to (auth.currentUser?.displayName ?: "User"),
                        "email" to (auth.currentUser?.email ?: ""),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }
}
