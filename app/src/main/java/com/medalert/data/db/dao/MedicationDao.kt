package com.medalert.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.medalert.data.db.entity.Medication

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications ORDER BY name ASC")
    fun getAllLive(): LiveData<List<Medication>>

    @Query("SELECT * FROM medications ORDER BY name ASC")
    suspend fun getAll(): List<Medication>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getById(id: Long): Medication?

    @Query("SELECT * FROM medications WHERE active = 1")
    suspend fun getActive(): List<Medication>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(medication: Medication): Long

    @Update
    suspend fun update(medication: Medication)

    @Delete
    suspend fun delete(medication: Medication)
}
