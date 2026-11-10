package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.Habit
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class HabitRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createHabit_validPayload_createsDocumentAndReturnsId(): Unit = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = HabitRepository(firestore)

        val habit = Habit(
            title = "Read 20 mins",
            category = "Learning",
            colorHex = "#6366F1",
            iconName = "book",
            frequency = "DAILY",
            targetPerDay = 1L,
            unit = "times"
        )

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createHabit(habit)
        }
        assertTrue("Expected createResult to be success", createResult.isSuccess)
        val habitId = createResult.getOrThrow()
        assertTrue("Expected non-empty habitId", habitId.isNotEmpty())
    }

    @Test
    fun observeHabits_authenticatedUser_emitsCreatedHabit(): Unit = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = HabitRepository(firestore)

        val uniqueTitle = "Habit_${UUID.randomUUID().toString().take(6)}"
        val habit = Habit(
            title = uniqueTitle,
            category = "Health",
            colorHex = "#10B981",
            iconName = "water",
            frequency = "DAILY",
            targetPerDay = 8L,
            unit = "glasses"
        )

        val habitId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createHabit(habit, aliceUid).getOrThrow()
        }

        val emittedHabits = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeHabits(aliceUid).first { list -> list.any { it.id == habitId } }
        }
        assertTrue(emittedHabits.any { it.id == habitId })
    }

    @Test
    fun recordHabitProgress_validLog_recordsLogAndCompletes(): Unit = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = HabitRepository(firestore)
        val habitId = "h_${UUID.randomUUID().toString().take(6)}"

        val recordResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.recordHabitProgress(
                habitId = habitId,
                date = "2026-11-09",
                value = 8L,
                targetPerDay = 8L,
                note = "Completed all water glasses!",
                userId = aliceUid
            )
        }
        assertTrue("Expected recordResult to be success", recordResult.isSuccess)

        val logs = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeHabitLogsForDate("2026-11-09", aliceUid).first()
        }
        assertTrue(logs.any { it.habitId == habitId && it.isCompleted })
    }

    @Test
    fun observeHabits_unauthenticatedUser_failsWithPermissionDenied(): Unit = runBlocking {
        auth.signOut()
        val repository = HabitRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeHabits("unauth_user").first()
            }
            fail("Expected exception when unauthenticated")
        } catch (e: Exception) {
            val firestoreException = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", firestoreException)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice@habitpulse.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
