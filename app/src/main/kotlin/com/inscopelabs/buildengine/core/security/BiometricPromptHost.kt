package com.inscopelabs.buildengine.core.security

import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

/**
 * Display specifications for the biometric prompt.
 *
 * Prompt text states the operation and short branch name; never secrets.
 */
data class PromptInfoSpec(
    val title: String,
    val subtitle: String,
    val description: String = "",
)

/**
 * Outcome from displaying [BiometricPrompt].
 */
sealed interface BiometricPromptResult {
    data class Success(val cryptoObject: BiometricPrompt.CryptoObject?) : BiometricPromptResult
    data object Cancelled : BiometricPromptResult
    data object Lockout : BiometricPromptResult
    data object KeyInvalidated : BiometricPromptResult
    data class Error(val errorCode: Int, val errString: CharSequence) : BiometricPromptResult
}

/**
 * Host interface that displays the system [BiometricPrompt] on behalf of [BiometricGate].
 */
interface BiometricPromptHost {
    suspend fun showPrompt(
        info: PromptInfoSpec,
        cryptoObject: BiometricPrompt.CryptoObject?,
        policy: BiometricPolicy,
    ): BiometricPromptResult

    companion object {
        private var currentHostRef: WeakReference<BiometricPromptHost>? = null

        fun registerHost(host: BiometricPromptHost) {
            currentHostRef = WeakReference(host)
        }

        fun unregisterHost(host: BiometricPromptHost) {
            if (currentHostRef?.get() === host) {
                currentHostRef = null
            }
        }

        val currentHost: BiometricPromptHost?
            get() = currentHostRef?.get()
    }
}

/**
 * Default implementation of [BiometricPromptHost] using a [FragmentActivity].
 */
class ActivityBiometricPromptHost(
    private val activity: FragmentActivity,
) : BiometricPromptHost {

    override suspend fun showPrompt(
        info: PromptInfoSpec,
        cryptoObject: BiometricPrompt.CryptoObject?,
        policy: BiometricPolicy,
    ): BiometricPromptResult = suspendCancellableCoroutine { continuation ->
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (continuation.isActive) {
                    continuation.resume(BiometricPromptResult.Success(result.cryptoObject))
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (continuation.isActive) {
                    val outcome = when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_CANCELED -> BiometricPromptResult.Cancelled
                        BiometricPrompt.ERROR_LOCKOUT,
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> BiometricPromptResult.Lockout
                        else -> BiometricPromptResult.Error(errorCode, errString)
                    }
                    continuation.resume(outcome)
                }
            }

            override fun onAuthenticationFailed() {
                // Soft failure; BiometricPrompt keeps listening until success, error, or cancel.
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(info.title)
            .setSubtitle(info.subtitle)
            .setAllowedAuthenticators(policy.allowedAuthenticators)

        if (info.description.isNotEmpty()) {
            promptInfoBuilder.setDescription(info.description)
        }

        if (!policy.allowsDeviceCredential()) {
            promptInfoBuilder.setNegativeButtonText("Cancel")
        }

        val promptInfo = promptInfoBuilder.build()

        continuation.invokeOnCancellation {
            prompt.cancelAuthentication()
        }

        try {
            if (cryptoObject != null) {
                prompt.authenticate(promptInfo, cryptoObject)
            } else {
                prompt.authenticate(promptInfo)
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            if (continuation.isActive) continuation.resume(BiometricPromptResult.KeyInvalidated)
        } catch (e: Exception) {
            if (continuation.isActive) continuation.resume(BiometricPromptResult.Error(-1, e.message ?: "Authentication failed"))
        }
    }
}
