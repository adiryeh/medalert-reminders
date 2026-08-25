package com.medalert.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "persons")
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int = 0xFF2196F3.toInt()
)

/** 8 predefined palette colors for person assignment */
object PersonColors {
    val PALETTE = listOf(
        0xFF2196F3.toInt(),  // כחול
        0xFF4CAF50.toInt(),  // ירוק
        0xFFF44336.toInt(),  // אדום
        0xFFFF9800.toInt(),  // כתום
        0xFF9C27B0.toInt(),  // סגול
        0xFF009688.toInt(),  // טורקיז
        0xFFE91E63.toInt(),  // ורוד
        0xFF795548.toInt()   // חום
    )

    val NAMES = listOf("כחול", "ירוק", "אדום", "כתום", "סגול", "טורקיז", "ורוד", "חום")

    val DEFAULT = 0xFF9E9E9E.toInt()  // אפור — ללא שיוך
}
