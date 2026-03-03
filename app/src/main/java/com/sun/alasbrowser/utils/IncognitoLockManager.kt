package com.sun.alasbrowser.utils

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.sun.alasbrowser.data.BrowserPreferences

object IncognitoLockManager {

    fun authenticateForIncognito(
        activity: FragmentActivity,
        preferences: BrowserPreferences,
        onSuccess: () -> Unit,
        onFailure: () -> Unit = {}
    ) {
        if (!preferences.incognitoLockEnabled) {
            // No lock enabled, proceed directly
            onSuccess()
            return
        }

        val biometricManager = BiometricManager.from(activity)

        when (biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                val executor = ContextCompat.getMainExecutor(activity)
                val biometricPrompt = BiometricPrompt(
                    activity,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            onSuccess()
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            // Don't call onFailure here, wait for error
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                                errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                                // For other errors, allow access anyway
                                onSuccess()
                            } else {
                                onFailure()
                            }
                        }
                    }
                )

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock Incognito Mode")
                    .setSubtitle("Authenticate to access incognito browsing")
                    .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                    .build()

                biometricPrompt.authenticate(promptInfo)
            }
            else -> {
                // Biometric not available, but lock is enabled
                // Fall back to allowing access (could implement PIN here)
                onSuccess()
            }
        }
    }
}
