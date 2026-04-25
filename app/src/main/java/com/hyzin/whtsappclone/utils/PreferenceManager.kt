package com.hyzin.whtsappclone.utils

import android.content.Context
import android.content.SharedPreferences

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("wattshub_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_THEME = "selected_theme"
        private const val KEY_VERSION = "app_version"
        private const val KEY_VERSION_NAME = "app_version_name"
    }

    fun setTheme(theme: String) {
        prefs.edit().putString(KEY_THEME, theme).apply()
    }

    fun getTheme(): String {
        return prefs.getString(KEY_THEME, "STARRY_NIGHT") ?: "STARRY_NIGHT"
    }

    fun setAppVersion(version: Int) {
        prefs.edit().putInt(KEY_VERSION, version).apply()
    }

    fun getAppVersion(): Int {
        return prefs.getInt(KEY_VERSION, 1)
    }

    fun setVersionName(name: String) {
        prefs.edit().putString(KEY_VERSION_NAME, name).apply()
    }

    fun getVersionName(): String {
        return prefs.getString(KEY_VERSION_NAME, "") ?: ""
    }

    // ── NOTIFICATION CONTROLS ──
    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("notifications_enabled", enabled).apply()
    }

    fun getNotificationsEnabled(): Boolean {
        return prefs.getBoolean("notifications_enabled", true)
    }

    fun setSoundsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("sounds_enabled", enabled).apply()
    }

    fun getSoundsEnabled(): Boolean {
        return prefs.getBoolean("sounds_enabled", true)
    }
}
