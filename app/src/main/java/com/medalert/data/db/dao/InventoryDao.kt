package com.medalert.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.medalert.data.db.entity.Inventory

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory WHERE medicationId = :medicationId")
    suspend fun getForMedication(medicationId: Long): Inventory?

    @Query("SELECT * FROM inventory")
    fun getAllLive(): LiveData<List<Inventory>>

    @Query("SELECT * FROM inventory")
    suspend fun getAll(): List<Inventory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(inventory: Inventory)

    @Update
    suspend fun update(inventory: Inventory)
}
