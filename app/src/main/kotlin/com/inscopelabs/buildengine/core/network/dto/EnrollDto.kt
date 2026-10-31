package com.inscopelabs.buildengine.core.network.dto

import kotlinx.serialization.Serializable

/**
 * Device enrolment and key rotation, as they go on the wire.
 *
 * docs/gateway-contract.md §3.15–§3.16 — `POST /enroll` and `POST /enroll/rotate`.
 *
 * The only thing that leaves the device during enrolment is the public key and the
 * Android Keystore key-attestation chain. The gateway verifies the attestation
 * extension in the root certificate to confirm that the private key was generated
 * inside a hardware-backed Secure Element or TEE (ADR 0006, D-T4).
 *
 * Play Integrity attestation is deferred and is not part of this request.
 */
@Serializable
data class EnrollRequestDto(
    val alias: String = "",
    val publicKey: String = "",
    val attestationChain: List<String> = emptyList(),
)

@Serializable
data class RotateKeyRequestDto(
    val oldKid: String = "",
    val newPublicKey: String = "",
    val attestationChain: List<String> = emptyList(),
)

@Serializable
data class EnrollResponseDto(
    val kid: String = "",
    val status: String = "",
    val enrolledAt: Long = 0L,
)
