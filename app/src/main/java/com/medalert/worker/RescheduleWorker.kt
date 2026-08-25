package com.medalert.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.medalert.alarm.AlarmScheduler
import com.medalert.data.repository.MedicationRepository

/**
 * One-time WorkManager task that reschedules all alarms without any time limit.
 *
 * Used by BootReceiver and TimeChangeReceiver instead of doing heavy DB work
 * inside goAsync() — which has a hard 10-second budget.
 *
 * IMPORTANT: cancel+reschedule happens PER MEDICATION, never as two separate
 * cancel-all-then-reschedule-all phases. If the process is killed mid-run (common on
 * aggressive OEMs like Samsung), only the medication currently being processed is
 * ever at risk of having zero alarms — every medication already processed keeps
 * its alarms. The old two-phase design could leave EVERY medication with zero
 * alarms forever if the worker died between phase 1 and phase 2.
 *
 * Runs as expedited work so the OS treats it with high priority instead of
 * deferring it under Doze/battery restrictions.
 */
class RescheduleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val KEY_CANCEL_FIRST = "cancel_first"

        fun enqueue(context: Context, cancelFirst: Boolean = false) {
            val data = Data.Builder()
                .putBoolean(KEY_CANCEL_FIRST, cancelFirst)
                .build()
            val request = OneTimeWorkRequestBuilder<RescheduleWorker>()
                .setInputData(data)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }

    override suspend fun doWork(): Result {
        val cancelFirst = inputData.getBoolean(KEY_CANCEL_FIRST, false)
        val repo = MedicationRepository(applicationContext)
        val medications = repo.getActiveMedications()

        // Cancel + reschedule ATOMICALLY per medication — never leave a gap where
        // alarms are cancelled for everyone before any are rescheduled.
        for (med in medications) {
            try {
                if (cancelFirst) {
                    AlarmScheduler.cancelAlarms(applicationContext, med.id)
                }
                val schedule = repo.getSchedule(med.id) ?: continue
                AlarmScheduler.scheduleAlarms(applicationContext, med, schedule)
            } catch (_: Exception) {
                // Continue with other medications even if one fails
            }
        }

        return Result.success()
    }
}
