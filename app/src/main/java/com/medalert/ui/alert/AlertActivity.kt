package com.medalert.ui.alert

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.medalert.alarm.AlarmScheduler
import com.medalert.data.db.entity.IntakeHistory
import com.medalert.data.db.entity.IntakeStatus
import com.medalert.data.repository.MedicationRepository
import com.medalert.databinding.ActivityAlertBinding
import com.medalert.notification.NotificationHelper
import com.medalert.util.toDisplayTime
import kotlinx.coroutines.launch

class AlertActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlertBinding
    private lateinit var repo: MedicationRepository
    private var medicationId: Long = -1
    private var medicationName: String = ""
    private var dosage: String = ""
    private var scheduledTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        )
        binding = ActivityAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = MedicationRepository(this)
        medicationId = intent.getLongExtra("medication_id", -1)
        medicationName = intent.getStringExtra("medication_name") ?: ""
        dosage = intent.getStringExtra("medication_dosage") ?: ""
        scheduledTime = intent.getLongExtra("scheduled_time", System.currentTimeMillis())

        binding.tvMedicationName.text = medicationName
        binding.tvDosage.text = dosage
        binding.tvScheduledTime.text = scheduledTime.toDisplayTime()

        setupButtons()
    }

    private fun setupButtons() {
        binding.btnTaken.setOnClickListener { logAndClose(IntakeStatus.TAKEN) }
        binding.btnSkip.setOnClickListener { logAndClose(IntakeStatus.SKIPPED) }
        binding.btnSnooze5.setOnClickListener { snooze(5) }
        binding.btnSnooze15.setOnClickListener { snooze(15) }
        binding.btnSnooze30.setOnClickListener { snooze(30) }
    }

    private fun logAndClose(status: IntakeStatus) {
        lifecycleScope.launch {
            repo.logIntake(
                IntakeHistory(
                    medicationId = medicationId,
                    scheduledDateTime = scheduledTime,
                    actualDateTime = System.currentTimeMillis(),
                    status = status
                )
            )
            if (status == IntakeStatus.TAKEN) {
                repo.decrementInventory(medicationId, this@AlertActivity)
            }
            NotificationHelper.cancelAlert(this@AlertActivity, medicationId)
            AlarmScheduler.cancelMissedCheck(this@AlertActivity, medicationId)
            finish()
        }
    }

    private fun snooze(minutes: Int) {
        AlarmScheduler.scheduleSnooze(this, medicationId, medicationName, dosage, minutes, scheduledTime)
        AlarmScheduler.cancelMissedCheck(this, medicationId)
        NotificationHelper.cancelAlert(this, medicationId)
        finish()
    }
}
