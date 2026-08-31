package com.muncho.kaptal

class DesktopBiometryManager : BiometryManager {
    override fun canAuthenticate(): Boolean = false // Pas de support natif direct simple ici

    override fun authenticate(
        title: String,
        subtitle: String,
        negativeButtonText: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        // Sur desktop, on pourrait afficher un dialogue de mot de passe.
        // Pour l'instant, on considère que c'est toujours un succès ou non supporté.
        onSuccess() 
    }
}

actual fun getBiometryManager(): BiometryManager = DesktopBiometryManager()
