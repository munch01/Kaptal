package com.muncho.kaptal

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

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
        currentActivity?.finish()
    }

    override fun pickDate(initialDate: Long, onDateSelected: (Long) -> Unit) {
        val activity = currentActivity ?: return
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = initialDate }
        android.app.DatePickerDialog(
            activity,
            { _, year, month, day ->
                val resultCal = java.util.Calendar.getInstance().apply {
                    set(year, month, day)
                }
                onDateSelected(resultCal.timeInMillis)
            },
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH),
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        ).show()
    }
    override fun pickFile(type: String, onResult: (String?) -> Unit) {
        filePickerLauncher?.launch(type)
        onFilePickedCallback = onResult
    }

    override fun shareFile(content: String, fileName: String, mimeType: String) {
        try {
            val file = java.io.File(context.cacheDir, fileName)
            file.writeText(content)
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Partager le fichier").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            showToast("Erreur lors du partage : ${e.message}")
        }
    }

    override fun setLanguage(language: String) {
        val localeCode = when (language) {
            "English" -> "en"
            "Español" -> "es"
            else -> "fr"
        }
        val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(localeCode)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }
}

lateinit var appContext: Context
var filePickerLauncher: androidx.activity.result.ActivityResultLauncher<String>? = null
var onFilePickedCallback: ((String?) -> Unit)? = null

actual fun getPlatform(): Platform = AndroidPlatform(appContext)
