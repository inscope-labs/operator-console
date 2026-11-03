package com.inscopelabs.buildengine.domain.usecase.enrollment

import com.inscopelabs.buildengine.core.crypto.Base64Url
import com.inscopelabs.buildengine.core.crypto.DeviceKeyManager
import com.inscopelabs.buildengine.core.crypto.DeviceKeySpec
import com.inscopelabs.buildengine.core.network.dto.EnrollRequestDto
import com.inscopelabs.buildengine.core.security.AuthTier
import com.inscopelabs.buildengine.core.security.DeviceAttestation
import com.inscopelabs.buildengine.data.enrollment.DeviceEnrollmentStore
import com.inscopelabs.buildengine.data.remote.GatewayApi
import java.security.GeneralSecurityException
import java.time.Instant

/**
 * Thrown when the requested authentication tier is disallowed by gateway policy.
 */
class AuthTierNotAllowedException(message: String) : GeneralSecurityException(message)

/** Creates a hardware-bound key and registers its public half with the gateway. */
class EnrollDeviceUseCase(
    private val deviceKeyManager: DeviceKeyManager,
    private val deviceAttestation: DeviceAttestation,
    private val gatewayApi: GatewayApi,
    private val enrollmentStore: DeviceEnrollmentStore,
    private val authTierProvider: () -> AuthTier = { AuthTier.HARDWARE_CONFIRM },
) {
    suspend operator fun invoke(alias: String, overrideTier: AuthTier? = null): String {
        val safeAlias = alias.trim()
        require(safeAlias.matches(Regex("[A-Za-z0-9._-]{1,64}"))) { "Invalid device key alias" }
        val tier = overrideTier ?: authTierProvider()

        // 1. Fetch attestation challenge
        var challengeDto = gatewayApi.enrollChallenge()
        var challengeBytes = Base64Url.decode(challengeDto.challenge)

        // 2. Generate key with challenge and tier
        deviceKeyManager.ensureDeviceKey(
            DeviceKeySpec(
                alias = safeAlias,
                attestationChallenge = challengeBytes,
                authTier = tier,
            )
        )
        val publicKey = deviceKeyManager.publicKeyBase64(safeAlias)

        // 3. Read key attestation chain (must be >= 2 certificates)
        val chain = deviceAttestation.keyAttestationChain(safeAlias)
        if (chain.size < 2) {
            throw GeneralSecurityException("Enrolment refused: attestation chain size < 2 (no hardware attestation)")
        }

        // 4. Enroll with gateway (retry challenge once if ENROLL_CHALLENGE_INVALID)
        val response = try {
            gatewayApi.enroll(
                EnrollRequestDto(
                    alias = safeAlias,
                    publicKey = publicKey,
                    attestationChain = chain,
                    challenge = challengeDto.challenge,
                    authTier = tier.wireValue,
                )
            )
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            if (msg.contains("ENROLL_CHALLENGE_INVALID", ignoreCase = true)) {
                // Fetch fresh challenge and retry once
                challengeDto = gatewayApi.enrollChallenge()
                challengeBytes = Base64Url.decode(challengeDto.challenge)
                deviceKeyManager.deleteDeviceKey(safeAlias)
                deviceKeyManager.ensureDeviceKey(
                    DeviceKeySpec(
                        alias = safeAlias,
                        attestationChallenge = challengeBytes,
                        authTier = tier,
                    )
                )
                val newPubKey = deviceKeyManager.publicKeyBase64(safeAlias)
                val newChain = deviceAttestation.keyAttestationChain(safeAlias)
                gatewayApi.enroll(
                    EnrollRequestDto(
                        alias = safeAlias,
                        publicKey = newPubKey,
                        attestationChain = newChain,
                        challenge = challengeDto.challenge,
                        authTier = tier.wireValue,
                    )
                )
            } else if (msg.contains("AUTH_TIER_NOT_ALLOWED", ignoreCase = true)) {
                throw AuthTierNotAllowedException(
                    "The authentication tier '${tier.wireValue}' is not permitted in this environment. The console operates in read-only mode."
                )
            } else {
                throw e
            }
        }

        require(response.status.equals("active", ignoreCase = true)) {
            "Gateway did not activate the device key (status=${response.status})"
        }
        require(response.kid.matches(Regex("[A-Za-z0-9_]{1,16}"))) {
            "Gateway returned an invalid key id"
        }

        val now = if (response.enrolledAt > 0) response.enrolledAt else Instant.now().epochSecond
        val effectiveTier = response.authTier ?: tier.wireValue
        enrollmentStore.recordPending(response.kid, safeAlias, publicKey, now, effectiveTier)
        enrollmentStore.activate(response.kid, now)
        return response.kid
    }
}
