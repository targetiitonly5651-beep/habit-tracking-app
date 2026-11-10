package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Restore default daily reminder at 20:00
            HabitNotificationManager.scheduleDailyReminder(
                context = context,
                hour = 20,
                minute = 0,
                title = "HabitPulse Reminder",
                message = "Keep your streaks going! Check off your habits today."
            )
        }
    }
}
