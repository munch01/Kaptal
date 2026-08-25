package com.Muncho.kaptal

import android.content.Context
import android.util.Log
import android.widget.Toast

class AndroidPlatform(private val context: Context) : Platform {
    override val name: String = "Android ${android.os.Build.VERSION.SDK_INT}"
    
    override fun log(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    override fun openUrl(url: String) {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    override fun openEmail(email: String, subject: String) {
        val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
            data = android.net.Uri.parse("mailto:$email")
            putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            showToast("Erreur : aucune appli d'email")
        }
    }

    override fun exit() {
        (context as? android.app.Activity)?.finish()
    }
}

lateinit var appContext: Context

actual fun getPlatform(): Platform = AndroidPlatform(appContext)
