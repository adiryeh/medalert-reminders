package com.medalert.util

import android.content.Context
import android.content.SharedPreferences

object PreferenceHelper {

    private const val PREFS = "medalert_prefs"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_TIME_FORMAT = "time_format_24h"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_AUTO_BACKUP = "auto_backup"
    private const val KEY_BIOMETRIC = "biometric_enabled"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isOnboardingDone(context: Context) = prefs(context).getBoolean(KEY_ONBOARDING_DONE, false)
    fun setOnboardingDone(context: Context) = prefs(context).edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()

    fun getDarkMode(context: Context) = prefs(context).getString(KEY_DARK_MODE, "system") ?: "system"
    fun setDarkMode(context: Context, mode: String) = prefs(context).edit().putString(KEY_DARK_MODE, mode).apply()

    fun is24HourFormat(context: Context) = prefs(context).getBoolean(KEY_TIME_FORMAT, true)
    fun set24HourFormat(context: Context, v: Boolean) = prefs(context).edit().putBoolean(KEY_TIME_FORMAT, v).apply()

    fun isAutoBackup(context: Context) = prefs(context).getBoolean(KEY_AUTO_BACKUP, false)
    fun setAutoBackup(context: Context, v: Boolean) = prefs(context).edit().putBoolean(KEY_AUTO_BACKUP, v).apply()

    fun isBiometricEnabled(context: Context) = prefs(context).getBoolean(KEY_BIOMETRIC, false)
    fun setBiometricEnabled(context: Context, v: Boolean) = prefs(context).edit().putBoolean(KEY_BIOMETRIC, v).apply()
}
