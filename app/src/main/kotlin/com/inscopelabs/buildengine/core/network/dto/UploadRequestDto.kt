package com.inscopelabs.buildengine.core.network.dto

import kotlinx.serialization.Serializable

/**
 * An upload request as it goes on the wire.
 *
 * docs/gateway-contract.md §3.10 — `POST /tasks/{id}/upload`.
 *
 * The archive travels base64-encoded, with its SHA-256 alongside so the gateway can
 * check what it received against what was sent. The payload is never logged, and
 * the capability that authorises the upload never is either.
 */
@Serializable
data class UploadRequestDto(
    val plan: String = "",
    val task: String = "",
    val branch: String = "",
    val head: String = "",
    val submissionId: String = "",
    val zipSha256: String = "",
    val zipBase64: String = "",
    val capability: String = "",
)
