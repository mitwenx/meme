package com.elejar.memeji.util

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

object PreferencesHelper {

    private const val PREFS_NAME = "MemeJiPrefs"
    private const val KEY_CUTIE_MODE = "cutieModeEnabled"
    private const val KEY_THEME_MODE = "themeMode"

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isCutieModeEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_CUTIE_MODE, true)
    }

    fun setCutieModeEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_CUTIE_MODE, enabled).apply()
    }

    fun getThemeMode(context: Context): Int {
        return getPreferences(context).getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    fun setThemeMode(context: Context, mode: Int) {
        getPreferences(context).edit().putInt(KEY_THEME_MODE, mode).apply()
    }
}
