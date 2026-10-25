package com.medalert

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.medalert.worker.IntegrityCheckWorker
import com.medalert.worker.SafetyNetWorker
import java.util.concurrent.TimeUnit

class MedAlertApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        scheduleIntegrityCheck()
        SafetyNetWorker.schedule(this)
        com.medalert.fcm.FcmSync.syncToken(this)
        com.medalert.util.ProcessDiagnostics.record(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // Main alerts channel — IMPORTANCE_HIGH for heads-up on lock screen
            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "התראות תרופות",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "תזכורות לנטילת תרופות"
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            // Critical alerts — same importance (Android max for notification channels)
            // Priority escalated to MAX in NotificationCompat builder
            val criticalChannel = NotificationChannel(
                CHANNEL_ALERTS_CRITICAL,
                "התראות תרופה — קריטי",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "התראות קריטיות לנטילת תרופות"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            // Inventory low-stock channel
            val inventoryChannel = NotificationChannel(
                CHANNEL_INVENTORY,
                "התראות מלאי",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "התראות על מלאי נמוך"
            }

            manager.createNotificationChannels(listOf(alertChannel, criticalChannel, inventoryChannel))
        }
    }

    private fun scheduleIntegrityCheck() {
        // Runs once every 24h with a 3h flex window.
        // Flex window = Android can run it any time in the LAST 3 hours of the 24h period.
        // This avoids wake-lock conflicts while ensuring it runs at least once a day.
        //
        // backoffCriteria: if the worker fails, retry after 30min (EXPONENTIAL backoff cap = ~5h).
        // No network constraint — runs fully offline.
        val request = PeriodicWorkRequestBuilder<IntegrityCheckWorker>(
            repeatInterval = 24, repeatIntervalTimeUnit = TimeUnit.HOURS,
            flexTimeInterval = 3,  flexTimeIntervalUnit = TimeUnit.HOURS
        )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "integrity_check",
            // UPDATE (not KEEP): if app was updated with a newer worker, replace the old one.
            // Safe because PeriodicWork remembers the last run time so it won't run twice.
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    companion object {
        const val CHANNEL_ALERTS          = "medication_alerts"
        const val CHANNEL_ALERTS_CRITICAL = "medication_alerts_critical"
        const val CHANNEL_INVENTORY       = "inventory_alerts"
    }
}
