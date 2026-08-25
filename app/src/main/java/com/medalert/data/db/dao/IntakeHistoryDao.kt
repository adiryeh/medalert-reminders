package com.medalert.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.medalert.data.db.entity.IntakeHistory
import com.medalert.data.db.entity.IntakeStatus

@Dao
interface IntakeHistoryDao {
    @Query("SELECT * FROM intake_history WHERE medicationId = :medicationId ORDER BY scheduledDateTime DESC")
    fun getForMedicationLive(medicationId: Long): LiveData<List<IntakeHistory>>

    @Query("SELECT * FROM intake_history ORDER BY scheduledDateTime DESC")
    suspend fun getAll(): List<IntakeHistory>

    @Query("SELECT * FROM intake_history WHERE scheduledDateTime >= :from AND scheduledDateTime <= :to ORDER BY scheduledDateTime DESC")
    suspend fun getInRange(from: Long, to: Long): List<IntakeHistory>

    @Query("SELECT * FROM intake_history WHERE medicationId = :medicationId AND scheduledDateTime >= :todayStart AND scheduledDateTime <= :todayEnd")
    suspend fun getTodayForMedication(medicationId: Long, todayStart: Long, todayEnd: Long): List<IntakeHistory>

    @Query("SELECT * FROM intake_history WHERE medicationId = :medicationId AND scheduledDateTime = :scheduledTime LIMIT 1")
    suspend fun getEntry(medicationId: Long, scheduledTime: Long): IntakeHistory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: IntakeHistory): Long

    @Update
    suspend fun update(history: IntakeHistory)

    @Query("UPDATE intake_history SET status = 'MISSED' WHERE status = 'SNOOZED' AND scheduledDateTime < :cutoff")
    suspend fun markMissed(cutoff: Long)

    @Query("DELETE FROM intake_history WHERE medicationId = :medicationId AND scheduledDateTime = :scheduledTime")
    suspend fun deleteEntry(medicationId: Long, scheduledTime: Long)

    @Query("DELETE FROM intake_history WHERE id = :id")
    suspend fun deleteById(id: Long)
}
