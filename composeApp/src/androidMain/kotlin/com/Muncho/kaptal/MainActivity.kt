package com.muncho.kaptal

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            onFilePickedCallback?.invoke(uri?.toString())
        }

        // Initialisation des abstractions plateforme
        appContext = applicationContext
        currentActivity = this
        
        setContent {
            App()
        }
    }
}
