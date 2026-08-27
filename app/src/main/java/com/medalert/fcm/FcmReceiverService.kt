package com.medalert.fcm

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.medalert.data.repository.MedicationRepository
import com.medalert.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Last-resort push channel. A GitHub Action polls the anonymized occurrence timestamps
 * we upload via FcmSync and sends a data-only push (medicationId + scheduledTime, nothing
 * else) when local AlarmManager + SafetyNetWorker both failed to fire in time. This maps
 * the anonymous id back to the real medication using the local Room DB — the server never
 * knows what medicationId actually is.
 */
class FcmReceiverService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        FcmSync.syncToken(applicationContext)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d("FcmReceiverService", "onMessageReceived: data=${message.data}")
        val medicationId = message.data["medicationId"]?.toLongOrNull() ?: return
        val scheduledTime = message.data["scheduledTime"]?.toLongOrNull() ?: return

        CoroutineScope(Dispatchers.IO).launch {
            val repo = MedicationRepository(applicationContext)
            val med = repo.getMedication(medicationId) ?: return@launch
            if (!med.active) return@launch

            if (repo.getHistoryEntry(medicationId, scheduledTime) != null) return@launch

            val delayMin = (System.currentTimeMillis() - scheduledTime) / 60_000
            // Atomically check-and-claim so AlarmReceiver/SafetyNetWorker racing in for the
            // same dose (this push can arrive late, after local channels already recovered)
            // can't both pass a plain check before either writes.
            if (!repo.claimFire(medicationId, med.name, scheduledTime, "FCM_FIRED", "missed by ${delayMin}min")) return@launch

            val settings = repo.getSettings(medicationId)
            NotificationHelper.showMedicationAlert(
                applicationContext, medicationId, med.name, med.dosage, scheduledTime, settings
            )
        }
    }
}
