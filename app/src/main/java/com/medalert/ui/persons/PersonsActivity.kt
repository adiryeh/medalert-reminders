package com.medalert.ui.persons

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medalert.data.db.entity.Person
import com.medalert.data.db.entity.PersonColors
import com.medalert.data.repository.MedicationRepository
import com.medalert.databinding.ActivityPersonsBinding
import com.medalert.databinding.DialogPersonEditBinding
import com.medalert.databinding.ItemPersonBinding
import kotlinx.coroutines.launch

class PersonsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPersonsBinding
    private lateinit var repo: MedicationRepository
    private lateinit var adapter: PersonAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPersonsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        repo = MedicationRepository(this)

        adapter = PersonAdapter(
            onEdit   = { person -> showEditDialog(person) },
            onDelete = { person -> confirmDelete(person) }
        )
        binding.recyclerPersons.layoutManager = LinearLayoutManager(this)
        binding.recyclerPersons.adapter = adapter

        binding.btnAddPerson.setOnClickListener { showEditDialog(null) }

        repo.allPersons.observe(this) { persons ->
            adapter.submitList(persons)
            binding.tvNoPersons.visibility = if (persons.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun showEditDialog(existing: Person?) {
        val dialogBinding = DialogPersonEditBinding.inflate(LayoutInflater.from(this))
        var selectedColor = existing?.color ?: PersonColors.PALETTE[0]

        existing?.let { dialogBinding.etPersonName.setText(it.name) }

        // Set up color selector
        val colorViews = listOf(
            dialogBinding.color0, dialogBinding.color1, dialogBinding.color2, dialogBinding.color3,
            dialogBinding.color4, dialogBinding.color5, dialogBinding.color6, dialogBinding.color7
        )

        fun updateSelection(color: Int) {
            selectedColor = color
            dialogBinding.selectedColorPreview.setBackgroundColor(color)
            // Show border on selected
            colorViews.forEachIndexed { i, v ->
                val c = PersonColors.PALETTE[i]
                v.alpha = if (c == color) 1f else 0.5f
                v.scaleX = if (c == color) 1.15f else 1f
                v.scaleY = if (c == color) 1.15f else 1f
            }
        }

        colorViews.forEachIndexed { i, v ->
            v.setBackgroundColor(PersonColors.PALETTE[i])
            v.setOnClickListener { updateSelection(PersonColors.PALETTE[i]) }
        }
        updateSelection(selectedColor)

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "הוסף אדם" else "ערוך אדם")
            .setView(dialogBinding.root)
            .setPositiveButton("שמור") { _, _ ->
                val name = dialogBinding.etPersonName.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "יש להזין שם", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    if (existing == null) {
                        repo.insertPerson(Person(name = name, color = selectedColor))
                    } else {
                        repo.updatePerson(existing.copy(name = name, color = selectedColor))
                        // Update all medications belonging to this person
                        val meds = repo.getAllMedications().filter { it.personId == existing.id }
                        for (med in meds) {
                            repo.updateMedication(med.copy(color = selectedColor))
                        }
                    }
                }
            }
            .setNegativeButton("ביטול", null)
            .show()
    }

    private fun confirmDelete(person: Person) {
        lifecycleScope.launch {
            val medsCount = repo.getAllMedications().count { it.personId == person.id }
            val msg = if (medsCount > 0)
                "האם למחוק את ${person.name}?\n\n$medsCount תרופות ישויכו ל\"ללא שיוך\"."
            else
                "האם למחוק את ${person.name}?"

            AlertDialog.Builder(this@PersonsActivity)
                .setTitle("מחיקת אדם")
                .setMessage(msg)
                .setPositiveButton("מחק") { _, _ ->
                    lifecycleScope.launch {
                        // Unassign medications
                        val meds = repo.getAllMedications().filter { it.personId == person.id }
                        for (med in meds) {
                            repo.updateMedication(med.copy(personId = 0, color = PersonColors.DEFAULT))
                        }
                        repo.deletePerson(person)
                    }
                }
                .setNegativeButton("ביטול", null)
                .show()
        }
    }

    class PersonAdapter(
        private val onEdit: (Person) -> Unit,
        private val onDelete: (Person) -> Unit
    ) : ListAdapter<Person, PersonAdapter.VH>(Diff()) {

        inner class VH(val b: ItemPersonBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemPersonBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val person = getItem(position)
            holder.b.tvPersonName.text = person.name
            holder.b.viewPersonColor.setBackgroundColor(person.color)
            holder.b.btnEditPerson.setOnClickListener { onEdit(person) }
            holder.b.btnDeletePerson.setOnClickListener { onDelete(person) }
        }

        class Diff : DiffUtil.ItemCallback<Person>() {
            override fun areItemsTheSame(a: Person, b: Person) = a.id == b.id
            override fun areContentsTheSame(a: Person, b: Person) = a == b
        }
    }
}
