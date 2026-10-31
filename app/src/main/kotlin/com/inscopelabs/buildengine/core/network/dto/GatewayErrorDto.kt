package com.inscopelabs.buildengine.core.network.dto

import kotlinx.serialization.Serializable

/**
 * The gateway's error body.
 *
 * docs/gateway-contract.md §4 — every rejection is
 * `{"code": "<MACHINE_READABLE_CODE>", "message": "<HUMAN_READABLE_SUMMARY>"}`.
 *
 * The `code` is from the gateway's error registry and it is what the console's
 * error mapper keys on: the four V5–V8 rejections are four different situations,
 * and the code is what keeps them apart. The `message` is for the operator and may
 * be absent.
 */
@Serializable
data class GatewayErrorDto(
    val code: String = "",
    val message: String = "",
)
