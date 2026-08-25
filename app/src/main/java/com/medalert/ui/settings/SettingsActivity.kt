package com.medalert.ui.settings

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.medalert.alarm.AlarmScheduler
import com.medalert.backup.BackupManager
import com.medalert.data.db.entity.Person
import com.medalert.data.repository.MedicationRepository
import com.medalert.databinding.ActivitySettingsBinding
import com.medalert.databinding.ItemMedicationBinding
import com.medalert.ui.medication.AddEditMedicationActivity
import com.medalert.ui.persons.PersonsActivity
import com.medalert.util.BiometricHelper
import com.medalert.util.PreferenceHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var repo: MedicationRepository

    private val createFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> doExport(uri) }
        }
    }

    private val openFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> showRestoreConfirmation(uri) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        repo = MedicationRepository(this)

        // Hardcode: 24h always on, auto-backup always on
        PreferenceHelper.set24HourFormat(this, true)
        PreferenceHelper.setAutoBackup(this, true)

        loadDarkModeSetting()
        loadBiometricSetting()
        setupListeners()
        refreshAutoBackupList()
    }

    override fun onResume() {
        super.onResume()
        loadMedications()
    }

    private fun loadBiometricSetting() {
        val available = BiometricHelper.isAvailable(this)
        binding.rowBiometric.visibility = if (available) android.view.View.VISIBLE else android.view.View.GONE
        if (available) {
            binding.switchBiometric.isChecked = PreferenceHelper.isBiometricEnabled(this)
            binding.switchBiometric.setOnCheckedChangeListener { _, isChecked ->
                PreferenceHelper.setBiometricEnabled(this, isChecked)
            }
        }
    }

    private fun loadDarkModeSetting() {
        binding.rgDarkMode.check(when (PreferenceHelper.getDarkMode(this)) {
            "dark"  -> com.medalert.R.id.rbDark
            "light" -> com.medalert.R.id.rbLight
            else    -> com.medalert.R.id.rbSystem
        })
    }

    private fun setupListeners() {
        binding.rgDarkMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                com.medalert.R.id.rbDark  -> "dark"
                com.medalert.R.id.rbLight -> "light"
                else -> "system"
            }
            PreferenceHelper.setDarkMode(this, mode)
            AppCompatDelegate.setDefaultNightMode(when (mode) {
                "dark"  -> AppCompatDelegate.MODE_NIGHT_YES
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                else    -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            })
        }

        binding.btnManagePersons.setOnClickListener {
            startActivity(Intent(this, PersonsActivity::class.java))
        }

        binding.btnAddMedication.setOnClickListener {
            startActivity(Intent(this, AddEditMedicationActivity::class.java))
        }

        binding.btnExport.setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, BackupManager.generateFileName())
            }
            createFileLauncher.launch(intent)
        }

        binding.btnImport.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
            }
            openFileLauncher.launch(intent)
        }

        binding.btnAuditLog.setOnClickListener {
            startActivity(Intent(this, AuditLogActivity::class.java))
        }

        binding.btnBackupNow.setOnClickListener {
            lifecycleScope.launch {
                binding.btnBackupNow.isEnabled = false
                val ok = BackupManager.autoBackup(this@SettingsActivity)
                binding.btnBackupNow.isEnabled = true
                if (ok) {
                    Toast.makeText(this@SettingsActivity, "✅ גיבוי נשמר", Toast.LENGTH_SHORT).show()
                    refreshAutoBackupList()
                } else {
                    Toast.makeText(this@SettingsActivity, "❌ שגיאה בגיבוי", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadMedications() {
        lifecycleScope.launch {
            val meds = repo.getAllMedications().sortedByDescending { it.active }
            val personMap = repo.getAllPersons().associateBy { it.id }

            binding.layoutMedicationsList.removeAllViews()
            binding.tvNoMedications.visibility = if (meds.isEmpty()) View.VISIBLE else View.GONE

            var inactiveHeaderShown = false
            for (med in meds) {
                if (!med.active && !inactiveHeaderShown) {
                    inactiveHeaderShown = true
                    val header = android.widget.TextView(this@SettingsActivity).apply {
                        text = "לא פעילות"
                        textSize = 14f
                        setPadding(8, 32, 8, 8)
                        setTextColor(android.graphics.Color.parseColor("#757575"))
                    }
                    binding.layoutMedicationsList.addView(header)
                }

                val rowBinding = ItemMedicationBinding.inflate(
                    LayoutInflater.from(this@SettingsActivity),
                    binding.layoutMedicationsList, false
                )
                val person: Person? = if (med.personId != 0L) personMap[med.personId] else null
                val color = person?.color ?: med.color

                rowBinding.tvName.text = med.name
                rowBinding.tvDosage.text = med.dosage
                rowBinding.colorIndicator.setBackgroundColor(color)
                rowBinding.root.alpha = if (med.active) 1.0f else 0.5f

                val statusParts = mutableListOf<String>()
                if (person != null) statusParts.add("👤 ${person.name}")
                statusParts.add(if (med.active) "פעיל" else "לא פעיל")
                rowBinding.tvStatus.text = statusParts.joinToString("  ·  ")

                rowBinding.root.setOnClickListener {
                    startActivity(Intent(this@SettingsActivity, AddEditMedicationActivity::class.java).apply {
                        putExtra("medication_id", med.id)
                    })
                }

                if (med.active) {
                    rowBinding.btnDelete.visibility = View.VISIBLE
                    rowBinding.btnRestore.visibility = View.GONE
                    rowBinding.btnDelete.setOnClickListener {
                        AlertDialog.Builder(this@SettingsActivity)
                            .setTitle("השבתת תרופה")
                            .setMessage("${med.name} תעבור ל'לא פעילות'. לא יישלחו יותר התראות עליה, אבל כל ההיסטוריה שלה תישמר ותוכל להחזיר אותה בכל עת.")
                            .setPositiveButton("הפוך ללא פעיל") { _, _ ->
                                lifecycleScope.launch {
                                    repo.deactivateMedication(this@SettingsActivity, med)
                                    loadMedications()
                                }
                            }
                            .setNegativeButton("ביטול", null)
                            .show()
                    }
                } else {
                    rowBinding.btnDelete.visibility = View.GONE
                    rowBinding.btnRestore.visibility = View.VISIBLE
                    rowBinding.btnRestore.setOnClickListener {
                        lifecycleScope.launch {
                            repo.reactivateMedication(this@SettingsActivity, med)
                            loadMedications()
                        }
                    }
                }
                binding.layoutMedicationsList.addView(rowBinding.root)
            }
        }
    }

    private fun doExport(uri: Uri) {
        lifecycleScope.launch {
            binding.btnExport.isEnabled = false
            val result = BackupManager.exportToUri(this@SettingsActivity, uri)
            binding.btnExport.isEnabled = true
            if (result.isSuccess) {
                Toast.makeText(this@SettingsActivity, "✅ גיבוי יוצא בהצלחה", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@SettingsActivity,
                    "❌ שגיאה: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showRestoreConfirmation(uri: Uri) {
        lifecycleScope.launch {
            val summaryResult = BackupManager.readSummaryFromUri(this@SettingsActivity, uri)
            if (summaryResult.isFailure) {
                Toast.makeText(this@SettingsActivity,
                    "❌ ${summaryResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                return@launch
            }
            val summary = summaryResult.getOrNull()!!
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
            val dateStr = sdf.format(Date(summary.exportedAt))

            AlertDialog.Builder(this@SettingsActivity)
                .setTitle("שחזור גיבוי")
                .setMessage(
                    "📁 קובץ מתאריך: $dateStr\n\n" +
                    "• ${summary.medications} תרופות\n" +
                    "• ${summary.persons} אנשים\n" +
                    "• ${summary.historyEntries} רשומות היסטוריה\n\n" +
                    "⚠️ פעולה זו תמחק את כל הנתונים הנוכחיים.\n\nהאם להמשיך?"
                )
                .setPositiveButton("שחזר") { _, _ -> doRestore(uri) }
                .setNegativeButton("ביטול", null)
                .show()
        }
    }

    private fun doRestore(uri: Uri) {
        lifecycleScope.launch {
            binding.btnImport.isEnabled = false
            binding.progressBar.visibility = View.VISIBLE

            val result = BackupManager.restoreFromUri(this@SettingsActivity, uri)

            binding.progressBar.visibility = View.GONE
            binding.btnImport.isEnabled = true

            if (result.isSuccess) {
                val summary = result.getOrNull()!!
                try {
                    val medications = repo.getActiveMedications()
                    for (med in medications) {
                        val schedule = repo.getSchedule(med.id) ?: continue
                        try { AlarmScheduler.scheduleAlarms(this@SettingsActivity, med, schedule) } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
                loadMedications()
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("✅ שחזור הושלם")
                    .setMessage("שוחזרו ${summary.medications} תרופות ו-${summary.persons} אנשים.\nכל התזכורות הוגדרו מחדש.")
                    .setPositiveButton("אישור", null)
                    .show()
            } else {
                Toast.makeText(this@SettingsActivity,
                    "❌ ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshAutoBackupList() {
        val files = BackupManager.listAutoBackups(this)
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
        // Show only the last (most recent) backup
        binding.tvAutoBackupList.text = if (files.isEmpty()) {
            "אין גיבוי פנימי"
        } else {
            val last = files.first()
            "📁 ${last.name}\n${sdf.format(Date(last.lastModified()))}"
        }
    }
}
