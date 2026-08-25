package com.medalert.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.medalert.alarm.AlarmScheduler
import com.medalert.data.repository.MedicationRepository
import com.medalert.notification.NotificationHelper

class IntegrityCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = MedicationRepository(applicationContext)

        // 1. Mark any snoozed-but-not-confirmed alerts as MISSED (older than 2 hours)
        val missedCutoff = System.currentTimeMillis() - 2 * 3600_000L
        repo.markMissedBefore(missedCutoff)

        // Purge audit log entries older than 14 days
        repo.purgeOldAuditLog()

        // 2. Reschedule all active medications (ensures 100 future alarms always exist)
        val medications = repo.getActiveMedications()
        for (med in medications) {
            val schedule = repo.getSchedule(med.id) ?: continue
            AlarmScheduler.scheduleAlarms(applicationContext, med, schedule)

            // 3. Check inventory levels and notify if needed
            val inv = repo.getInventory(med.id) ?: continue
            if (inv.currentQuantity <= 0) {
                NotificationHelper.showInventoryAlert(
                    applicationContext, med.name, 0.0, inv.unit.name.lowercase()
                )
            } else if (inv.currentQuantity <= inv.alertThreshold) {
                NotificationHelper.showInventoryAlert(
                    applicationContext, med.name, inv.currentQuantity, inv.unit.name.lowercase()
                )
            }
        }

        return Result.success()
    }
}
