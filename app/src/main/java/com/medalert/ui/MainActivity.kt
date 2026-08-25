package com.medalert.ui

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.medalert.databinding.ActivityMainBinding
import com.medalert.ui.history.HistoryActivity
import com.medalert.ui.inventory.InventoryActivity
import com.medalert.ui.medication.AddEditMedicationActivity
import com.medalert.ui.onboarding.OnboardingActivity
import com.medalert.ui.settings.SettingsActivity
import com.medalert.ui.viewmodel.MainViewModel
import com.medalert.util.BiometricHelper
import com.medalert.util.PreferenceHelper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private var authenticated = false   // reset when app goes to background
    private lateinit var overdueAdapter: AlertsAdapter
    private lateinit var doneAdapter: AlertsAdapter
    private lateinit var todayPendingAdapter: AlertsAdapter
    private lateinit var upcomingAdapter: AlertsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)

        if (!PreferenceHelper.isOnboardingDone(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        supportActionBar?.hide()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerViews()
        setupButtons()
        observeData()
    }

    override fun onResume() {
        super.onResume()
        if (PreferenceHelper.isBiometricEnabled(this) && !authenticated) {
            // Hide content until authenticated
            binding.root.visibility = android.view.View.INVISIBLE
            BiometricHelper.authenticate(
                activity = this,
                onSuccess = {
                    authenticated = true
                    binding.root.visibility = android.view.View.VISIBLE
                    viewModel.loadAll()
                    checkPermissions()
                },
                onFailed = { finish() }
            )
        } else {
            viewModel.loadAll()
            checkPermissions()
        }
    }

    override fun onStop() {
        super.onStop()
        // Require re-authentication when coming back from background
        authenticated = false
    }

    private fun applyTheme() {
        when (PreferenceHelper.getDarkMode(this)) {
            "dark"  -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            else    -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun checkPermissions() {
        val problems = mutableListOf<String>()

        // Android 13+: POST_NOTIFICATIONS requires runtime grant
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!getSystemService(NotificationManager::class.java).areNotificationsEnabled())
                problems.add("התראות")
        }

        // Android 12+: SCHEDULE_EXACT_ALARM requires user grant
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!getSystemService(AlarmManager::class.java).canScheduleExactAlarms())
                problems.add("תזמון מדויק")
        }

        // All versions: battery optimization exemption needed for reliable background alarms
        // On Samsung/Xiaomi/etc. Doze kills alarms without this exemption
        val powerManager = getSystemService(PowerManager::class.java)
        if (!powerManager.isIgnoringBatteryOptimizations(packageName))
            problems.add("חיסכון בסוללה")

        // Check notification channel importance — user can silently demote the channel
        // after initial grant (Settings → App → Notifications → channel → silent).
        // The alarm fires and notification posts successfully, but no sound/vibration.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = nm.getNotificationChannel(com.medalert.MedAlertApplication.CHANNEL_ALERTS)
            if (channel != null && channel.importance < NotificationManager.IMPORTANCE_DEFAULT) {
                problems.add("עוצמת התראות הופחתה")
            }
        }

        binding.tvPermissionWarning.visibility = if (problems.isNotEmpty()) {
            binding.tvPermissionWarning.text = "⚠ בעיה: ${problems.joinToString(", ")} — לחץ לתיקון"
            binding.tvPermissionWarning.setOnClickListener {
                // If it's specifically the channel importance, open channel settings directly
                if (problems.any { it.contains("עוצמת") } && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        putExtra(Settings.EXTRA_CHANNEL_ID, com.medalert.MedAlertApplication.CHANNEL_ALERTS)
                    }
                    startActivity(intent)
                } else {
                    startActivity(Intent(this, OnboardingActivity::class.java))
                }
            }
            View.VISIBLE
        } else View.GONE
    }

    private fun setupRecyclerViews() {
        overdueAdapter = AlertsAdapter(
            onTaken = { viewModel.markTaken(it) },
            onSkip  = { viewModel.markSkipped(it) }
        )
        doneAdapter = AlertsAdapter(
            onEdit = { viewModel.undoAction(it) }
        )
        todayPendingAdapter = AlertsAdapter()
        upcomingAdapter = AlertsAdapter()

        binding.recyclerPastAlerts.layoutManager = LinearLayoutManager(this)
        binding.recyclerPastAlerts.adapter = overdueAdapter

        binding.recyclerDoneAlerts.layoutManager = LinearLayoutManager(this)
        binding.recyclerDoneAlerts.adapter = doneAdapter

        binding.recyclerTodayPendingAlerts.layoutManager = LinearLayoutManager(this)
        binding.recyclerTodayPendingAlerts.adapter = todayPendingAdapter

        // Upcoming week fills remaining height and scrolls independently
        binding.recyclerUpcomingAlerts.layoutManager = LinearLayoutManager(this)
        binding.recyclerUpcomingAlerts.adapter = upcomingAdapter
    }

    private fun setupButtons() {
        binding.btnAddMedication.setOnClickListener {
            startActivity(Intent(this, AddEditMedicationActivity::class.java))
        }
        binding.btnInventory.setOnClickListener {
            startActivity(Intent(this, InventoryActivity::class.java))
        }
        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun observeData() {
        viewModel.todaySummary.observe(this) { s ->
            binding.tvMedicationsCount.text = "${s.total}"
            binding.tvCompleted.text = "${s.completed}"
            binding.tvPending.text = "${s.pending}"
            binding.tvMissed.text = "${s.missed}"
        }
        viewModel.overdueAlerts.observe(this) { list ->
            overdueAdapter.submitList(list)
            binding.tvPastHeader.visibility = if (list.isNotEmpty()) View.VISIBLE else View.GONE
            // If any overdue item was scheduled in the last 2 hours, the notification
            // should have fired very recently — warn the user it might have been missed.
            val twoHoursAgo = System.currentTimeMillis() - 2 * 3600_000L
            val recentMissed = list.filter { it.scheduledTime >= twoHoursAgo }
            if (recentMissed.isNotEmpty()) {
                val names = recentMissed.joinToString(", ") { it.medication.name }
                Snackbar.make(binding.root, "⚠ ייתכן שהתראה לא נשלחה: $names", Snackbar.LENGTH_LONG)
                    .setAction("טפל") { binding.recyclerPastAlerts.smoothScrollToPosition(0) }
                    .show()
            }
        }
        viewModel.todayDoneAlerts.observe(this) { list ->
            doneAdapter.submitList(list)
            binding.tvDoneHeader.visibility = if (list.isNotEmpty()) View.VISIBLE else View.GONE
        }
        viewModel.todayPendingAlerts.observe(this) { list ->
            todayPendingAdapter.submitList(list)
            binding.tvTodayPendingHeader.visibility = if (list.isNotEmpty()) View.VISIBLE else View.GONE
        }
        viewModel.upcomingAlerts.observe(this) { list ->
            upcomingAdapter.submitList(list)
            binding.tvUpcomingHeader.visibility = if (list.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }
}
