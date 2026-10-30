package com.inscopelabs.buildengine.core.security

/**
 * Produces a device attestation statement for the gateway.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §13: attestation is what makes the transport mutually authenticated
 * rather than merely TLS.
 */
class DeviceAttestation {

    suspend fun requestAttestation(nonce: String): ByteArray =
        TODO("SCAFFOLD: request a Play Integrity / key-attestation statement.")

    suspend fun keyAttestationChain(alias: String): ByteArray =
        TODO("SCAFFOLD: export the device key attestation chain.")
}
