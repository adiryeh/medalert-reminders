package com.medalert.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.medalert.data.db.entity.Person

@Dao
interface PersonDao {
    @Query("SELECT * FROM persons ORDER BY name ASC")
    fun getAllLive(): LiveData<List<Person>>

    @Query("SELECT * FROM persons ORDER BY name ASC")
    suspend fun getAll(): List<Person>

    @Query("SELECT * FROM persons WHERE id = :id")
    suspend fun getById(id: Long): Person?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(person: Person): Long

    @Update
    suspend fun update(person: Person)

    @Delete
    suspend fun delete(person: Person)
}
