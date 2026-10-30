package com.inscopelabs.buildengine.domain.usecase.common

import com.inscopelabs.buildengine.capability.CapabilityMinter
import com.inscopelabs.buildengine.capability.signing.SignedCapability
import com.inscopelabs.buildengine.core.security.BiometricGate
import com.inscopelabs.buildengine.core.security.BiometricPolicy
import com.inscopelabs.buildengine.domain.model.OperationRequest

/**
 * The single path by which a capability comes into existence.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §5: the operator confirms, and only then is a capability minted.
 * Every write use case goes through here — none of them may mint their own.
 */
class ConfirmAndMintCapabilityUseCase(
    private val capabilityMinter: CapabilityMinter,
    private val biometricGate: BiometricGate,
) {

    suspend operator fun invoke(
        request: OperationRequest,
        policy: BiometricPolicy = BiometricPolicy(),
    ): SignedCapability = TODO("SCAFFOLD: authenticate, then mint the scoped capability.")
}
