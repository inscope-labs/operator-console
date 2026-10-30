package com.inscopelabs.buildengine.core.network

import io.ktor.client.HttpClient

/**
 * The HTTP transport to the gateway.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §8: every request must produce an audit record, but credentials,
 * signatures and nonces must never appear in a log line. See util/Redactor.kt.
 */
class GatewayHttpClient(private val client: HttpClient) {

    suspend fun get(path: String): String = TODO("SCAFFOLD: GET against the gateway.")

    suspend fun post(path: String, body: String): String =
        TODO("SCAFFOLD: POST against the gateway.")

    suspend fun request(method: String, path: String, body: String?): String =
        TODO("SCAFFOLD: generic gateway request.")
}
