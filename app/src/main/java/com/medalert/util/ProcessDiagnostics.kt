package com.medalert.util

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.medalert.data.repository.MedicationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Writes process lifecycle facts into the audit log so overnight misses can be diagnosed
 * from the phone alone: when the process came up, and (API 30+) why the previous one died,
 * stamped at the real exit time so it lands in the right place in the timeline.
 */
object ProcessDiagnostics {

    private const val PREFS = "process_diagnostics"
    private const val KEY_LAST_EXIT_SEEN = "last_exit_seen"

    fun record(context: Context) {
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = MedicationRepository(app)
                repo.logAudit(-1L, "מערכת", System.currentTimeMillis(), "APP_STARTED")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) logExitReasons(app, repo)
            } catch (e: Exception) {
                android.util.Log.e("ProcessDiagnostics", "record failed", e)
            }
        }
    }

    private suspend fun logExitReasons(context: Context, repo: MedicationRepository) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastSeen = prefs.getLong(KEY_LAST_EXIT_SEEN, 0L)
        val am = context.getSystemService(ActivityManager::class.java)
        val exits = am.getHistoricalProcessExitReasons(context.packageName, 0, 20)
            .filter { it.timestamp > lastSeen }
            .sortedBy { it.timestamp }
        for (exit in exits) {
            val note = buildString {
                append(reasonName(exit.reason))
                exit.description?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
            }
            repo.logAudit(-1L, "מערכת", exit.timestamp, "PROCESS_EXIT", note, timestamp = exit.timestamp)
        }
        exits.lastOrNull()?.let { prefs.edit().putLong(KEY_LAST_EXIT_SEEN, it.timestamp).apply() }
    }

    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_FREEZER -> "FREEZER"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        else -> "UNKNOWN($reason)"
    }
}
