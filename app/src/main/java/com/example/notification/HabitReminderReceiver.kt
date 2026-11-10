package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class HabitReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "HabitPulse Daily Reminder"
        val message = intent.getStringExtra("message") ?: "Time to check off your habits for today! 🔥"
        val hour = intent.getIntExtra("hour", 20)
        val minute = intent.getIntExtra("minute", 0)

        // Show the notification to the user
        HabitNotificationManager.showNotification(context, title, message)

        // Reschedule alarm for the next day
        HabitNotificationManager.scheduleDailyReminder(context, hour, minute, title, message)
    }
}
