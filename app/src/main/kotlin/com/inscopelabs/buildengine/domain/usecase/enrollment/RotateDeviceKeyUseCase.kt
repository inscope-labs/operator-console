package com.inscopelabs.buildengine.domain.usecase.enrollment

import com.inscopelabs.buildengine.core.crypto.DeviceKeyManager
import com.inscopelabs.buildengine.core.crypto.DeviceKeySpec
import com.inscopelabs.buildengine.core.network.dto.RotateKeyRequestDto
import com.inscopelabs.buildengine.core.security.DeviceAttestation
import com.inscopelabs.buildengine.data.enrollment.DeviceEnrollmentStore
import com.inscopelabs.buildengine.data.remote.GatewayApi
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
) {
    suspend operator fun invoke(oldAlias: String, newAlias: String): String {
        require(oldAlias != newAlias) { "New key alias must differ from the current alias" }
        val active = enrollmentStore.activeRecord()
            ?: error("There is no active device enrolment to rotate")
        require(active.keyAlias == oldAlias) { "The supplied old alias is not the active enrolled key" }

        deviceKeyManager.ensureDeviceKey(DeviceKeySpec(alias = newAlias))
        val publicKey = deviceKeyManager.publicKeyBase64(newAlias)
        val chain = deviceAttestation.keyAttestationChain(newAlias)
        require(chain.isNotEmpty()) { "Rotation refused: no key-attestation chain" }

        val response = try {
            gatewayApi.rotateKey(
                RotateKeyRequestDto(
                    oldKid = active.kid,
                    newPublicKey = publicKey,
                    attestationChain = chain,
                )
            )
        } catch (error: Exception) {
            // Keep the old enrolled key untouched. The new unregistered key is safe to delete.
            runCatching { deviceKeyManager.deleteDeviceKey(newAlias) }
            throw error
        }

        require(response.status.equals("active", ignoreCase = true)) {
            "Gateway did not activate the rotated key (status=${response.status})"
        }
        require(response.kid.matches(Regex("[A-Za-z0-9_]{1,16}"))) {
            "Gateway returned an invalid key id"
        }

        val now = if (response.enrolledAt > 0) response.enrolledAt else Instant.now().epochSecond
        enrollmentStore.recordPending(response.kid, newAlias, publicKey, now)
        // If local commit fails, retain both keys; never delete the old key.
        enrollmentStore.activate(response.kid, now)
        // The gateway has retired oldKid; remove the retired private key only now.
        deviceKeyManager.deleteDeviceKey(oldAlias)
        return response.kid
    }
}
