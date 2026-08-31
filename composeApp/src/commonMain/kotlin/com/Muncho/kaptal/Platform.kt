package com.muncho.kaptal

interface Platform {
    val name: String
    fun log(tag: String, message: String)
    fun showToast(message: String)
    fun openUrl(url: String)
    fun openEmail(email: String, subject: String)
    fun exit()
    fun pickDate(initialDate: Long, onDateSelected: (Long) -> Unit)
    fun pickFile(type: String, onResult: (String?) -> Unit)
    fun shareFile(content: String, fileName: String, mimeType: String)
    fun setLanguage(language: String)
}

expect fun getPlatform(): Platform
