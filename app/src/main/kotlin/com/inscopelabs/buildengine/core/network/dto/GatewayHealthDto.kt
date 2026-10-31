package com.inscopelabs.buildengine.core.network.dto

import kotlinx.serialization.Serializable

/**
 * The gateway's liveness and environment probe.
 *
 * docs/gateway-contract.md §3.1 — `GET /health`.
 *
 * `environment` is the security-relevant field: it is `"test"` or `"production"`, and
 * the Lab engine strictly refuses to run against any gateway where it is not
 * `"test"`. A console that ignored it would let a Lab mutation land on a
 * production gateway, so the value is carried all the way to the UI rather than
 * being collapsed into a boolean here.
 */
@Serializable
data class GatewayHealthDto(
    val status: String = "",
    val environment: String = "",
    val version: String = "",
    val timestamp: Long = 0L,
)
