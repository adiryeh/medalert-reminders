package com.medalert.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.medalert.data.db.entity.IntakeStatus
import com.medalert.data.repository.MedicationRepository
import com.medalert.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires ~20 minutes after a medication alarm.
 * Checks whether the user already responded (TAKEN/SKIPPED).
 * If not — shows a short, quiet follow-up notification.
 *
 * This does NOT loop: one follow-up per original alarm, then stops.
 */
class MissedCheckReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_MED_ID         = "medication_id"
        const val EXTRA_MED_NAME       = "medication_name"
        const val EXTRA_SCHEDULED_TIME = "scheduled_time"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val medicationId  = intent.getLongExtra(EXTRA_MED_ID, -1L)
        val medicationName = intent.getStringExtra(EXTRA_MED_NAME) ?: return
        val scheduledTime = intent.getLongExtra(EXTRA_SCHEDULED_TIME, -1L)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = MedicationRepository(context)

                // Check if there's already a log entry for this scheduled time
                val entry = repo.getHistoryEntry(medicationId, scheduledTime)
                val alreadyHandled = entry != null &&
                        entry.status in listOf(IntakeStatus.TAKEN, IntakeStatus.SKIPPED)

                if (!alreadyHandled) {
                    // The original ongoing notification may still be showing —
                    // show an additional quiet nudge in the notification shade.
                    NotificationHelper.showMissedReminder(context, medicationId, medicationName)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
