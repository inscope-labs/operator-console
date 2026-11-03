package com.inscopelabs.buildengine.domain.usecase.enrollment

import com.inscopelabs.buildengine.core.crypto.Base64Url
import com.inscopelabs.buildengine.core.crypto.DeviceKeyManager
import com.inscopelabs.buildengine.core.crypto.DeviceKeySpec
import com.inscopelabs.buildengine.core.network.dto.RotateKeyRequestDto
import com.inscopelabs.buildengine.core.security.AuthTier
import com.inscopelabs.buildengine.core.security.DeviceAttestation
import com.inscopelabs.buildengine.data.enrollment.DeviceEnrollmentStore
import com.inscopelabs.buildengine.data.remote.GatewayApi
import java.security.GeneralSecurityException
import java.time.Instant

/**
 * Rotates a key without destroying the old local key until the gateway and local
 * active-record transition have both succeeded.
 */
class RotateDeviceKeyUseCase(
    private val deviceKeyManager: DeviceKeyManager,
    private val deviceAttestation: DeviceAttestation,
    private val gatewayApi: GatewayApi,
    private val enrollmentStore: DeviceEnrollmentStore,
    private val authTierProvider: () -> AuthTier = { AuthTier.HARDWARE_CONFIRM },
) {
    suspend operator fun invoke(oldAlias: String, newAlias: String, overrideTier: AuthTier? = null): String {
        require(oldAlias != newAlias) { "New key alias must differ from the current alias" }
        val active = enrollmentStore.activeRecord()
            ?: error("There is no active device enrolment to rotate")
        require(active.keyAlias == oldAlias) { "The supplied old alias is not the active enrolled key" }

        val tier = overrideTier ?: authTierProvider()
        var challengeDto = gatewayApi.enrollChallenge()
        var challengeBytes = Base64Url.decode(challengeDto.challenge)

        deviceKeyManager.ensureDeviceKey(
            DeviceKeySpec(
                alias = newAlias,
                attestationChallenge = challengeBytes,
                authTier = tier,
            )
        )
        val publicKey = deviceKeyManager.publicKeyBase64(newAlias)
        val chain = deviceAttestation.keyAttestationChain(newAlias)
        require(chain.size >= 2) { "Rotation refused: key attestation chain size < 2 (no hardware attestation)" }

        val response = try {
            gatewayApi.rotateKey(
                RotateKeyRequestDto(
                    oldKid = active.kid,
                    newPublicKey = publicKey,
                    attestationChain = chain,
                    challenge = challengeDto.challenge,
                    authTier = tier.wireValue,
                )
            )
        } catch (error: Exception) {
            val msg = error.message.orEmpty()
            if (msg.contains("ENROLL_CHALLENGE_INVALID", ignoreCase = true)) {
                // Retry once
                runCatching { deviceKeyManager.deleteDeviceKey(newAlias) }
                challengeDto = gatewayApi.enrollChallenge()
                challengeBytes = Base64Url.decode(challengeDto.challenge)
                deviceKeyManager.ensureDeviceKey(
                    DeviceKeySpec(
                        alias = newAlias,
                        attestationChallenge = challengeBytes,
                        authTier = tier,
                    )
                )
                val newPubKey = deviceKeyManager.publicKeyBase64(newAlias)
                val newChain = deviceAttestation.keyAttestationChain(newAlias)
                gatewayApi.rotateKey(
                    RotateKeyRequestDto(
                        oldKid = active.kid,
                        newPublicKey = newPubKey,
                        attestationChain = newChain,
                        challenge = challengeDto.challenge,
                        authTier = tier.wireValue,
                    )
                )
            } else if (msg.contains("AUTH_TIER_NOT_ALLOWED", ignoreCase = true)) {
                runCatching { deviceKeyManager.deleteDeviceKey(newAlias) }
                throw AuthTierNotAllowedException(
                    "The authentication tier '${tier.wireValue}' is not permitted in this environment. The console operates in read-only mode."
                )
            } else {
                runCatching { deviceKeyManager.deleteDeviceKey(newAlias) }
                throw error
            }
        }

        require(response.status.equals("active", ignoreCase = true)) {
            "Gateway did not activate the rotated key (status=${response.status})"
        }
        require(response.kid.matches(Regex("[A-Za-z0-9_]{1,16}"))) {
            "Gateway returned an invalid key id"
        }

        val now = if (response.enrolledAt > 0) response.enrolledAt else Instant.now().epochSecond
        val effectiveTier = response.authTier ?: tier.wireValue
        enrollmentStore.recordPending(response.kid, newAlias, publicKey, now, effectiveTier)
        enrollmentStore.activate(response.kid, now)
        deviceKeyManager.deleteDeviceKey(oldAlias)
        return response.kid
    }
}
