package com.inscopelabs.buildengine.core.network

/**
 * One gateway HTTP exchange, suitable for the operator diagnostics panel.
 *
 * Only method, path, and outcome are recorded — never tokens, capabilities,
 * signatures, nonces, or response bodies (design review §8).
 */
data class GatewayActivityEvent(
    val method: String,
    val path: String,
    val outcome: Outcome,
    val statusCode: Int? = null,
    val detail: String? = null,
    val epochMillis: Long,
) {
    enum class Outcome {
        SUCCESS,
        REFUSED,
        NOT_CONFIGURED,
        UNREACHABLE,
        PROTOCOL_ERROR,
        ERROR,
    }

    /** Single-line redacted summary for the diagnostics panel and share export. */
    fun summaryLine(): String {
        val code = statusCode?.let { " $it" }.orEmpty()
        val extra = detail?.let { " — $it" }.orEmpty()
        return "$method $path → ${outcome.name.lowercase()}$code$extra"
    }
}
