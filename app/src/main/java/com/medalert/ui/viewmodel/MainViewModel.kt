package com.medalert.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.medalert.data.db.entity.*
import com.medalert.data.repository.MedicationRepository
import com.medalert.util.occurrencesInRange
import kotlinx.coroutines.launch
import java.util.Calendar

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = MedicationRepository(application)
    val medications: LiveData<List<Medication>> = repo.allMedications

    private val _todaySummary = MutableLiveData<TodaySummary>()
    val todaySummary: LiveData<TodaySummary> = _todaySummary

    private val _overdueAlerts = MutableLiveData<List<AlertItem>>()
    val overdueAlerts: LiveData<List<AlertItem>> = _overdueAlerts

    private val _todayDoneAlerts = MutableLiveData<List<AlertItem>>()
    val todayDoneAlerts: LiveData<List<AlertItem>> = _todayDoneAlerts

    private val _todayPendingAlerts = MutableLiveData<List<AlertItem>>()
    val todayPendingAlerts: LiveData<List<AlertItem>> = _todayPendingAlerts

    private val _upcomingAlerts = MutableLiveData<List<AlertItem>>()
    val upcomingAlerts: LiveData<List<AlertItem>> = _upcomingAlerts

    fun loadAll() {
        viewModelScope.launch { loadSummaryAndAlerts() }
    }

    private suspend fun loadSummaryAndAlerts() {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()

        cal.timeInMillis = now
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val todayStart = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
        val todayEnd = cal.timeInMillis

        val dayAgo    = now - 24 * 3600_000L
        val weekAhead = now + 7 * 24 * 3600_000L

        val allMedications = repo.getActiveMedications()
        val history = repo.getHistoryInRange(dayAgo, weekAhead)

        // Build person map for color/name lookup
        val personMap = repo.getAllPersons().associateBy { it.id }

        // Today summary
        val todayHistory = history.filter { it.scheduledDateTime in todayStart..todayEnd }
        val taken   = todayHistory.count { it.status == IntakeStatus.TAKEN }
        val skipped = todayHistory.count { it.status == IntakeStatus.SKIPPED }
        val missed  = todayHistory.count { it.status == IntakeStatus.MISSED }

        var todayMedCount = 0
        for (med in allMedications) {
            val sch = repo.getSchedule(med.id) ?: continue
            if (occurrencesInRange(sch, todayStart, todayEnd).isNotEmpty()) todayMedCount++
        }
        val pending = (todayMedCount - taken - skipped - missed).coerceAtLeast(0)
        _todaySummary.postValue(TodaySummary(todayMedCount, taken, pending, missed))

        val overdue      = mutableListOf<AlertItem>()
        val todayDone    = mutableListOf<AlertItem>()
        val todayPending = mutableListOf<AlertItem>()
        val upcoming     = mutableListOf<AlertItem>()

        for (med in allMedications) {
            val schedule = repo.getSchedule(med.id) ?: continue
            val person = if (med.personId != 0L) personMap[med.personId] else null
            val displayColor = person?.color ?: med.color
            val personName = person?.name

            // Past (last 24h → now): overdue or done
            // "טופל היום" = only times from todayStart onward
            // "לא טופלו"  = any unhandled time in the last 24h (including yesterday evening)
            val pastTimes = occurrencesInRange(schedule, dayAgo, now)
            for (time in pastTimes) {
                val entry = history.find {
                    it.medicationId == med.id &&
                    kotlin.math.abs(it.scheduledDateTime - time) < 120_000
                }
                val isHandled = entry?.status == IntakeStatus.TAKEN ||
                                entry?.status == IntakeStatus.SKIPPED
                if (isHandled) {
                    // Only show in "טופל היום" if the scheduled time is today
                    if (time >= todayStart) {
                        val state = if (entry!!.status == IntakeStatus.TAKEN) AlertItemState.TAKEN else AlertItemState.SKIPPED
                        todayDone.add(AlertItem(med, time, state, displayColor, personName))
                    }
                    // Yesterday's handled items: silently omit (history screen has them)
                } else {
                    // Unhandled: show in overdue regardless of day
                    overdue.add(AlertItem(med, time, AlertItemState.OVERDUE, displayColor, personName))
                }
            }

            // Today future (now → end of today): "ממתין להיום" — pinned section
            val todayFutureTimes = occurrencesInRange(schedule, now, todayEnd)
            for (time in todayFutureTimes) {
                todayPending.add(AlertItem(med, time, AlertItemState.FUTURE, displayColor, personName))
            }

            // Rest of week (tomorrow → 7 days): scrollable section
            val futureTimes = occurrencesInRange(schedule, todayEnd, weekAhead)
            for (time in futureTimes) {
                upcoming.add(AlertItem(med, time, AlertItemState.FUTURE, displayColor, personName))
            }
        }

        _overdueAlerts.postValue(overdue.sortedByDescending { it.scheduledTime })
        _todayDoneAlerts.postValue(todayDone.sortedByDescending { it.scheduledTime })
        _todayPendingAlerts.postValue(todayPending.sortedBy { it.scheduledTime })
        _upcomingAlerts.postValue(upcoming.sortedBy { it.scheduledTime })
    }

    fun markTaken(item: AlertItem) {
        viewModelScope.launch {
            repo.logIntake(IntakeHistory(
                medicationId = item.medication.id,
                scheduledDateTime = item.scheduledTime,
                actualDateTime = System.currentTimeMillis(),
                status = IntakeStatus.TAKEN
            ))
            repo.decrementInventory(item.medication.id)
            loadSummaryAndAlerts()
        }
    }

    fun markSkipped(item: AlertItem) {
        viewModelScope.launch {
            repo.logIntake(IntakeHistory(
                medicationId = item.medication.id,
                scheduledDateTime = item.scheduledTime,
                actualDateTime = System.currentTimeMillis(),
                status = IntakeStatus.SKIPPED
            ))
            loadSummaryAndAlerts()
        }
    }

    fun undoAction(item: AlertItem) {
        viewModelScope.launch {
            if (item.state == AlertItemState.TAKEN) {
                val inv = repo.getInventory(item.medication.id)
                if (inv != null) {
                    repo.updateInventory(inv.copy(currentQuantity = inv.currentQuantity + inv.quantityPerDose))
                }
            }
            repo.deleteHistoryEntry(item.medication.id, item.scheduledTime)
            loadSummaryAndAlerts()
        }
    }

    fun deleteMedication(medication: Medication) {
        viewModelScope.launch { repo.deleteMedication(medication) }
    }
}

data class TodaySummary(val total: Int, val completed: Int, val pending: Int, val missed: Int)

data class AlertItem(
    val medication: Medication,
    val scheduledTime: Long,
    val state: AlertItemState,
    val displayColor: Int,
    val personName: String?
)

enum class AlertItemState { OVERDUE, TAKEN, SKIPPED, FUTURE }
