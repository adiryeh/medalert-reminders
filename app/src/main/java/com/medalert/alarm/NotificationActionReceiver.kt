package com.medalert.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.medalert.data.db.entity.IntakeHistory
import com.medalert.data.db.entity.IntakeStatus
import com.medalert.data.repository.MedicationRepository
import com.medalert.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles quick-action taps directly from the notification shade:
 *   ✅ ACTION_TAKEN   — logs TAKEN, cancels notification + missed-check alarm
 *   ❌ ACTION_SKIP    — logs SKIPPED, cancels notification + missed-check alarm
 *   ⏰ ACTION_SNOOZE  — schedules snooze alarm, cancels notification
 *
 * All DB work runs on IO inside goAsync() so the OS won't kill the receiver.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TAKEN  = "com.medalert.ACTION_TAKEN"
        const val ACTION_SKIP   = "com.medalert.ACTION_SKIP"
        const val ACTION_SNOOZE = "com.medalert.ACTION_SNOOZE"

        const val EXTRA_MED_ID        = "medication_id"
        const val EXTRA_MED_NAME      = "medication_name"
        const val EXTRA_MED_DOSAGE    = "medication_dosage"
        const val EXTRA_SCHEDULED_TIME = "scheduled_time"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val medicationId   = intent.getLongExtra(EXTRA_MED_ID, -1L)
        val medicationName = intent.getStringExtra(EXTRA_MED_NAME) ?: return
        val dosage         = intent.getStringExtra(EXTRA_MED_DOSAGE) ?: ""
        val scheduledTime  = intent.getLongExtra(EXTRA_SCHEDULED_TIME, System.currentTimeMillis())

        when (intent.action) {

            ACTION_TAKEN -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val repo = MedicationRepository(context)
                        // Idempotency guard: duplicate notifications from a race between
                        // AlarmReceiver/SafetyNetWorker/FcmReceiverService could otherwise let
                        // the user tap "Taken" twice for the same dose, double-decrementing inventory.
                        if (repo.getHistoryEntry(medicationId, scheduledTime) == null) {
                            repo.logIntake(IntakeHistory(
                                medicationId    = medicationId,
                                scheduledDateTime = scheduledTime,
                                actualDateTime  = System.currentTimeMillis(),
                                status          = IntakeStatus.TAKEN
                            ))
                            repo.decrementInventory(medicationId, context)
                            repo.logAudit(medicationId, medicationName, scheduledTime, "ACTION_TAKEN")
                        }
                    } finally {
                        NotificationHelper.cancelAlert(context, medicationId)
                        AlarmScheduler.cancelMissedCheck(context, medicationId)
                        pending.finish()
                    }
                }
            }

            ACTION_SKIP -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val repo = MedicationRepository(context)
                        if (repo.getHistoryEntry(medicationId, scheduledTime) == null) {
                            repo.logIntake(IntakeHistory(
                                medicationId      = medicationId,
                                scheduledDateTime = scheduledTime,
                                actualDateTime    = System.currentTimeMillis(),
                                status            = IntakeStatus.SKIPPED
                            ))
                            repo.logAudit(medicationId, medicationName, scheduledTime, "ACTION_SKIPPED")
                        }
                    } finally {
                        NotificationHelper.cancelAlert(context, medicationId)
                        AlarmScheduler.cancelMissedCheck(context, medicationId)
                        pending.finish()
                    }
                }
            }

            ACTION_SNOOZE -> {
                val minutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 10)
                AlarmScheduler.scheduleSnooze(context, medicationId, medicationName, dosage, minutes, scheduledTime)
                AlarmScheduler.cancelMissedCheck(context, medicationId)
                NotificationHelper.cancelAlert(context, medicationId)
                CoroutineScope(Dispatchers.IO).launch {
                    MedicationRepository(context).logAudit(medicationId, medicationName, scheduledTime, "ACTION_SNOOZED", "snooze=${minutes}min")
                }
            }
        }
    }
}
