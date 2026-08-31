package com.muncho.kaptal

class IosPlatform : Platform {
    override val name: String = "iOS"
    override fun log(tag: String, message: String) {
        println("[$tag] $message")
    }
    override fun showToast(message: String) {
        println("TOAST: $message")
    }
    override fun openUrl(url: String) {
        // Implementation for iOS URL opening
    }
    override fun openEmail(email: String, subject: String) {
        // Implementation for iOS Email
    }
    override fun exit() {
        // iOS apps usually don't have an exit button
    }
    override fun pickDate(initialDate: Long, onDateSelected: (Long) -> Unit) {
        // iOS implementation could use a UIDatePicker
    }
    override fun pickFile(type: String, onResult: (String?) -> Unit) {
        // iOS implementation for UIDocumentPickerViewController
    }
    override fun shareFile(content: String, fileName: String, mimeType: String) {
        // iOS implementation for UIActivityViewController
    }
    override fun setLanguage(language: String) {
        // iOS implementation for language change
    }
}

actual fun getPlatform(): Platform = IosPlatform()
