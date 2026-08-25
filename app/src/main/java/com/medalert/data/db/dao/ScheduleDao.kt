package com.medalert.data.db.dao

import androidx.room.*
import com.medalert.data.db.entity.Schedule

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules WHERE medicationId = :medicationId")
    suspend fun getForMedication(medicationId: Long): List<Schedule>

    @Query("SELECT * FROM schedules")
    suspend fun getAll(): List<Schedule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(schedule: Schedule): Long

    @Update
    suspend fun update(schedule: Schedule)

    @Query("DELETE FROM schedules WHERE medicationId = :medicationId")
    suspend fun deleteForMedication(medicationId: Long)
}
