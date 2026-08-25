package com.medalert.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminder_settings",
    foreignKeys = [ForeignKey(
        entity = Medication::class,
        parentColumns = ["id"],
        childColumns = ["medicationId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ReminderSettings(
    @PrimaryKey val medicationId: Long,
    val soundEnabled: Boolean = true,
    val soundUri: String = "",
    val vibrateEnabled: Boolean = true,
    val priority: AlertPriority = AlertPriority.NORMAL,
    val durationSeconds: Int = 60,
    val snoozeIntervals: String = "5,15,30",   // comma separated minutes
    val maxSnoozeAttempts: Int = 3,
    val untilConfirmed: Boolean = false
)

enum class AlertPriority { NORMAL, HIGH, CRITICAL }
