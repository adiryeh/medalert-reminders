package com.medalert.util

import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Opens the OEM-specific "Autostart" / "Protected apps" battery settings screen.
 *
 * Samsung, Xiaomi, Huawei, Oppo/Realme, Vivo, and OnePlus all have proprietary
 * background-kill layers that ignore Android's standard battery optimization exemption.
 * Without being whitelisted in these screens, alarms may be killed on ~60% of devices.
 *
 * Returns true if a known OEM screen was found and opened, false if the device is
 * stock Android (no OEM screen needed / not known).
 */
object OemBatteryHelper {

    /** True only on OEMs known to have aggressive proprietary battery killing. */
    fun isAffectedOem(): Boolean = oemIntent() != null

    /** Attempts to open the OEM autostart screen. Returns false if not applicable. */
    fun openOemSettings(context: Context): Boolean {
        val intent = oemIntent() ?: return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun oemIntent(): Intent? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ->
                tryIntent("com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity")
                    ?: tryIntent("com.miui.securitycenter",
                        "com.miui.securitycenter.MainActivity")

            manufacturer.contains("huawei") || manufacturer.contains("honor") ->
                tryIntent("com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
                    ?: tryIntent("com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity")

            manufacturer.contains("samsung") ->
                tryIntent("com.samsung.android.lool",
                    "com.samsung.android.sm.battery.ui.BatteryActivity")
                    ?: tryIntent("com.samsung.android.sm_cn",
                        "com.samsung.android.sm.battery.ui.BatteryActivity")

            manufacturer.contains("oppo") || manufacturer.contains("realme") ->
                tryIntent("com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity")
                    ?: tryIntent("com.coloros.oppoguardelf",
                        "com.coloros.powermanager.powersave.PowerUsageModelActivity")

            manufacturer.contains("vivo") ->
                tryIntent("com.vivo.permissionmanagement",
                    "com.vivo.permissionmanagement.activity.SoftPermissionDetailActivity")
                    ?: tryIntent("com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")

            manufacturer.contains("oneplus") ->
                tryIntent("com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")

            manufacturer.contains("letv") || manufacturer.contains("leeco") ->
                tryIntent("com.letv.android.letvsafe",
                    "com.letv.android.letvsafe.AutobootManageActivity")

            manufacturer.contains("asus") ->
                tryIntent("com.asus.mobilemanager",
                    "com.asus.mobilemanager.powersaver.PowerSaverSettings")

            else -> null
        }
    }

    private fun tryIntent(pkg: String, cls: String): Intent? {
        return Intent().apply {
            setClassName(pkg, cls)
        }
    }
}
