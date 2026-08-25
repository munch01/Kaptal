package com.Muncho.kaptal

import java.util.Properties
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class DesktopSettings : KmpSettings {
    private val props = Properties()
    private val file = File(System.getProperty("user.home"), ".kaptal_settings.properties")

    init {
        if (file.exists()) {
            FileInputStream(file).use { props.load(it) }
        }
    }

    private fun save() {
        FileOutputStream(file).use { props.store(it, null) }
    }

    override fun putString(key: String, value: String) {
        props.setProperty(key, value)
        save()
    }

    override fun getString(key: String, defaultValue: String): String {
        return props.getProperty(key, defaultValue)
    }

    override fun putBoolean(key: String, value: Boolean) {
        props.setProperty(key, value.toString())
        save()
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return props.getProperty(key, defaultValue.toString()).toBoolean()
    }
}

actual fun getSettings(): KmpSettings = DesktopSettings()
