package com.medalert.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.medalert.alarm.AlarmScheduler
import com.medalert.data.db.entity.*
import com.medalert.data.repository.MedicationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddEditMedicationViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = MedicationRepository(application)

    val medication = MutableLiveData(Medication(name = ""))
    val schedule   = MutableLiveData(Schedule(medicationId = 0, type = ScheduleType.DAILY, timesOfDay = "[\"08:00\"]"))
    val settings   = MutableLiveData(ReminderSettings(medicationId = 0))
    val inventory  = MutableLiveData(Inventory(medicationId = 0))
    val isEditMode = MutableLiveData(false)
    val persons    = MutableLiveData<List<Person>>(emptyList())

    init {
        viewModelScope.launch {
            persons.postValue(repo.getAllPersons())
        }
    }

    fun loadMedication(id: Long) {
        viewModelScope.launch {
            val med = repo.getMedication(id) ?: return@launch
            val sch = repo.getSchedule(id) ?: Schedule(medicationId = id, type = ScheduleType.DAILY, timesOfDay = "[\"08:00\"]")
            val set = repo.getSettings(id) ?: ReminderSettings(medicationId = id)
            val inv = repo.getInventory(id) ?: Inventory(medicationId = id)
            withContext(Dispatchers.Main) {
                isEditMode.value = true   // set FIRST so observers see edit mode
                medication.value = med
                schedule.value = sch
                settings.value = set
                inventory.value = inv
            }
        }
    }

    fun saveMedication(onDone: () -> Unit, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val med = medication.value ?: return@launch
                val sch = schedule.value ?: return@launch
                val set = settings.value ?: return@launch
                val inv = inventory.value ?: return@launch

                val savedId: Long
                if (isEditMode.value == true) {
                    repo.updateMedication(med)
                    repo.updateSchedule(sch.copy(medicationId = med.id))
                    repo.updateSettings(set.copy(medicationId = med.id))
                    repo.updateInventory(inv.copy(medicationId = med.id))
                    savedId = med.id
                } else {
                    savedId = repo.insertMedication(med, sch, set, inv)
                }

                val savedMed = med.copy(id = savedId)
                val savedSch = sch.copy(medicationId = savedId)

                withContext(Dispatchers.Main) {
                    try {
                        AlarmScheduler.cancelAlarms(getApplication(), savedId)
                        AlarmScheduler.scheduleAlarms(getApplication(), savedMed, savedSch)
                    } catch (e: SecurityException) {
                        onError("התרופה נשמרה, אך לא ניתן לקבוע תזכורות ללא הרשאת 'אזעקות מדויקות'.")
                    }
                    onDone()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("שגיאה בשמירה: ${e.message}")
                }
            }
        }
    }
}
