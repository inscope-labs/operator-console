package com.inscopelabs.buildengine.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import java.security.Signature

/**
 * Outcome from [BiometricGate.authorize].
 */
sealed interface AuthorizedSignatureResult {
    data class Success(val signature: Signature) : AuthorizedSignatureResult
    data object Cancelled : AuthorizedSignatureResult
    data object Lockout : AuthorizedSignatureResult
    data object NoPromptHost : AuthorizedSignatureResult
    data object HardwareUnavailable : AuthorizedSignatureResult
    data object KeyInvalidated : AuthorizedSignatureResult
    data class Failed(val reason: String) : AuthorizedSignatureResult
}

/**
 * Gates capability minting behind an explicit operator confirmation.
 *
 * Design review §5, §13: no capability exists until the operator has confirmed. The
 * gate is CryptoObject-bound so the unlock authorises the signing operation
 * itself, not a general-purpose "the screen was unlocked" flag.
 */
open class BiometricGate(
    val context: Context? = null,
    private val authenticator: (suspend (BiometricPolicy, BiometricPrompt.CryptoObject?) -> Boolean)? = null,
) {
    /** Registered prompt host for UI interactions. */
    var promptHost: BiometricPromptHost? = null

    /** Override for testing canAuthenticate results. */
    var simulatedCanAuthenticateResult: Int? = null

    /** Mirrors BiometricManager.canAuthenticate() for the given policy. */
    open fun canAuthenticate(policy: BiometricPolicy = BiometricPolicy()): Int {
        simulatedCanAuthenticateResult?.let { return it }
        val ctx = context ?: return BiometricManager.BIOMETRIC_SUCCESS
        return try {
            BiometricManager.from(ctx).canAuthenticate(policy.allowedAuthenticators)
        } catch (_: Exception) {
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE
        }
    }

    /** Authorizes an initialized Signature object via the BiometricPrompt CryptoObject. */
    open suspend fun authorize(
        signature: Signature,
        info: PromptInfoSpec,
        policy: BiometricPolicy = BiometricPolicy(),
    ): AuthorizedSignatureResult {
        if (authenticator != null) {
            val ok = authenticator.invoke(policy, BiometricPrompt.CryptoObject(signature))
            return if (ok) AuthorizedSignatureResult.Success(signature) else AuthorizedSignatureResult.Cancelled
        }

        val host = promptHost ?: BiometricPromptHost.currentHost
            ?: return AuthorizedSignatureResult.NoPromptHost

        val canAuth = canAuthenticate(policy)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            return AuthorizedSignatureResult.HardwareUnavailable
        }

        val cryptoObject = BiometricPrompt.CryptoObject(signature)
        return when (val res = host.showPrompt(info, cryptoObject, policy)) {
            is BiometricPromptResult.Success -> {
                val returnedSig = res.cryptoObject?.signature ?: signature
                AuthorizedSignatureResult.Success(returnedSig)
            }
            is BiometricPromptResult.Cancelled -> AuthorizedSignatureResult.Cancelled
            is BiometricPromptResult.Lockout -> AuthorizedSignatureResult.Lockout
            is BiometricPromptResult.KeyInvalidated -> AuthorizedSignatureResult.KeyInvalidated
            is BiometricPromptResult.Error -> {
                when (res.errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_CANCELED -> AuthorizedSignatureResult.Cancelled
                    BiometricPrompt.ERROR_LOCKOUT,
                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> AuthorizedSignatureResult.Lockout
                    BiometricPrompt.ERROR_HW_UNAVAILABLE,
                    BiometricPrompt.ERROR_HW_NOT_PRESENT,
                    BiometricPrompt.ERROR_NO_BIOMETRICS,
                    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> AuthorizedSignatureResult.HardwareUnavailable
                    else -> AuthorizedSignatureResult.Failed("${res.errorCode}: ${res.errString}")
                }
            }
        }
    }

    /** Returns true when the operator authenticated under [policy]. */
    open suspend fun authenticate(
        policy: BiometricPolicy = BiometricPolicy(),
        cryptoObject: BiometricPrompt.CryptoObject? = null,
        title: String = "Operator Authorization",
        subtitle: String = "Authenticate to sign operator capability",
    ): Boolean {
        if (authenticator != null) {
            return authenticator.invoke(policy, cryptoObject)
        }
        val host = promptHost ?: BiometricPromptHost.currentHost
        if (host != null) {
            val res = host.showPrompt(PromptInfoSpec(title, subtitle), cryptoObject, policy)
            return res is BiometricPromptResult.Success
        }

        val canAuth = canAuthenticate(policy)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            return false
        }

        return false
    }
}
