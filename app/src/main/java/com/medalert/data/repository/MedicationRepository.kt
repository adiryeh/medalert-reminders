package com.medalert.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.medalert.data.db.AppDatabase
import com.medalert.data.db.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MedicationRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val medDao = db.medicationDao()
    private val scheduleDao = db.scheduleDao()
    private val settingsDao = db.reminderSettingsDao()
    private val inventoryDao = db.inventoryDao()
    private val historyDao = db.intakeHistoryDao()
    private val personDao = db.personDao()
    private val auditDao = db.alarmAuditLogDao()

    val allMedications = medDao.getAllLive()
    val allInventory = inventoryDao.getAllLive()
    val allPersons = personDao.getAllLive()

    // ── Medications ──────────────────────────────────────────────────────────
    suspend fun insertMedication(med: Medication, schedule: Schedule, settings: ReminderSettings, inventory: Inventory): Long {
        return withContext(Dispatchers.IO) {
            val id = medDao.insert(med)
            scheduleDao.insert(schedule.copy(medicationId = id))
            settingsDao.insert(settings.copy(medicationId = id))
            inventoryDao.insert(inventory.copy(medicationId = id))
            id
        }
    }

    suspend fun updateMedication(med: Medication) = withContext(Dispatchers.IO) { medDao.update(med) }
    suspend fun deleteMedication(med: Medication) = withContext(Dispatchers.IO) { medDao.delete(med) }
    suspend fun getMedication(id: Long) = withContext(Dispatchers.IO) { medDao.getById(id) }
    suspend fun getActiveMedications() = withContext(Dispatchers.IO) { medDao.getActive() }
    suspend fun getAllMedications() = withContext(Dispatchers.IO) { medDao.getAll() }

    /** Soft-delete: turns the medication off and cancels its alarms, but keeps all history/schedule data intact. */
    suspend fun deactivateMedication(context: android.content.Context, med: Medication) = withContext(Dispatchers.IO) {
        com.medalert.alarm.AlarmScheduler.cancelAlarms(context, med.id)
        com.medalert.alarm.AlarmScheduler.cancelMissedCheck(context, med.id)
        medDao.update(med.copy(active = false))
    }

    /** Reverses deactivateMedication: turns the medication back on and reschedules its alarms. */
    suspend fun reactivateMedication(context: android.content.Context, med: Medication) = withContext(Dispatchers.IO) {
        val reactivated = med.copy(active = true)
        medDao.update(reactivated)
        val schedule = scheduleDao.getForMedication(med.id).firstOrNull()
        if (schedule != null) {
            com.medalert.alarm.AlarmScheduler.scheduleAlarms(context, reactivated, schedule)
        } else {
            // Should never happen (a schedule is created alongside every medication), but if it
            // does the medication would otherwise come back "active" with zero alarms and no
            // visible sign anything is wrong — surface it in the audit log instead of a silent no-op.
            logAudit(med.id, med.name, System.currentTimeMillis(), "REACTIVATE_FAILED", "no schedule found")
        }
    }

    // ── Schedules ─────────────────────────────────────────────────────────────
    suspend fun getSchedule(medicationId: Long) = withContext(Dispatchers.IO) { scheduleDao.getForMedication(medicationId).firstOrNull() }
    suspend fun updateSchedule(schedule: Schedule) = withContext(Dispatchers.IO) { scheduleDao.insert(schedule) }

    // ── ReminderSettings ──────────────────────────────────────────────────────
    suspend fun getSettings(medicationId: Long) = withContext(Dispatchers.IO) { settingsDao.getForMedication(medicationId) }
    suspend fun updateSettings(settings: ReminderSettings) = withContext(Dispatchers.IO) { settingsDao.insert(settings) }

    // ── Inventory ─────────────────────────────────────────────────────────────
    suspend fun getInventory(medicationId: Long) = withContext(Dispatchers.IO) { inventoryDao.getForMedication(medicationId) }
    suspend fun updateInventory(inventory: Inventory) = withContext(Dispatchers.IO) { inventoryDao.insert(inventory) }
    suspend fun decrementInventory(medicationId: Long, context: android.content.Context? = null) = withContext(Dispatchers.IO) {
        val inv = inventoryDao.getForMedication(medicationId) ?: return@withContext
        val newQty = (inv.currentQuantity - inv.quantityPerDose).coerceAtLeast(0.0)
        inventoryDao.update(inv.copy(currentQuantity = newQty))
        // Fire low-stock alert when crossing the threshold (only once — when it first drops below)
        if (context != null &&
            newQty <= inv.alertThreshold &&
            inv.currentQuantity > inv.alertThreshold &&
            inv.alertThreshold > 0.0
        ) {
            val med = medDao.getById(medicationId)
            if (med != null) {
                val unitLabel = inv.unit.name.lowercase()
                com.medalert.notification.NotificationHelper.showInventoryAlert(
                    context, med.name, newQty, unitLabel
                )
            }
        }
    }

    // ── History ───────────────────────────────────────────────────────────────
    suspend fun logIntake(history: IntakeHistory) = withContext(Dispatchers.IO) { historyDao.insert(history) }
    suspend fun deleteHistoryEntry(medicationId: Long, scheduledTime: Long) = withContext(Dispatchers.IO) { historyDao.deleteEntry(medicationId, scheduledTime) }
    suspend fun deleteHistoryById(id: Long) = withContext(Dispatchers.IO) { historyDao.deleteById(id) }
    suspend fun getHistoryEntry(medicationId: Long, scheduledTime: Long) = withContext(Dispatchers.IO) { historyDao.getEntry(medicationId, scheduledTime) }
    suspend fun markMissedBefore(cutoff: Long) = withContext(Dispatchers.IO) { historyDao.markMissed(cutoff) }
    suspend fun getAllHistory() = withContext(Dispatchers.IO) { historyDao.getAll() }
    suspend fun getHistoryInRange(from: Long, to: Long) = withContext(Dispatchers.IO) { historyDao.getInRange(from, to) }

    // ── Audit Log ─────────────────────────────────────────────────────────────
    suspend fun logAudit(medicationId: Long, medicationName: String, scheduledTime: Long, event: String, note: String = "") {
        withContext(Dispatchers.IO) {
            auditDao.insert(com.medalert.data.db.entity.AlarmAuditLog(
                medicationId = medicationId,
                medicationName = medicationName,
                scheduledTime = scheduledTime,
                event = event,
                note = note
            ))
        }
    }

    suspend fun hasAuditEvent(medicationId: Long, scheduledTime: Long, event: String): Boolean =
        withContext(Dispatchers.IO) { auditDao.countMatching(medicationId, scheduledTime, event) > 0 }

    /** True if any delivery channel (local alarm, safety net, or FCM push) already fired for this dose. */
    suspend fun hasAnyFireEvent(medicationId: Long, scheduledTime: Long): Boolean = withContext(Dispatchers.IO) {
        auditDao.countMatching(medicationId, scheduledTime, "NOTIFICATION_SHOWN") > 0 ||
        auditDao.countMatching(medicationId, scheduledTime, "SAFETY_NET_FIRED") > 0 ||
        auditDao.countMatching(medicationId, scheduledTime, "FCM_FIRED") > 0
    }

    /**
     * Atomically checks hasAnyFireEvent() and, if nobody has claimed this dose yet, logs [event]
     * in the same DB transaction and returns true. Three independent channels (AlarmReceiver,
     * SafetyNetWorker, FcmReceiverService) can all wake up within the same window and race each
     * other; a plain check-then-insert (the old pattern) lets two of them both pass the check
     * before either writes, producing a duplicate notification. Callers should show the
     * notification only when this returns true.
     */
    suspend fun claimFire(medicationId: Long, medicationName: String, scheduledTime: Long, event: String, note: String = ""): Boolean =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                if (hasAnyFireEvent(medicationId, scheduledTime)) {
                    false
                } else {
                    auditDao.insert(AlarmAuditLog(
                        medicationId = medicationId,
                        medicationName = medicationName,
                        scheduledTime = scheduledTime,
                        event = event,
                        note = note
                    ))
                    true
                }
            }
        }

    suspend fun getRecentAuditLog(days: Int = 14): List<com.medalert.data.db.entity.AlarmAuditLog> {
        val since = System.currentTimeMillis() - days * 86_400_000L
        return withContext(Dispatchers.IO) { auditDao.getSince(since) }
    }

    suspend fun purgeOldAuditLog(daysToKeep: Int = 14) {
        val cutoff = System.currentTimeMillis() - daysToKeep * 86_400_000L
        withContext(Dispatchers.IO) { auditDao.deleteOlderThan(cutoff) }
    }

    // ── Persons ───────────────────────────────────────────────────────────────
    suspend fun insertPerson(person: Person) = withContext(Dispatchers.IO) { personDao.insert(person) }
    suspend fun updatePerson(person: Person) = withContext(Dispatchers.IO) { personDao.update(person) }
    suspend fun deletePerson(person: Person) = withContext(Dispatchers.IO) { personDao.delete(person) }
    suspend fun getAllPersons() = withContext(Dispatchers.IO) { personDao.getAll() }
    suspend fun getPersonById(id: Long) = withContext(Dispatchers.IO) { personDao.getById(id) }

    // ── Bulk for backup ────────────────────────────────────────────────────────
    suspend fun getAllSchedules() = withContext(Dispatchers.IO) { scheduleDao.getAll() }
    suspend fun getAllInventory() = withContext(Dispatchers.IO) { inventoryDao.getAll() }
    suspend fun getAllSettings() = withContext(Dispatchers.IO) { settingsDao.getAll() }

    suspend fun restoreFromBackup(data: com.medalert.backup.BackupData) = withContext(Dispatchers.IO) {
        db.clearAllTables()
        data.persons.forEach { personDao.insert(it) }
        data.medications.forEach { medDao.insert(it) }
        data.schedules.forEach { scheduleDao.insert(it) }
        data.reminderSettings.forEach { settingsDao.insert(it) }
        data.inventory.forEach { inventoryDao.insert(it) }
        data.history.forEach { historyDao.insert(it) }
    }
}
