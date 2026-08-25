package com.Muncho.kaptal

interface Platform {
    val name: String
    fun log(tag: String, message: String)
    fun showToast(message: String)
    fun openUrl(url: String)
    fun openEmail(email: String, subject: String)
    fun exit()
}

expect fun getPlatform(): Platform
