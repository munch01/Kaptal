package com.Muncho.kaptal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialisation des abstractions plateforme
        appContext = applicationContext
        currentActivity = this as androidx.fragment.app.FragmentActivity // Si nécessaire pour la biométrie
        
        setContent {
            App()
        }
    }
}
