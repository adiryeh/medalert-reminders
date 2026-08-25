package com.medalert.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "inventory",
    foreignKeys = [ForeignKey(
        entity = Medication::class,
        parentColumns = ["id"],
        childColumns = ["medicationId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Inventory(
    @PrimaryKey val medicationId: Long,
    val currentQuantity: Double = 0.0,
    val quantityPerDose: Double = 1.0,
    val alertThreshold: Double = 10.0,
    val unit: InventoryUnit = InventoryUnit.PILLS
)

enum class InventoryUnit { PILLS, CAPSULES, DROPS, ML, INJECTIONS, OTHER }
