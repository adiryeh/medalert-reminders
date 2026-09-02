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
 * Uses a dynamic lookback window PER MEDICATION: scans from the last time THIS
 * medication was successfully checked (stored in SharedPreferences, keyed by
 * medication id) to now-1min. This guarantees no gap is ever skipped regardless of
 * how long Samsung delays WorkManager between runs.
 *
 * Per-medication windows are deliberate, not just per-medication error isolation:
 * if a medication's window advanced globally and that one run threw partway through,
 * the next run's window would start AFTER the missed dose, losing it forever. Keeping
 * each medication's "last successfully checked" timestamp separate means a failure on
 * one medication only delays retries for that medication — the missed dose stays in
 * range until it's actually processed successfully.
 *
 * Deduplication via claimFire() prevents double-firing when multiple backed-up runs
 * fire in quick succession after Doze, or when another channel (AlarmReceiver,
 * FcmReceiverService) claims the same dose first.
 */
class SafetyNetWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val repo  = MedicationRepository(applicationContext)
        val now   = System.currentTimeMillis()

        val medications = repo.getActiveMedications()

        repo.logAudit(
            medicationId   = -1L,
            medicationName = "מערכת",
            scheduledTime  = now,
            event          = "SAFETY_NET_CHECKED",
            note           = "בדק ${medications.size} תרופות"
        )

        for (med in medications) {
            val lastRunKey  = "$KEY_LAST_RUN_PREFIX${med.id}"
            val windowStart = prefs.getLong(lastRunKey, now - DEFAULT_FIRST_WINDOW_MS)
            val windowEnd   = now - ALARM_GRACE_MS
            if (windowEnd <= windowStart) continue

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
                // Only advance THIS medication's window on success — if we threw above,
                // leave it as-is so the next run retries the same (now slightly wider) window
                // instead of silently skipping past whatever we failed to check.
                prefs.edit().putLong(lastRunKey, now).apply()
            } catch (e: Exception) {
                android.util.Log.e("SafetyNetWorker", "Failed processing medication ${med.id}", e)
                // Visible in the in-app audit log, not just Logcat — Logcat rotates out within
                // hours, so a failure here was previously undiagnosable after the fact.
                repo.logAudit(med.id, med.name, now, "SAFETY_NET_ERROR", e.message ?: e.javaClass.simpleName ?: "unknown error")
            }
        }

        return Result.success()
    }

    companion object {
        private const val PREFS_NAME             = "safety_net_prefs"
        private const val KEY_LAST_RUN_PREFIX    = "last_run_med_"
        private const val ALARM_GRACE_MS         = 1 * 60_000L        // 1 min grace for AlarmManager to fire first
        private const val DEFAULT_FIRST_WINDOW_MS = 24 * 60 * 60_000L // first-ever run for a medication: look back 24h

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
