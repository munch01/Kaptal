package com.muncho.kaptal

class IosSettings : KmpSettings {
    override fun putString(key: String, value: String) {}
    override fun getString(key: String, defaultValue: String): String = defaultValue
    override fun putBoolean(key: String, value: Boolean) {}
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
}

actual fun getSettings(): KmpSettings = IosSettings()
