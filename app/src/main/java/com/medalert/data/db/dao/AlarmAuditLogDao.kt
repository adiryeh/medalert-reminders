package com.medalert.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.medalert.data.db.entity.AlarmAuditLog

@Dao
interface AlarmAuditLogDao {

    @Insert
    suspend fun insert(log: AlarmAuditLog): Long

    @Query("SELECT * FROM alarm_audit_log WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getSince(since: Long): List<AlarmAuditLog>

    @Query("DELETE FROM alarm_audit_log WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("SELECT COUNT(*) FROM alarm_audit_log WHERE medicationId = :medicationId AND scheduledTime = :scheduledTime AND event = :event")
    suspend fun countMatching(medicationId: Long, scheduledTime: Long, event: String): Int
}
