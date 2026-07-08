package com.elejar.memeji

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.elejar.memeji.util.PreferencesHelper

class MemesJiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val themeMode = PreferencesHelper.getThemeMode(this)
        AppCompatDelegate.setDefaultNightMode(themeMode)
    }
}
