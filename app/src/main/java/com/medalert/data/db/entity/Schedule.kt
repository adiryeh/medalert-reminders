package com.medalert.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "schedules",
    foreignKeys = [ForeignKey(
        entity = Medication::class,
        parentColumns = ["id"],
        childColumns = ["medicationId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("medicationId")]
)
data class Schedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val type: ScheduleType,
    val timesOfDay: String = "",      // JSON array of "HH:mm" strings
    val daysOfWeek: String = "",      // JSON array of ints 1-7
    val intervalDays: Int = 1,
    val cycleDaysOn: Int = 0,
    val cycleDaysOff: Int = 0,
    val startDate: Long = System.currentTimeMillis(),
    val endDate: Long = 0             // 0 = no end
)

enum class ScheduleType {
    DAILY,
    MULTIPLE_TIMES_DAILY,
    SPECIFIC_WEEKDAYS,
    EVERY_X_DAYS,
    SINGLE_DATE,
    COMPLEX_CYCLE
}
