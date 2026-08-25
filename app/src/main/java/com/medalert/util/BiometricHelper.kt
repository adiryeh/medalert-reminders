package com.medalert.util

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricHelper {

    // Accept fingerprint, face, iris, or device PIN/pattern as fallback
    private const val ALLOWED = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** True if the device supports AND has enrolled biometric/credential. */
    fun isAvailable(activity: FragmentActivity): Boolean {
        val manager = BiometricManager.from(activity)
        return manager.canAuthenticate(ALLOWED) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Show the biometric prompt.
     * [onSuccess] is called on the main thread when authentication passes.
     * [onFailed] is called if the user cancels or too many attempts (should close the screen).
     */
    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailed: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // User pressed "Cancel" or system error — leave the app
                onFailed()
            }
            override fun onAuthenticationFailed() {
                // Wrong finger/face — the system handles retry UI automatically;
                // we only act after max retries (onAuthenticationError) or cancel.
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("MedAlert")
            .setSubtitle("אמת זהות כדי להמשיך")
            .setAllowedAuthenticators(ALLOWED)
            .build()

        prompt.authenticate(info)
    }
}
