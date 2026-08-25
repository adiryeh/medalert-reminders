package com.medalert.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.medalert.MedAlertApplication
import com.medalert.R
import com.medalert.alarm.NotificationActionReceiver
import com.medalert.data.db.entity.AlertPriority
import com.medalert.data.db.entity.ReminderSettings
import com.medalert.ui.alert.AlertActivity

object NotificationHelper {

    // Notification ID offset for missed-dose reminders (avoids collision with main alerts)
    private const val MISSED_ID_OFFSET = 200_000

    fun showMedicationAlert(
        context: Context,
        medicationId: Long,
        name: String,
        dosage: String,
        scheduledTime: Long,
        settings: ReminderSettings? = null
    ) {
        // ── Full-screen / tap intent ─────────────────────────────────────────
        val alertIntent = Intent(context, AlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("medication_id", medicationId)
            putExtra("medication_name", name)
            putExtra("medication_dosage", dosage)
            putExtra("scheduled_time", scheduledTime)
        }
        val tapPi = PendingIntent.getActivity(
            context, medicationId.toInt(), alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ── Action: ✅ נלקח ──────────────────────────────────────────────────
        val takenPi = actionPendingIntent(
            context, medicationId, scheduledTime, name, dosage,
            NotificationActionReceiver.ACTION_TAKEN,
            requestCode = (medicationId * 10000 + 1001).toInt()
        )

        // ── Action: ❌ דלג ───────────────────────────────────────────────────
        val skipPi = actionPendingIntent(
            context, medicationId, scheduledTime, name, dosage,
            NotificationActionReceiver.ACTION_SKIP,
            requestCode = (medicationId * 10000 + 1002).toInt()
        )

        // ── Action: ⏰ דחה 10 דקות ───────────────────────────────────────────
        val snoozePi = actionPendingIntent(
            context, medicationId, scheduledTime, name, dosage,
            NotificationActionReceiver.ACTION_SNOOZE,
            requestCode = (medicationId * 10000 + 1003).toInt(),
            snoozeMinutes = 10
        )

        // ── Priority / Channel ───────────────────────────────────────────────
        val notifPriority = if (settings?.priority == AlertPriority.CRITICAL)
            NotificationCompat.PRIORITY_MAX
        else
            NotificationCompat.PRIORITY_HIGH
        val channelId = if (settings?.priority == AlertPriority.CRITICAL)
            ensureCriticalChannel(context)
        else
            MedAlertApplication.CHANNEL_ALERTS

        // ── Build notification ───────────────────────────────────────────────
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle("💊 זמן לקחת תרופה")
            .setContentText("$name${if (dosage.isNotEmpty()) " — $dosage" else ""}")
            .setPriority(notifPriority)
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(tapPi)
            .setFullScreenIntent(tapPi, true)
            // Quick-action buttons visible in the notification shade
            .addAction(R.drawable.ic_pill, "✅ נלקח",  takenPi)
            .addAction(R.drawable.ic_pill, "❌ דלג",   skipPi)
            .addAction(R.drawable.ic_pill, "⏰ +10 דק׳", snoozePi)

        // Sound: use specific URI if set, default notification sound, or silent
        if (settings?.soundEnabled == false) {
            builder.setSilent(true)
        } else {
            val soundUri = if (!settings?.soundUri.isNullOrEmpty())
                Uri.parse(settings!!.soundUri)
            else
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            builder.setSound(soundUri)
        }

        if (settings?.vibrateEnabled == true || settings == null)
            builder.setVibrate(longArrayOf(0, 500, 200, 500))
        else
            builder.setVibrate(null)

        context.getSystemService(NotificationManager::class.java)
            .notify(medicationId.toInt(), builder.build())
    }

    /**
     * Quiet follow-up notification shown ~20 min after the original alarm
     * if the user hasn't responded yet.
     */
    fun showMissedReminder(context: Context, medicationId: Long, name: String) {
        val builder = NotificationCompat.Builder(context, MedAlertApplication.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle("⚠ לא סומן — $name")
            .setContentText("שכחת לקחת את $name?  לחץ לפתיחה")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setSilent(true)   // quiet — just a badge in the shade

        context.getSystemService(NotificationManager::class.java)
            .notify(medicationId.toInt() + MISSED_ID_OFFSET, builder.build())
    }

    fun showInventoryAlert(context: Context, name: String, quantity: Double, unit: String) {
        val notification = NotificationCompat.Builder(context, MedAlertApplication.CHANNEL_INVENTORY)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle("מלאי נמוך — $name")
            .setContentText("נותרו ${quantity.toInt()} $unit")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify((name.hashCode() + 50000), notification)
    }

    fun cancelAlert(context: Context, medicationId: Long) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(medicationId.toInt())
        manager.cancel(medicationId.toInt() + MISSED_ID_OFFSET)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun actionPendingIntent(
        context: Context,
        medicationId: Long,
        scheduledTime: Long,
        name: String,
        dosage: String,
        action: String,
        requestCode: Int,
        snoozeMinutes: Int = 10
    ): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(NotificationActionReceiver.EXTRA_MED_ID, medicationId)
            putExtra(NotificationActionReceiver.EXTRA_MED_NAME, name)
            putExtra(NotificationActionReceiver.EXTRA_MED_DOSAGE, dosage)
            putExtra(NotificationActionReceiver.EXTRA_SCHEDULED_TIME, scheduledTime)
            putExtra(NotificationActionReceiver.EXTRA_SNOOZE_MINUTES, snoozeMinutes)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureCriticalChannel(context: Context): String {
        val channelId = MedAlertApplication.CHANNEL_ALERTS_CRITICAL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(
                    channelId,
                    "התראות תרופה — קריטי",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "התראות קריטיות לנטילת תרופות"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                manager.createNotificationChannel(channel)
            }
        }
        return channelId
    }
}
