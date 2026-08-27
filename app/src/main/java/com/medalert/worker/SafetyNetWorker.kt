package com.medalert.worker

import android.content.Context
import androidx.work.*
import com.medalert.alarm.AlarmScheduler
import com.medalert.data.repository.MedicationRepository
import com.medalert.notification.NotificationHelper
import com.medalert.util.occurrencesInRange
import java.util.concurrent.TimeUnit

/**
 * Safety-net that runs every 15 minutes and re-fires any alarm that AlarmManager
 * should have triggered but didn't (killed by OEM battery optimizer, Doze, etc.).
 *
 * Uses a dynamic lookback window: scans from the last time this worker successfully
 * ran (stored in SharedPreferences) to now-1min. This guarantees no gap is ever
 * skipped regardless of how long Samsung delays WorkManager between runs.
 *
 * Deduplication via hasAuditEvent(SAFETY_NET_FIRED) prevents double-firing when
 * multiple backed-up runs fire in quick succession after Doze.
 */
class SafetyNetWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val repo  = MedicationRepository(applicationContext)
        val now   = System.currentTimeMillis()

        // Dynamic window: scan from last successful run up to 1 min ago (grace for AlarmManager)
        val lastRun     = prefs.getLong(KEY_LAST_RUN, now - DEFAULT_FIRST_WINDOW_MS)
        val windowStart = lastRun
        val windowEnd   = now - ALARM_GRACE_MS

        val medications = repo.getActiveMedications()

        repo.logAudit(
            medicationId   = -1L,
            medicationName = "מערכת",
            scheduledTime  = now,
            event          = "SAFETY_NET_CHECKED",
            note           = "חלון: ${(windowEnd - windowStart) / 60_000}min · ${medications.size} תרופות"
        )

        if (windowEnd > windowStart) {
            for (med in medications) {
                // Isolate failures per medication — one bad schedule (e.g. corrupt JSON, a
                // null settings row) must not stop the safety net from checking every other
                // medication in this run. Mirrors RescheduleWorker's per-item isolation.
                try {
                    val schedule = repo.getSchedule(med.id) ?: continue
                    val settings = repo.getSettings(med.id)

                    val occurrences = occurrencesInRange(schedule, windowStart, windowEnd)
                    for (time in occurrences) {
                        // Already handled by user (taken / skipped / missed)?
                        if (repo.getHistoryEntry(med.id, time) != null) continue

                        // User already snoozed — AlarmManager will re-fire on its own
                        if (AlarmScheduler.isSnoozeScheduled(applicationContext, med.id)) continue

                        // Atomically check-and-claim so a concurrent AlarmReceiver/FcmReceiverService
                        // firing for the same dose can't race past this check and double-notify.
                        if (!repo.claimFire(med.id, med.name, time, "SAFETY_NET_FIRED", "missed by ${(now - time) / 60_000}min")) continue

                        NotificationHelper.showMedicationAlert(
                            applicationContext, med.id, med.name, med.dosage, time, settings
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SafetyNetWorker", "Failed processing medication ${med.id}", e)
                }
            }
        }

        // Persist successful run time so next run knows where to start scanning
        prefs.edit().putLong(KEY_LAST_RUN, now).apply()
        return Result.success()
    }

    companion object {
        private const val PREFS_NAME             = "safety_net_prefs"
        private const val KEY_LAST_RUN           = "last_run_timestamp"
        private const val ALARM_GRACE_MS         = 1 * 60_000L        // 1 min grace for AlarmManager to fire first
        private const val DEFAULT_FIRST_WINDOW_MS = 24 * 60 * 60_000L // first-ever run: look back 24h

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SafetyNetWorker>(15, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "safety_net",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
