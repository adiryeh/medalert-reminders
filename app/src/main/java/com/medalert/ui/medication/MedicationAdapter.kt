package com.medalert.ui.medication

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medalert.data.db.entity.Medication
import com.medalert.data.db.entity.Person
import com.medalert.data.db.entity.PersonColors
import com.medalert.databinding.ItemMedicationBinding

class MedicationAdapter(
    private val onItemClick: (Medication) -> Unit,
    private val onDeleteClick: (Medication) -> Unit
) : ListAdapter<Medication, MedicationAdapter.ViewHolder>(DiffCallback()) {

    private var personMap: Map<Long, Person> = emptyMap()

    fun setPersonMap(map: Map<Long, Person>) {
        personMap = map
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemMedicationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(med: Medication) {
            val person = if (med.personId != 0L) personMap[med.personId] else null
            val color = person?.color ?: PersonColors.DEFAULT

            binding.tvName.text = med.name
            binding.tvDosage.text = med.dosage
            binding.colorIndicator.setBackgroundColor(color)

            // Show person name and active status
            val statusParts = mutableListOf<String>()
            if (person != null) statusParts.add("👤 ${person.name}")
            statusParts.add(if (med.active) "פעיל" else "מושבת")
            binding.tvStatus.text = statusParts.joinToString("  ·  ")

            binding.root.setOnClickListener { onItemClick(med) }
            binding.btnDelete.setOnClickListener { onDeleteClick(med) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMedicationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class DiffCallback : DiffUtil.ItemCallback<Medication>() {
        override fun areItemsTheSame(a: Medication, b: Medication) = a.id == b.id
        override fun areContentsTheSame(a: Medication, b: Medication) = a == b
    }
}
