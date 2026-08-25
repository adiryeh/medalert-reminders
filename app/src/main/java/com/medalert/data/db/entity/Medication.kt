package com.medalert.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dosage: String = "",
    val notes: String = "",
    val color: Int = PersonColors.DEFAULT,  // fallback when no person
    val active: Boolean = true,
    val personId: Long = 0   // 0 = ללא שיוך לאדם ספציפי
)
