package com.muncho.kaptal

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        currentActivity = this
        appContext = applicationContext
        
        PDFBoxResourceLoader.init(applicationContext)

        filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            onFilePickedCallback?.invoke(uri?.toString())
        }

        // Appliquer la langue enregistrée au démarrage
        val savedLanguage = getSettings().getString("selected_language", "Français")
        applyLanguage(savedLanguage)

        setContent {
            App()
        }
    }

    private fun applyLanguage(language: String) {
        val localeCode = when (language) {
            "English" -> "en"
            "Español" -> "es"
            else -> "fr"
        }
        val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(localeCode)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }
}
