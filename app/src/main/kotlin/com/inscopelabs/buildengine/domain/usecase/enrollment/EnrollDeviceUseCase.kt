package com.inscopelabs.buildengine.domain.usecase.enrollment

import com.inscopelabs.buildengine.core.crypto.DeviceKeyManager
import com.inscopelabs.buildengine.core.crypto.DeviceKeySpec
import com.inscopelabs.buildengine.core.network.dto.EnrollRequestDto
import com.inscopelabs.buildengine.data.enrollment.DeviceEnrollmentStore
import com.inscopelabs.buildengine.data.remote.GatewayApi
import com.inscopelabs.buildengine.core.security.DeviceAttestation
import java.security.GeneralSecurityException
import java.time.Instant

/** Creates a hardware-bound key and registers its public half with the gateway. */
class EnrollDeviceUseCase(
    private val deviceKeyManager: DeviceKeyManager,
    private val deviceAttestation: DeviceAttestation,
    private val gatewayApi: GatewayApi,
    private val enrollmentStore: DeviceEnrollmentStore,
) {
    suspend operator fun invoke(alias: String): String {
        val safeAlias = alias.trim()
        require(safeAlias.matches(Regex("[A-Za-z0-9._-]{1,64}"))) { "Invalid device key alias" }

        deviceKeyManager.ensureDeviceKey(DeviceKeySpec(alias = safeAlias))
        val publicKey = deviceKeyManager.publicKeyBase64(safeAlias)
        val chain = deviceAttestation.keyAttestationChain(safeAlias)
        if (chain.isEmpty()) throw GeneralSecurityException("Enrolment refused: no key-attestation chain")

        val response = gatewayApi.enroll(
            EnrollRequestDto(
                alias = safeAlias,
                publicKey = publicKey,
                attestationChain = chain,
            )
        )
        require(response.status.equals("active", ignoreCase = true)) {
            "Gateway did not activate the device key (status=${response.status})"
        }
        require(response.kid.matches(Regex("[A-Za-z0-9_]{1,16}"))) {
            "Gateway returned an invalid key id"
        }

        val now = if (response.enrolledAt > 0) response.enrolledAt else Instant.now().epochSecond
        enrollmentStore.recordPending(response.kid, safeAlias, publicKey, now)
        enrollmentStore.activate(response.kid, now)
        return response.kid
    }
}
