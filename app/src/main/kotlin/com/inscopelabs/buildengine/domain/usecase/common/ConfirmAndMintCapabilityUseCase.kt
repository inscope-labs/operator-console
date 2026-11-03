package com.inscopelabs.buildengine.domain.usecase.common

import com.inscopelabs.buildengine.capability.CapabilityMinter
import com.inscopelabs.buildengine.capability.signing.SignedCapability
import com.inscopelabs.buildengine.core.crypto.DeviceKeyManager
import com.inscopelabs.buildengine.core.crypto.KeystoreSigner
import com.inscopelabs.buildengine.core.security.AuthTier
import com.inscopelabs.buildengine.core.security.AuthorizedSignatureResult
import com.inscopelabs.buildengine.core.security.BiometricGate
import com.inscopelabs.buildengine.core.security.BiometricPolicy
import com.inscopelabs.buildengine.core.security.PromptInfoSpec
import com.inscopelabs.buildengine.core.time.Clock
import com.inscopelabs.buildengine.core.time.SystemClock
import com.inscopelabs.buildengine.data.enrollment.DeviceEnrollmentStore
import com.inscopelabs.buildengine.domain.model.OperationRequest
import java.security.GeneralSecurityException
import java.security.Signature

/**
 * Result of confirming and minting an operator capability.
 */
sealed interface MintResult {
    data class Minted(val capability: SignedCapability) : MintResult
    data class HeadDrifted(val liveSha: String) : MintResult
    data class HeadUnavailable(val error: String) : MintResult
    data object Declined : MintResult
    data class AuthFailed(val error: String) : MintResult
    data object LockedOut : MintResult
    data object KeyInvalidated : MintResult
    data object NotEnrolled : MintResult
    data object ExpiredWhileConfirming : MintResult
    data class TierNotAllowed(val reason: String = "Auth tier not allowed") : MintResult
}

/** Thrown when remote branch head SHA has drifted. */
class HeadDriftException(message: String) : Exception(message)

/** Thrown when operator biometric authorization was cancelled or failed. */
class BiometricAuthException(message: String) : Exception(message)

/**
 * The single path by which a capability comes into existence.
 *
 * Design review §5: the operator confirms, and only then is a capability minted.
 * Every write use case goes through here — none of them may mint their own.
 *
 * Sequence:
 * 1. Verify branch head (refuse if drifted or unavailable)
 * 2. Show operation summary
 * 3. Operator confirmation (hardware_confirm with CryptoObject or in_app_confirm with typed head)
 * 4. Mint scoped capability with fresh timestamp (TTL starts at approval)
 */
class ConfirmAndMintCapabilityUseCase(
    private val capabilityMinter: CapabilityMinter,
    private val biometricGate: BiometricGate,
    private val verifyBranchHeadUseCase: VerifyBranchHeadUseCase? = null,
    private val onShowSummary: ((OperationRequest) -> Unit)? = null,
    private val deviceKeyManager: DeviceKeyManager? = null,
    private val enrollmentStore: DeviceEnrollmentStore? = null,
    private val clock: Clock? = null,
    private val signatureEngineProvider: () -> Signature = {
        Signature.getInstance("SHA256withECDSA")
    },
) {

    suspend operator fun invoke(
        request: OperationRequest,
        policy: BiometricPolicy = BiometricPolicy(),
    ): MintResult {
        // Step 1: Verify branch head if branch/task and head are present
        if (verifyBranchHeadUseCase != null) {
            val target = request.scope.branch.ifEmpty { request.task }
            if (target.isNotBlank() && request.scope.head.isNotBlank()) {
                when (val headResult = verifyBranchHeadUseCase(target, request.scope.head)) {
                    is VerifyBranchHeadResult.Match -> { /* Head verified */ }
                    is VerifyBranchHeadResult.Drifted -> return MintResult.HeadDrifted(headResult.liveSha)
                    is VerifyBranchHeadResult.Unavailable -> return MintResult.HeadUnavailable(headResult.reason)
                }
            }
        }

        // Validate consistency between request and capability scope
        require(request.op == request.scope.op) {
            "OperationRequest op (${request.op}) does not match capability scope op (${request.scope.op})"
        }

        // Step 2: Show summary to operator
        onShowSummary?.invoke(request)

        // Step 3: Determine enrollment & tier
        val activeRecord = enrollmentStore?.activeRecord()
        if (enrollmentStore != null && activeRecord == null) {
            return MintResult.NotEnrolled
        }

        val effectiveTier = activeRecord?.authTier?.let { AuthTier.fromWireValue(it) }
            ?: AuthTier.HARDWARE_CONFIRM
        val kid = activeRecord?.kid ?: "k1"
        val keyAlias = activeRecord?.keyAlias ?: "op-key-v1"

        val effectiveClock = clock ?: SystemClock()

        // Step 4: Authorize and mint based on confirmation tier
        return when (effectiveTier) {
            AuthTier.HARDWARE_CONFIRM -> {
                if (deviceKeyManager != null) {
                    val privateKey = try {
                        deviceKeyManager.getPrivateKey(keyAlias)
                    } catch (_: Exception) {
                        return MintResult.KeyInvalidated
                    }
                    if (privateKey == null) {
                        return MintResult.KeyInvalidated
                    }

                    val sigEngine = try {
                        signatureEngineProvider().apply { initSign(privateKey) }
                    } catch (_: Exception) {
                        return MintResult.KeyInvalidated
                    }

                    val promptInfo = PromptInfoSpec(
                        title = "Confirm ${request.op.name}",
                        subtitle = request.scope.branch,
                    )

                    val authResult = biometricGate.authorize(sigEngine, promptInfo, policy)
                    when (authResult) {
                        is AuthorizedSignatureResult.Cancelled -> MintResult.Declined
                        is AuthorizedSignatureResult.Lockout -> MintResult.LockedOut
                        is AuthorizedSignatureResult.KeyInvalidated -> MintResult.KeyInvalidated
                        is AuthorizedSignatureResult.HardwareUnavailable -> MintResult.AuthFailed("Hardware unavailable")
                        is AuthorizedSignatureResult.NoPromptHost -> MintResult.AuthFailed("No prompt host attached")
                        is AuthorizedSignatureResult.Failed -> MintResult.AuthFailed(authResult.reason)
                        is AuthorizedSignatureResult.Success -> {
                            val approvedAt = effectiveClock.nowEpochSeconds()
                            val now = effectiveClock.nowEpochSeconds()
                            if (now >= approvedAt + capabilityMinter.ttlSeconds) {
                                return MintResult.ExpiredWhileConfirming
                            }
                            val authorizedSigner = KeystoreSigner(kid, authResult.signature)
                            val signed = capabilityMinter.mint(
                                scope = request.scope,
                                order = request.order,
                                plan = request.plan,
                                task = request.task,
                                overrideSigner = authorizedSigner,
                                mintedAtEpochSeconds = approvedAt,
                            )
                            MintResult.Minted(signed)
                        }
                    }
                } else {
                    // Fallback path when deviceKeyManager is not provided (e.g. HMAC test suites)
                    val authenticated = biometricGate.authenticate(
                        policy = policy,
                        title = "Confirm ${request.op.name}",
                        subtitle = request.scope.branch,
                    )
                    if (!authenticated) {
                        return MintResult.Declined
                    }
                    val approvedAt = effectiveClock.nowEpochSeconds()
                    val signed = capabilityMinter.mint(
                        scope = request.scope,
                        order = request.order,
                        plan = request.plan,
                        task = request.task,
                        mintedAtEpochSeconds = approvedAt,
                    )
                    MintResult.Minted(signed)
                }
            }
            AuthTier.IN_APP_CONFIRM -> {
                val expectedPrefix = request.scope.head.take(7).lowercase()
                val typed = request.typedHeadConfirmation?.trim()?.lowercase()
                if (typed.isNullOrEmpty() || typed != expectedPrefix) {
                    return MintResult.Declined
                }
                val approvedAt = effectiveClock.nowEpochSeconds()
                val signed = capabilityMinter.mint(
                    scope = request.scope,
                    order = request.order,
                    plan = request.plan,
                    task = request.task,
                    mintedAtEpochSeconds = approvedAt,
                )
                MintResult.Minted(signed)
            }
        }
    }

    /** Helper for callers requiring SignedCapability directly. */
    suspend fun mintOrThrow(
        request: OperationRequest,
        policy: BiometricPolicy = BiometricPolicy(),
    ): SignedCapability = when (val res = invoke(request, policy)) {
        is MintResult.Minted -> res.capability
        is MintResult.HeadDrifted -> throw HeadDriftException(
            "Branch head verification failed for task '${request.task}'. Expected head '${request.scope.head}', but remote head has drifted."
        )
        is MintResult.HeadUnavailable -> throw HeadDriftException(
            "Branch head verification unavailable for task '${request.task}': ${res.error}"
        )
        is MintResult.Declined, is MintResult.AuthFailed, is MintResult.LockedOut ->
            throw BiometricAuthException("Operator confirmation was rejected or cancelled.")
        is MintResult.KeyInvalidated, is MintResult.NotEnrolled ->
            throw GeneralSecurityException("Device key is not enrolled or invalidated.")
        is MintResult.ExpiredWhileConfirming ->
            throw IllegalStateException("Confirmation expired while processing.")
        is MintResult.TierNotAllowed ->
            throw GeneralSecurityException("Auth tier not allowed: ${res.reason}")
    }
}
