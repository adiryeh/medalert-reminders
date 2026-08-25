package com.medalert.ui.settings

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.medalert.data.db.entity.AlarmAuditLog
import com.medalert.data.repository.MedicationRepository
import com.medalert.databinding.ActivityAuditLogBinding
import com.medalert.databinding.ItemAuditLogBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AuditLogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuditLogBinding
    private val sdfFull   = SimpleDateFormat("dd/MM HH:mm:ss", Locale.US)
    private val sdfSched  = SimpleDateFormat("dd/MM HH:mm", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuditLogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        binding.btnBack.setOnClickListener { finish() }

        binding.recyclerAuditLog.layoutManager = LinearLayoutManager(this)
        binding.recyclerAuditLog.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )

        lifecycleScope.launch { loadLog() }
    }

    private suspend fun loadLog() {
        val repo = MedicationRepository(this)
        val entries = repo.getRecentAuditLog()

        val sevenDaysAgo = System.currentTimeMillis() - 7 * 86_400_000L
        val recentEntries = entries.filter { it.timestamp >= sevenDaysAgo }
        val safetyNetCount = recentEntries.count { it.event == "SAFETY_NET_FIRED" }
        val receiverFiredCount = recentEntries.count { it.event == "RECEIVER_FIRED" }
        val lastChecked = entries.firstOrNull { it.event == "SAFETY_NET_CHECKED" }

        if (safetyNetCount == 0) {
            binding.tvSummaryStatus.text = "✅ כל ההתראות קפצו בזמן"
            binding.tvSummaryStatus.setTextColor(Color.parseColor("#388E3C"))
            binding.cardSummary.setCardBackgroundColor(Color.parseColor("#F1F8E9"))
        } else {
            binding.tvSummaryStatus.text = "⚠ $safetyNetCount פעמים התראה לא קפצה בזמן"
            binding.tvSummaryStatus.setTextColor(Color.parseColor("#E53935"))
            binding.cardSummary.setCardBackgroundColor(Color.parseColor("#FFF8E1"))
        }
        val lastCheckedStr = if (lastChecked != null)
            "בדיקה אחרונה: ${sdfFull.format(Date(lastChecked.timestamp))}"
        else "בדיקת רקע לא רצה עדיין"
        binding.tvSummaryDetail.text = "7 ימים · $receiverFiredCount התראות · $lastCheckedStr"

        binding.recyclerAuditLog.adapter = AuditLogAdapter(entries)
    }

    private inner class AuditLogAdapter(private val items: List<AlarmAuditLog>)
        : RecyclerView.Adapter<AuditLogAdapter.VH>() {

        inner class VH(val b: ItemAuditLogBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemAuditLogBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val b = holder.b

            val (label, color) = when (item.event) {
                "RECEIVER_FIRED"     -> "📡 מקלט התעורר"    to "#388E3C"
                "NOTIFICATION_SHOWN" -> "🔔 התראה נשלחה"    to "#1565C0"
                "SAFETY_NET_FIRED"   -> "🆘 רשת ביטחון"     to "#E53935"
                "ACTION_TAKEN"       -> "✅ סומן נלקח"       to "#388E3C"
                "ACTION_SKIPPED"     -> "❌ סומן דולג"       to "#757575"
                "ACTION_SNOOZED"      -> "⏰ נדחה"             to "#FF8F00"
                "SAFETY_NET_CHECKED" -> "🔍 בדיקת רקע"       to "#9E9E9E"
                else                 -> item.event            to "#999999"
            }

            b.tvEventName.text = label
            b.tvEventName.setTextColor(Color.parseColor(color))
            b.viewEventBar.setBackgroundColor(Color.parseColor(color))
            b.tvTimestamp.text = sdfFull.format(Date(item.timestamp))
            b.tvMedName.text = if (item.medicationId < 0)
                item.medicationName
            else
                "${item.medicationName} · תוכנן: ${sdfSched.format(Date(item.scheduledTime))}"

            if (item.note.isNotEmpty()) {
                b.tvNote.text = item.note
                b.tvNote.visibility = View.VISIBLE
            } else {
                b.tvNote.visibility = View.GONE
            }
        }
    }
}
