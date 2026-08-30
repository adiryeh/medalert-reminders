package com.medalert.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.medalert.data.db.entity.*
import com.medalert.util.nextOccurrences
import java.util.Calendar

object AlarmScheduler {

    private const val MAX_FUTURE_ALARMS = 100

    // PendingIntent request codes are namespaced per medication: medicationId * REQUEST_CODE_SLOT_SIZE + offset.
    // Regular dose alarms use offsets 0..MAX_FUTURE_ALARMS-1 (index into the occurrence list);
    // the fixed offsets below are reserved slots for the other alarm types on that same medication.
    // These values are unchanged from before this was named — do not renumber, or existing
    // scheduled PendingIntents on devices already running an older build won't match anymore.
    private const val REQUEST_CODE_SLOT_SIZE     = 10000
    private const val OFFSET_MISSED_CHECK        = 9998
    private const val OFFSET_SNOOZE_OR_LAST_DOSE = 9999

    private fun requestCode(medicationId: Long, offset: Int) = (medicationId * REQUEST_CODE_SLOT_SIZE + offset).toInt()

    /**
     * Schedules one alarm using setAlarmClock() — the strongest wake guarantee AlarmManager
     * offers. Unlike setExactAndAllowWhileIdle(), OEMs (Samsung included) are far less willing
     * to kill the process behind a pending "alarm clock" entry, because their own Clock app
     * relies on the same contract. It also does not require the user-revocable
     * SCHEDULE_EXACT_ALARM permission. The tradeoff is a permanent alarm-clock icon in the
     * status bar while any alarm is pending — accepted deliberately after repeated total
     * outages caused by Samsung killing the process despite every other exemption being granted.
     *
     * showIntent controls what opens when the user taps that status bar icon — set to MainActivity.
     */
    private fun AlarmManager.setAlarmClockSafe(context: Context, triggerAt: Long, pi: PendingIntent) {
        val showIntent = PendingIntent.getActivity(
            context, 0, Intent(context, com.medalert.ui.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), pi)
    }

    fun scheduleAlarms(context: Context, medication: Medication, schedule: Schedule) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // A schedule change invalidates any pending missed-check tied to the old scheduledTime
        // it was watching — otherwise it can fire later referencing a dose time that no longer
        // matches this medication's current schedule.
        cancelMissedCheck(context, medication.id)
        val occurrences = schedule.nextOccurrences(MAX_FUTURE_ALARMS)
        occurrences.forEachIndexed { index, timeMillis ->
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("medication_id", medication.id)
                putExtra("medication_name", medication.name)
                putExtra("medication_dosage", medication.dosage)
                putExtra("scheduled_time", timeMillis)
            }
            val requestCode = requestCode(medication.id, index)
            val pi = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAlarmClockSafe(context, timeMillis, pi)
        }
        // Last-resort backup channel: push the same (anonymized) occurrence list to Firestore
        // so an external GitHub Action can send an FCM push if this device's local alarms
        // and SafetyNetWorker both fail to fire. No medication name/dosage ever leaves the device.
        com.medalert.fcm.FcmSync.pushOccurrences(context, medication.id, occurrences)
    }

    fun cancelAlarms(context: Context, medicationId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (i in 0 until MAX_FUTURE_ALARMS) {
            val intent = Intent(context, AlarmReceiver::class.java)
            val requestCode = requestCode(medicationId, i)
            val pi = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pi?.let { alarmManager.cancel(it) }
        }
        com.medalert.fcm.FcmSync.markInactive(context, medicationId)
    }

    /** Schedule a missed-check alarm DELAY_MINUTES after the original scheduled time. */
    fun scheduleMissedCheck(
        context: Context,
        medicationId: Long,
        medicationName: String,
        scheduledTime: Long,
        delayMinutes: Int = 20
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = scheduledTime + delayMinutes * 60_000L
        val intent = Intent(context, MissedCheckReceiver::class.java).apply {
            putExtra(MissedCheckReceiver.EXTRA_MED_ID, medicationId)
            putExtra(MissedCheckReceiver.EXTRA_MED_NAME, medicationName)
            putExtra(MissedCheckReceiver.EXTRA_SCHEDULED_TIME, scheduledTime)
        }
        val requestCode = requestCode(medicationId, OFFSET_MISSED_CHECK)
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAlarmClockSafe(context, triggerAt, pi)
    }

    /** Returns true if a snooze alarm is already pending for this medication. */
    fun isSnoozeScheduled(context: Context, medicationId: Long): Boolean {
        val intent = Intent(context, AlarmReceiver::class.java)
        val requestCode = requestCode(medicationId, OFFSET_SNOOZE_OR_LAST_DOSE)
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        return pi != null
    }

    fun cancelMissedCheck(context: Context, medicationId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, MissedCheckReceiver::class.java)
        val requestCode = requestCode(medicationId, OFFSET_MISSED_CHECK)
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pi?.let { alarmManager.cancel(it) }
    }

    fun scheduleSnooze(
        context: Context,
        medicationId: Long,
        medicationName: String,
        dosage: String,
        snoozeMinutes: Int,
        originalScheduledTime: Long   // always the ORIGINAL alarm time, preserved across multiple snoozes
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + snoozeMinutes * 60_000L
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("medication_id", medicationId)
            putExtra("medication_name", medicationName)
            putExtra("medication_dosage", dosage)
            putExtra("scheduled_time", originalScheduledTime)  // ← original time, NOT triggerAt
            putExtra("is_snooze", true)
        }
        val requestCode = requestCode(medicationId, OFFSET_SNOOZE_OR_LAST_DOSE)
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAlarmClockSafe(context, triggerAt, pi)
    }
}
