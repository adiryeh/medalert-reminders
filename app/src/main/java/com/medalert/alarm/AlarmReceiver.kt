package com.medalert.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.medalert.data.repository.MedicationRepository
import com.medalert.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val medicationId = intent.getLongExtra("medication_id", -1)
        val medicationName = intent.getStringExtra("medication_name") ?: return
        val dosage = intent.getStringExtra("medication_dosage") ?: ""
        val scheduledTime = intent.getLongExtra("scheduled_time", System.currentTimeMillis())

        // Load ReminderSettings to apply per-medication sound/vibrate/priority,
        // then show notification with fullScreenIntent.
        // NOTE: Do NOT call context.startActivity() here — starting activities
        // from a BroadcastReceiver background is blocked on Android 10+ (API 29+).
        // The fullScreenIntent in the notification is the correct mechanism.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = MedicationRepository(context)
                repo.logAudit(medicationId, medicationName, scheduledTime, "RECEIVER_FIRED")
                if (repo.getHistoryEntry(medicationId, scheduledTime) != null) return@launch

                // Atomically check-and-claim so SafetyNetWorker/FcmReceiverService racing in
                // for the same dose can't both pass a plain check before either writes.
                if (!repo.claimFire(medicationId, medicationName, scheduledTime, "NOTIFICATION_SHOWN")) return@launch

                val settings = repo.getSettings(medicationId)
                NotificationHelper.showMedicationAlert(
                    context, medicationId, medicationName, dosage, scheduledTime, settings
                )
                // Schedule a missed-check 20 minutes later.
                // If the user acts (taken/skip/snooze) the check is cancelled automatically.
                val isSnooze = intent.getBooleanExtra("is_snooze", false)
                if (!isSnooze) {
                    AlarmScheduler.scheduleMissedCheck(context, medicationId, medicationName, scheduledTime)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
