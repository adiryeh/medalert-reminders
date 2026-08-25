package com.medalert.data.db.dao

import androidx.room.*
import com.medalert.data.db.entity.ReminderSettings

@Dao
interface ReminderSettingsDao {
    @Query("SELECT * FROM reminder_settings WHERE medicationId = :medicationId")
    suspend fun getForMedication(medicationId: Long): ReminderSettings?

    @Query("SELECT * FROM reminder_settings")
    suspend fun getAll(): List<ReminderSettings>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(settings: ReminderSettings)

    @Update
    suspend fun update(settings: ReminderSettings)
}
