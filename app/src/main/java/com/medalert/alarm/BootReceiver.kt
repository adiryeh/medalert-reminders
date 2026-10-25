package com.medalert.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.medalert.worker.RescheduleWorker
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Trigger on:
        // • BOOT_COMPLETED          — device rebooted (alarms cleared by OS)
        // • LOCKED_BOOT_COMPLETED   — device rebooted, still on lock screen (Direct Boot)
        // • MY_PACKAGE_REPLACED     — app updated (AlarmManager cleared by OS on upgrade)
        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.LOCKED_BOOT_COMPLETED",
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
        if (intent.action !in validActions) return

        // Dispatch all rescheduling to WorkManager (no time limit).
        // goAsync() is finished immediately after enqueue — we never risk the 10s timeout.
        // cancelFirst=false: OS already cleared AlarmManager, just reschedule.
        val pending = goAsync()
        try {
            RescheduleWorker.enqueue(context, cancelFirst = false)
        } catch (e: Exception) {
            android.util.Log.e("BootReceiver", "enqueue failed", e)
        }
        val event = if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) "APP_UPDATED" else "DEVICE_BOOT"
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                com.medalert.data.repository.MedicationRepository(context)
                    .logAudit(-1L, "מערכת", System.currentTimeMillis(), event, intent.action ?: "")
            } finally {
                pending.finish()
            }
        }
    }
}
