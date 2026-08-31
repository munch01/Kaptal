package com.muncho.kaptal

je veinclass DesktopPlatform : Platform {
    override val name: String = "Desktop"
    
    override fun log(tag: String, message: String) {
        println("[$tag] $message")
    }

    override fun showToast(message: String) {
        println("TOAST: $message")
    }

    override fun openUrl(url: String) {
        java.awt.Desktop.getDesktop().browse(java.net.URI(url))
    }

    override fun openEmail(email: String, subject: String) {
        java.awt.Desktop.getDesktop().mail(java.net.URI("mailto:$email?subject=${subject.replace(" ", "%20")}"))
    }

    override fun exit() {
        System.exit(0)
    }

    override fun pickDate(initialDate: Long, onDateSelected: (Long) -> Unit) {
        // Desktop implementation could use a custom dialog
    }

    override fun pickFile(type: String, onResult: (String?) -> Unit) {
        // Implementation for desktop
    }

    override fun shareFile(content: String, fileName: String, mimeType: String) {
        // Implementation for desktop
    }

    override fun setLanguage(language: String) {
        // Implementation for desktop
    }
}

actual fun getPlatform(): Platform = DesktopPlatform()
