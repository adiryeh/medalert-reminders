package com.medalert.ui.onboarding

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.medalert.databinding.ActivityOnboardingBinding
import com.medalert.ui.MainActivity
import com.medalert.util.OemBatteryHelper
import com.medalert.util.PreferenceHelper

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    // Android 13+ notifications permission via the modern Activity Result API
    private val notifLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { updateStatuses() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Hide exact-alarm row on Android < 12 (not needed)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            binding.rowExactAlarm.visibility = View.GONE
        }
        // Hide notification row on Android < 13 (auto-granted)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            binding.rowNotifications.visibility = View.GONE
        }

        binding.btnGrantNotif.setOnClickListener { requestNotifications() }
        binding.btnGrantAlarm.setOnClickListener { requestExactAlarm() }
        binding.btnGrantBattery.setOnClickListener { requestBatteryExemption() }
        binding.btnGrantOem.setOnClickListener { OemBatteryHelper.openOemSettings(this) }

        // Show OEM row only on affected manufacturers (Xiaomi, Huawei, Samsung, Oppo, etc.)
        if (OemBatteryHelper.isAffectedOem()) {
            binding.rowOem.visibility = View.VISIBLE
        }

        binding.btnContinue.setOnClickListener {
            PreferenceHelper.setOnboardingDone(this)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatuses()
    }

    private fun updateStatuses() {
        // Notifications
        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        else true
        setRowStatus(binding.tvNotifStatus, binding.btnGrantNotif, notifGranted)

        // Exact alarms
        val alarmGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        else true
        setRowStatus(binding.tvAlarmStatus, binding.btnGrantAlarm, alarmGranted)

        // Battery optimization
        val batteryGranted = getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(packageName)
        setRowStatus(binding.tvBatteryStatus, binding.btnGrantBattery, batteryGranted)
    }

    private fun setRowStatus(statusView: android.widget.TextView, btn: android.widget.Button, granted: Boolean) {
        statusView.text = if (granted) "✅" else "⚠️"
        btn.isEnabled = !granted
        btn.alpha = if (granted) 0.4f else 1.0f
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestExactAlarm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:$packageName")
            })
        }
    }

    private fun requestBatteryExemption() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            })
        } catch (_: Exception) {
            // Fallback: open general battery settings
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}
