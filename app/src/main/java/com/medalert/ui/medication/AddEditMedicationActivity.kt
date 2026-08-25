package com.medalert.ui.medication

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.medalert.data.db.entity.AlertPriority
import com.medalert.data.db.entity.Person
import com.medalert.data.db.entity.PersonColors
import com.medalert.data.db.entity.ReminderSettings
import com.medalert.data.db.entity.ScheduleType
import com.medalert.databinding.ActivityAddEditMedicationBinding
import com.medalert.ui.viewmodel.AddEditMedicationViewModel
import org.json.JSONArray

class AddEditMedicationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEditMedicationBinding
    private val viewModel: AddEditMedicationViewModel by viewModels()

    /** "ללא שיוך" + all persons from DB */
    private var personList: List<Person?> = listOf(null)

    /** Selected ringtone URI; null = silent */
    private var selectedSoundUri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    private val ringtoneLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            selectedSoundUri = uri
            updateSoundLabel()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditMedicationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupScheduleTypeSpinner()
        setupAlertSpinners()
        setupSoundPicker()
        binding.btnSave.setOnClickListener { save() }
        binding.btnCancel.setOnClickListener { finish() }

        val medicationId = intent.getLongExtra("medication_id", -1L)
        if (medicationId != -1L) viewModel.loadMedication(medicationId)

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.persons.observe(this) { persons ->
            personList = listOf(null) + persons   // null = "ללא שיוך"
            val names = personList.map { it?.name ?: "ללא שיוך" }
            binding.spinnerPerson.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            binding.spinnerPerson.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                    val color = personList[pos]?.color ?: PersonColors.DEFAULT
                    binding.personColorDot.setBackgroundColor(color)
                }
                override fun onNothingSelected(p: AdapterView<*>) {}
            }

            // In edit mode, select the current person
            if (viewModel.isEditMode.value == true) {
                val currentPersonId = viewModel.medication.value?.personId ?: 0L
                val idx = personList.indexOfFirst { it?.id == currentPersonId }.coerceAtLeast(0)
                binding.spinnerPerson.setSelection(idx)
            }
        }

        viewModel.medication.observe(this) { med ->
            if (viewModel.isEditMode.value == true) {
                binding.etName.setText(med.name)
                binding.etDosage.setText(med.dosage)
                binding.etNotes.setText(med.notes)
                // Set person spinner
                val idx = personList.indexOfFirst { it?.id == med.personId }.coerceAtLeast(0)
                binding.spinnerPerson.setSelection(idx)
                val color = personList.getOrNull(idx)?.color ?: PersonColors.DEFAULT
                binding.personColorDot.setBackgroundColor(color)
            }
        }

        viewModel.schedule.observe(this) { sch ->
            if (viewModel.isEditMode.value == true) {
                binding.spinnerScheduleType.setSelection(sch.type.ordinal)
                updateVisibility(sch.type)
                try {
                    val arr = JSONArray(sch.timesOfDay)
                    if (arr.length() > 0) binding.etTime.setText(arr.getString(0))
                    if (arr.length() > 1) binding.etTime2.setText(arr.getString(1))
                    if (arr.length() > 2) binding.etTime3.setText(arr.getString(2))
                } catch (_: Exception) {}
                try {
                    val days = JSONArray(sch.daysOfWeek)
                    val daySet = (0 until days.length()).map { days.getInt(it) }.toSet()
                    binding.cbSun.isChecked = 1 in daySet; binding.cbMon.isChecked = 2 in daySet
                    binding.cbTue.isChecked = 3 in daySet; binding.cbWed.isChecked = 4 in daySet
                    binding.cbThu.isChecked = 5 in daySet; binding.cbFri.isChecked = 6 in daySet
                    binding.cbSat.isChecked = 7 in daySet
                } catch (_: Exception) {}
                if (sch.intervalDays > 1) binding.etIntervalDays.setText(sch.intervalDays.toString())
                if (sch.cycleDaysOn > 0) binding.etCycleOn.setText(sch.cycleDaysOn.toString())
                if (sch.cycleDaysOff > 0) binding.etCycleOff.setText(sch.cycleDaysOff.toString())
            }
        }

        viewModel.settings.observe(this) { set ->
            if (viewModel.isEditMode.value == true) {
                // Restore sound URI
                selectedSoundUri = if (set.soundEnabled) {
                    if (set.soundUri.isNullOrEmpty()) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    else Uri.parse(set.soundUri)
                } else null
                updateSoundLabel()
                binding.switchVibrate.isChecked = set.vibrateEnabled
                binding.spinnerPriority.setSelection(if (set.priority == AlertPriority.CRITICAL) 1 else 0)
                val snoozeOptions = listOf("5,15,30", "5,15", "15,30", "30")
                binding.spinnerSnooze.setSelection(snoozeOptions.indexOf(set.snoozeIntervals).coerceAtLeast(0))
            }
        }
    }

    private fun setupScheduleTypeSpinner() {
        val types = listOf("יומי", "מספר פעמים ביום", "ימים ספציפיים", "כל X ימים", "תאריך בודד", "מחזור מורכב")
        binding.spinnerScheduleType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, types).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerScheduleType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                updateVisibility(ScheduleType.values()[pos])
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }
    }

    private fun setupSoundPicker() {
        updateSoundLabel()
        binding.btnPickSound.setOnClickListener {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "בחר צליל התראה")
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, selectedSoundUri)
            }
            ringtoneLauncher.launch(intent)
        }
        binding.btnSilentSound.setOnClickListener {
            selectedSoundUri = null
            updateSoundLabel()
        }
    }

    private fun updateSoundLabel() {
        val label = when {
            selectedSoundUri == null -> "🔕 שקט"
            selectedSoundUri == RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) -> "ברירת מחדל"
            else -> RingtoneManager.getRingtone(this, selectedSoundUri)?.getTitle(this) ?: "צליל נבחר"
        }
        binding.tvSoundName.text = label
    }

    private fun setupAlertSpinners() {
        binding.spinnerSound.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf("שקט", "צליל מערכת")).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerSound.setSelection(1)

        binding.spinnerPriority.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf("רגיל", "קריטי")).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        binding.spinnerSnooze.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            listOf("אחרי 5, 15, 30 דקות", "אחרי 5, 15 דקות", "אחרי 15, 30 דקות", "אחרי 30 דקות בלבד")).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun updateVisibility(type: ScheduleType) {
        binding.layoutExtraTimes.visibility = if (type == ScheduleType.MULTIPLE_TIMES_DAILY) View.VISIBLE else View.GONE
        binding.layoutDaysOfWeek.visibility  = if (type == ScheduleType.SPECIFIC_WEEKDAYS)    View.VISIBLE else View.GONE
        binding.layoutInterval.visibility    = if (type == ScheduleType.EVERY_X_DAYS)          View.VISIBLE else View.GONE
        binding.layoutCycle.visibility       = if (type == ScheduleType.COMPLEX_CYCLE)         View.VISIBLE else View.GONE
    }

    private fun save() {
        val name = binding.etName.text.toString().trim()
        if (name.isEmpty()) { binding.etName.error = "שם חובה"; return }

        val type = ScheduleType.values()[binding.spinnerScheduleType.selectedItemPosition]
        if (type == ScheduleType.SPECIFIC_WEEKDAYS && !anyDayChecked()) {
            Toast.makeText(this, "יש לבחור לפחות יום אחד", Toast.LENGTH_SHORT).show(); return
        }

        val selectedPerson = personList.getOrNull(binding.spinnerPerson.selectedItemPosition)
        val personId = selectedPerson?.id ?: 0L
        val personColor = selectedPerson?.color ?: PersonColors.DEFAULT

        viewModel.medication.value = viewModel.medication.value!!.copy(
            name = name,
            dosage = binding.etDosage.text.toString().trim(),
            notes = binding.etNotes.text.toString().trim(),
            personId = personId,
            color = personColor
        )
        viewModel.schedule.value = viewModel.schedule.value!!.copy(
            type = type,
            timesOfDay = buildTimesJson(type),
            daysOfWeek = buildDaysJson(),
            intervalDays = binding.etIntervalDays.text.toString().toIntOrNull() ?: 1,
            cycleDaysOn  = binding.etCycleOn.text.toString().toIntOrNull() ?: 3,
            cycleDaysOff = binding.etCycleOff.text.toString().toIntOrNull() ?: 1
        )

        val snoozeValues = listOf("5,15,30", "5,15", "15,30", "30")
        viewModel.settings.value = ReminderSettings(
            medicationId   = viewModel.medication.value!!.id,
            soundEnabled   = selectedSoundUri != null,
            soundUri       = selectedSoundUri?.toString() ?: "",
            vibrateEnabled = binding.switchVibrate.isChecked,
            priority       = if (binding.spinnerPriority.selectedItemPosition == 1) AlertPriority.CRITICAL else AlertPriority.NORMAL,
            snoozeIntervals = snoozeValues[binding.spinnerSnooze.selectedItemPosition]
        )

        binding.btnSave.isEnabled = false
        viewModel.saveMedication(
            onDone = { finish() },
            onError = { msg ->
                runOnUiThread {
                    binding.btnSave.isEnabled = true
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun buildTimesJson(type: ScheduleType): String {
        val arr = JSONArray()
        arr.put(binding.etTime.text.toString().trim().ifEmpty { "08:00" })
        if (type == ScheduleType.MULTIPLE_TIMES_DAILY) {
            val t2 = binding.etTime2.text.toString().trim()
            val t3 = binding.etTime3.text.toString().trim()
            if (t2.isNotEmpty()) arr.put(t2)
            if (t3.isNotEmpty()) arr.put(t3)
        }
        return arr.toString()
    }

    private fun buildDaysJson(): String {
        val arr = JSONArray()
        if (binding.cbSun.isChecked) arr.put(1); if (binding.cbMon.isChecked) arr.put(2)
        if (binding.cbTue.isChecked) arr.put(3); if (binding.cbWed.isChecked) arr.put(4)
        if (binding.cbThu.isChecked) arr.put(5); if (binding.cbFri.isChecked) arr.put(6)
        if (binding.cbSat.isChecked) arr.put(7)
        return arr.toString()
    }

    private fun anyDayChecked() = listOf(
        binding.cbSun, binding.cbMon, binding.cbTue, binding.cbWed,
        binding.cbThu, binding.cbFri, binding.cbSat
    ).any { it.isChecked }
}
