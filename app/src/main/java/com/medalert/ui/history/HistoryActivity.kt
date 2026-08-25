package com.medalert.ui.history

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medalert.data.db.entity.IntakeHistory
import com.medalert.data.db.entity.IntakeStatus
import com.medalert.data.db.entity.Medication
import com.medalert.data.db.entity.Person
import com.medalert.data.db.entity.PersonColors
import com.medalert.data.repository.MedicationRepository
import com.medalert.databinding.ActivityHistoryBinding
import com.medalert.databinding.ItemHistoryBinding
import com.medalert.util.toDisplayDate
import com.medalert.util.toDisplayTime
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var repo: MedicationRepository
    private lateinit var adapter: HistoryAdapter

    private val displaySdf = SimpleDateFormat("dd/MM/yyyy", Locale.US)

    // Date range — default: today-7 → today (end of day)
    private var fromCal: Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -7)
    }
    private var toCal: Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59);       set(Calendar.MILLISECOND, 999)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        repo = MedicationRepository(this)

        adapter = HistoryAdapter { item, medName ->
            AlertDialog.Builder(this)
                .setTitle("מחיקת רשומה")
                .setMessage("למחוק את הרשומה של $medName מתאריך ${item.scheduledDateTime.toDisplayDate()} ${item.scheduledDateTime.toDisplayTime()}?")
                .setPositiveButton("מחק") { _, _ ->
                    lifecycleScope.launch {
                        repo.deleteHistoryById(item.id)
                        loadHistory()
                    }
                }
                .setNegativeButton("ביטול", null)
                .show()
        }
        binding.recyclerHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerHistory.adapter = adapter

        updateDateLabels()
        loadHistory()

        binding.tvFromDate.setOnClickListener { pickDate(isFrom = true) }
        binding.tvToDate.setOnClickListener   { pickDate(isFrom = false) }
    }

    private fun updateDateLabels() {
        binding.tvFromDate.text = displaySdf.format(fromCal.time)
        binding.tvToDate.text   = displaySdf.format(toCal.time)
    }

    private fun pickDate(isFrom: Boolean) {
        val cal = if (isFrom) fromCal else toCal
        DatePickerDialog(this,
            { _, year, month, day ->
                cal.set(year, month, day)
                if (isFrom) {
                    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0);      cal.set(Calendar.MILLISECOND, 0)
                } else {
                    cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59);       cal.set(Calendar.MILLISECOND, 999)
                }
                // Keep from <= to
                if (isFrom && fromCal.after(toCal)) toCal.timeInMillis = fromCal.timeInMillis + 86_400_000L
                if (!isFrom && toCal.before(fromCal)) fromCal.timeInMillis = toCal.timeInMillis - 86_400_000L
                updateDateLabels()
                loadHistory()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            val history = repo.getHistoryInRange(fromCal.timeInMillis, toCal.timeInMillis)
            val medMap  = repo.getAllMedications().associateBy { it.id }
            val personMap = repo.getAllPersons().associateBy { it.id }

            // Already DESC from DAO — just submit
            adapter.setData(history, medMap, personMap)
            binding.tvResultCount.text = "${history.size} רשומות"
        }
    }

    // ─── Adapter ──────────────────────────────────────────────────────────────

    class HistoryAdapter(
        private val onDeleteClick: (IntakeHistory, String) -> Unit
    ) : ListAdapter<IntakeHistory, HistoryAdapter.VH>(Diff()) {

        private var medMap: Map<Long, Medication> = emptyMap()
        private var personMap: Map<Long, Person>  = emptyMap()

        fun setData(items: List<IntakeHistory>, meds: Map<Long, Medication>, persons: Map<Long, Person>) {
            medMap = meds
            personMap = persons
            submitList(items)
        }

        class VH(val b: ItemHistoryBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = getItem(position)
            val med  = medMap[item.medicationId]
            val medName = med?.name ?: "תרופה #${item.medicationId}"

            holder.b.tvMedName.text = medName
            holder.b.tvDosage.text  = med?.dosage ?: ""
            holder.b.tvDate.text    = item.scheduledDateTime.toDisplayDate()
            holder.b.tvScheduledTime.text = item.scheduledDateTime.toDisplayTime()
            holder.b.tvActualTime.text = if (item.actualDateTime > 0)
                "בפועל: ${item.actualDateTime.toDisplayTime()}" else ""

            val person = if (med?.personId != null && med.personId != 0L) personMap[med.personId] else null
            holder.b.colorBar.setBackgroundColor(person?.color ?: med?.color ?: PersonColors.DEFAULT)

            val (icon, color) = when (item.status) {
                IntakeStatus.TAKEN   -> "✅" to "#4CAF50"
                IntakeStatus.SKIPPED -> "⏭" to "#FF9800"
                IntakeStatus.MISSED  -> "❌" to "#F44336"
                IntakeStatus.SNOOZED -> "⏰" to "#2196F3"
            }
            holder.b.tvStatusIcon.text = icon
            holder.b.tvStatusIcon.setTextColor(android.graphics.Color.parseColor(color))

            holder.b.btnDelete.setOnClickListener { onDeleteClick(item, medName) }
        }

        class Diff : DiffUtil.ItemCallback<IntakeHistory>() {
            override fun areItemsTheSame(a: IntakeHistory, b: IntakeHistory) = a.id == b.id
            override fun areContentsTheSame(a: IntakeHistory, b: IntakeHistory) = a == b
        }
    }
}
