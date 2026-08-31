package com.muncho.kaptal

import android.content.Context

class AndroidSettings(private val context: Context) : KmpSettings {
    private val prefs = context.getSharedPreferences("kaptal_prefs", Context.MODE_PRIVATE)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getString(key: String, defaultValue: String): String {
        return prefs.getString(key, defaultValue) ?: defaultValue
    }

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }
}

actual fun getSettings(): KmpSettings = AndroidSettings(appContext)
