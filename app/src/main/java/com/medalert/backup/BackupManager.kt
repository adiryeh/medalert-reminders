package com.medalert.backup

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.medalert.data.db.entity.*
import com.medalert.data.repository.MedicationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class BackupData(
    val version: Int = 2,
    val exportedAt: Long = System.currentTimeMillis(),
    val persons: List<Person> = emptyList(),
    val medications: List<Medication>,
    val schedules: List<Schedule>,
    val reminderSettings: List<ReminderSettings>,
    val inventory: List<Inventory>,
    val history: List<IntakeHistory>
)

data class BackupSummary(
    val medications: Int,
    val schedules: Int,
    val historyEntries: Int,
    val persons: Int,
    val exportedAt: Long
)

object BackupManager {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private const val MAX_AUTO_BACKUPS = 5

    fun generateFileName(): String {
        val sdf = SimpleDateFormat("yyyy_MM_dd", Locale.US)
        return "MedicationBackup_${sdf.format(Date())}.json"
    }

    suspend fun exportToUri(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val repo = MedicationRepository(context)
            val data = BackupData(
                persons = repo.getAllPersons(),
                medications = repo.getAllMedications(),
                schedules = repo.getAllSchedules(),
                reminderSettings = repo.getAllSettings(),
                inventory = repo.getAllInventory(),
                history = repo.getAllHistory()
            )
            val json = gson.toJson(data)
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                ?: return@withContext Result.failure(Exception("לא ניתן לפתוח קובץ"))
            Result.success(json)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun autoBackup(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val repo = MedicationRepository(context)
            val data = BackupData(
                persons = repo.getAllPersons(),
                medications = repo.getAllMedications(),
                schedules = repo.getAllSchedules(),
                reminderSettings = repo.getAllSettings(),
                inventory = repo.getAllInventory(),
                history = repo.getAllHistory()
            )
            val json = gson.toJson(data)
            val dir = File(context.filesDir, "backups").also { it.mkdirs() }
            File(dir, generateFileName()).writeText(json)

            val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return@withContext true
            files.drop(MAX_AUTO_BACKUPS).forEach { it.delete() }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun readSummaryFromUri(context: Context, uri: Uri): Result<BackupSummary> = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: return@withContext Result.failure(Exception("לא ניתן לקרוא קובץ"))
            val data = gson.fromJson(json, BackupData::class.java)
                ?: return@withContext Result.failure(Exception("קובץ לא תקין"))
            Result.success(BackupSummary(
                medications = data.medications.size,
                schedules = data.schedules.size,
                historyEntries = data.history.size,
                persons = data.persons.size,
                exportedAt = data.exportedAt
            ))
        } catch (e: Exception) {
            Result.failure(Exception("שגיאה בקריאת הקובץ: ${e.message}"))
        }
    }

    suspend fun restoreFromUri(context: Context, uri: Uri): Result<BackupSummary> = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: return@withContext Result.failure(Exception("לא ניתן לקרוא קובץ"))
            val data = gson.fromJson(json, BackupData::class.java)
                ?: return@withContext Result.failure(Exception("קובץ לא תקין"))

            val repo = MedicationRepository(context)
            repo.restoreFromBackup(data)

            Result.success(BackupSummary(
                medications = data.medications.size,
                schedules = data.schedules.size,
                historyEntries = data.history.size,
                persons = data.persons.size,
                exportedAt = data.exportedAt
            ))
        } catch (e: Exception) {
            Result.failure(Exception("שגיאה בשחזור: ${e.message}"))
        }
    }

    fun listAutoBackups(context: Context): List<File> {
        val dir = File(context.filesDir, "backups")
        return dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
}
