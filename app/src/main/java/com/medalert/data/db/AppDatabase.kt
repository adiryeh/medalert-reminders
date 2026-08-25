package com.medalert.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.medalert.data.db.dao.*
import com.medalert.data.db.entity.*

class Converters {
    @TypeConverter fun fromScheduleType(v: ScheduleType) = v.name
    @TypeConverter fun toScheduleType(v: String) = ScheduleType.valueOf(v)
    @TypeConverter fun fromAlertPriority(v: AlertPriority) = v.name
    @TypeConverter fun toAlertPriority(v: String) = AlertPriority.valueOf(v)
    @TypeConverter fun fromInventoryUnit(v: InventoryUnit) = v.name
    @TypeConverter fun toInventoryUnit(v: String) = InventoryUnit.valueOf(v)
    @TypeConverter fun fromIntakeStatus(v: IntakeStatus) = v.name
    @TypeConverter fun toIntakeStatus(v: String) = IntakeStatus.valueOf(v)
}

@Database(
    entities = [Medication::class, Schedule::class, ReminderSettings::class,
                Inventory::class, IntakeHistory::class, Person::class,
                AlarmAuditLog::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun reminderSettingsDao(): ReminderSettingsDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun intakeHistoryDao(): IntakeHistoryDao
    abstract fun personDao(): PersonDao
    abstract fun alarmAuditLogDao(): AlarmAuditLogDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `persons` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `color` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("ALTER TABLE `medications` ADD COLUMN `personId` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `alarm_audit_log` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `medicationId` INTEGER NOT NULL,
                        `medicationName` TEXT NOT NULL,
                        `scheduledTime` INTEGER NOT NULL,
                        `event` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `note` TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "medalert.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { INSTANCE = it }
            }
    }
}
