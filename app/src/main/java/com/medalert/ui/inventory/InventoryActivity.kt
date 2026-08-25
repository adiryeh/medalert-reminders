package com.medalert.ui.inventory

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.medalert.data.db.entity.Inventory
import com.medalert.data.db.entity.InventoryUnit
import com.medalert.data.db.entity.Medication
import com.medalert.data.repository.MedicationRepository
import com.medalert.databinding.ActivityInventoryBinding
import com.medalert.databinding.ItemMedicationBinding
import kotlinx.coroutines.launch

class InventoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInventoryBinding
    private lateinit var repo: MedicationRepository
    private var items: List<Pair<Medication, Inventory>> = emptyList()
    private lateinit var adapter: InvAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInventoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = MedicationRepository(this)
        adapter = InvAdapter()
        binding.recyclerInventory.layoutManager = LinearLayoutManager(this)
        binding.recyclerInventory.adapter = adapter
        loadData()
    }

    private fun loadData() {
        lifecycleScope.launch {
            val medications = repo.getActiveMedications()
            val inventory = repo.getAllInventory()
            val invMap = inventory.associateBy { it.medicationId }
            items = medications.mapNotNull { med ->
                invMap[med.id]?.let { inv -> Pair(med, inv) }
            }
            adapter.notifyDataSetChanged()
        }
    }

    private fun unitToHebrew(unit: InventoryUnit) = when (unit) {
        InventoryUnit.PILLS -> "כדורים"
        InventoryUnit.CAPSULES -> "קפסולות"
        InventoryUnit.DROPS -> "טיפות"
        InventoryUnit.ML -> "מ\"ל"
        InventoryUnit.INJECTIONS -> "זריקות"
        InventoryUnit.OTHER -> "יחידות"
    }

    private fun showUpdateDialog(med: Medication, inv: Inventory) {
        val unitHeb = unitToHebrew(inv.unit)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 24, 56, 0)
        }

        val tvCurrent = TextView(this).apply {
            text = "נוכחי: ${inv.currentQuantity.toInt()} $unitHeb"
            setPadding(0, 0, 0, 16)
        }
        val etAdd = EditText(this).apply {
            hint = "הוסף כמות (לדוגמה: 30 $unitHeb)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val etSet = EditText(this).apply {
            hint = "או: הגדר כמות חדשה"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val etThreshold = EditText(this).apply {
            hint = "סף התראה (נוכחי: ${inv.alertThreshold.toInt()})"
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        layout.addView(tvCurrent)
        layout.addView(etAdd)
        layout.addView(etSet)
        layout.addView(TextView(this).apply {
            text = "התראה כשנותר פחות מ:"; setPadding(0, 16, 0, 0)
        })
        layout.addView(etThreshold)

        AlertDialog.Builder(this)
            .setTitle("עדכון מלאי — ${med.name}")
            .setView(layout)
            .setPositiveButton("שמור") { _, _ ->
                val addVal = etAdd.text.toString().toDoubleOrNull()
                val setVal = etSet.text.toString().toDoubleOrNull()
                val newQty = when {
                    setVal != null -> setVal
                    addVal != null -> inv.currentQuantity + addVal
                    else -> inv.currentQuantity
                }
                val newThreshold = etThreshold.text.toString().toDoubleOrNull() ?: inv.alertThreshold
                lifecycleScope.launch {
                    repo.updateInventory(inv.copy(currentQuantity = newQty, alertThreshold = newThreshold))
                    loadData()
                }
            }
            .setNegativeButton("ביטול", null)
            .show()
    }

    private fun confirmDelete(med: Medication) {
        AlertDialog.Builder(this)
            .setTitle("השבתת תרופה")
            .setMessage("${med.name} תעבור ל'לא פעילות' בהגדרות. לא יישלחו יותר התראות עליה, אבל כל ההיסטוריה שלה תישמר ותוכל להחזיר אותה בכל עת.")
            .setPositiveButton("הפוך ללא פעיל") { _, _ ->
                lifecycleScope.launch {
                    repo.deactivateMedication(this@InventoryActivity, med)
                    loadData()
                }
            }
            .setNegativeButton("ביטול", null)
            .show()
    }

    inner class InvAdapter : RecyclerView.Adapter<InvAdapter.VH>() {

        inner class VH(val binding: ItemMedicationBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemMedicationBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (med, inv) = items[position]
            val unitHeb = unitToHebrew(inv.unit)
            holder.binding.tvName.text = med.name
            holder.binding.tvDosage.text = buildString {
                if (med.dosage.isNotEmpty()) append("${med.dosage}  |  ")
                append("${inv.currentQuantity.toInt()} $unitHeb")
            }
            holder.binding.tvStatus.text = when {
                inv.currentQuantity <= 0 -> "❌ אזל המלאי"
                inv.currentQuantity <= inv.alertThreshold -> "⚠ מלאי נמוך (סף: ${inv.alertThreshold.toInt()})"
                else -> "✓ תקין"
            }
            holder.binding.colorIndicator.setBackgroundColor(med.color)
            holder.binding.btnDelete.isEnabled = true
            holder.binding.btnDelete.setOnClickListener { confirmDelete(med) }
            holder.binding.root.setOnClickListener { showUpdateDialog(med, inv) }
        }
    }
}
