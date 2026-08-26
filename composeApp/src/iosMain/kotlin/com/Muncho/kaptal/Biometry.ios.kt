package com.muncho.kaptal

class IosBiometryManager : BiometryManager {
    override fun canAuthenticate(): Boolean = false
    override fun authenticate(
        title: String,
        subtitle: String,
        negativeButtonText: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        onSuccess()
    }
}

actual fun getBiometryManager(): BiometryManager = IosBiometryManager()
