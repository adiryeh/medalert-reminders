package com.medalert.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medalert.databinding.ItemAlertUpcomingBinding
import com.medalert.ui.viewmodel.AlertItem
import com.medalert.ui.viewmodel.AlertItemState
import com.medalert.util.toDayOfWeekHebrew
import com.medalert.util.toDisplayTime

class AlertsAdapter(
    private val onTaken: (AlertItem) -> Unit = {},
    private val onSkip:  (AlertItem) -> Unit = {},
    private val onEdit:  (AlertItem) -> Unit = {}
) : ListAdapter<AlertItem, AlertsAdapter.VH>(Diff()) {

    inner class VH(val binding: ItemAlertUpcomingBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemAlertUpcomingBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val b = holder.binding

        b.tvTime.text = item.scheduledTime.toDisplayTime()
        b.tvDay.text = item.scheduledTime.toDayOfWeekHebrew()
        b.tvMedName.text = item.medication.name
        b.tvDosage.text = item.medication.dosage
        b.colorBar.setBackgroundColor(item.displayColor)

        // Person name
        if (item.personName != null) {
            b.tvPersonName.text = "👤 ${item.personName}"
            b.tvPersonName.visibility = View.VISIBLE
        } else {
            b.tvPersonName.visibility = View.GONE
        }

        // Reset state
        b.layoutActions.visibility = View.GONE
        b.tvStatusBadge.visibility = View.GONE
        b.btnEdit.visibility = View.GONE
        holder.itemView.alpha = 1f

        when (item.state) {
            AlertItemState.OVERDUE -> {
                b.layoutActions.visibility = View.VISIBLE
                b.btnTaken.setOnClickListener { onTaken(item) }
                b.btnSkip.setOnClickListener { onSkip(item) }
            }
            AlertItemState.TAKEN -> {
                b.tvStatusBadge.visibility = View.VISIBLE
                b.tvStatusBadge.text = "✅"
                b.btnEdit.visibility = View.VISIBLE
                b.btnEdit.setOnClickListener { onEdit(item) }
                holder.itemView.alpha = 0.75f
            }
            AlertItemState.SKIPPED -> {
                b.tvStatusBadge.visibility = View.VISIBLE
                b.tvStatusBadge.text = "⏭"
                b.btnEdit.visibility = View.VISIBLE
                b.btnEdit.setOnClickListener { onEdit(item) }
                holder.itemView.alpha = 0.75f
            }
            AlertItemState.FUTURE -> { /* view only */ }
        }
    }

    class Diff : DiffUtil.ItemCallback<AlertItem>() {
        override fun areItemsTheSame(a: AlertItem, b: AlertItem) =
            a.medication.id == b.medication.id && a.scheduledTime == b.scheduledTime
        override fun areContentsTheSame(a: AlertItem, b: AlertItem) = a == b
    }
}
