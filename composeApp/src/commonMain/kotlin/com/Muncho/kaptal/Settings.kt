package com.Muncho.kaptal

interface KmpSettings {
    fun putString(key: String, value: String)
    fun getString(key: String, defaultValue: String): String
    fun putBoolean(key: String, value: Boolean)
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
}

expect fun getSettings(): KmpSettings
