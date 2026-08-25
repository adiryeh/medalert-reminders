package com.medalert.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Diagnostic log of every significant event in the alarm delivery chain.
 * Entries older than 14 days are auto-purged by IntegrityCheckWorker.
 *
 * event values:
 *   ALARM_SCHEDULED    — AlarmManager.setExact() called for this medication
 *   RECEIVER_FIRED     — AlarmReceiver.onReceive() executed
 *   NOTIFICATION_SHOWN — NotificationManager.notify() called successfully
 *   SAFETY_NET_FIRED   — SafetyNetWorker caught a miss and re-fired the notification
 *   ACTION_TAKEN       — User tapped ✅ from notification shade
 *   ACTION_SKIPPED     — User tapped ❌ from notification shade
 *   ACTION_SNOOZED     — User tapped ⏰ from notification shade
 */
@Entity(tableName = "alarm_audit_log")
data class AlarmAuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val medicationName: String,
    val scheduledTime: Long,
    val event: String,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = ""
)
