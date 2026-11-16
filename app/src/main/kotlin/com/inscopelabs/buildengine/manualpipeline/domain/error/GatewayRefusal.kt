package com.inscopelabs.buildengine.manualpipeline.domain.error

/**
 * Preserves the gateway's exact refusal code and message without flattening or rewriting.
 */
data class GatewayRefusal(
    val code: String,
    val message: String,
    val httpStatus: Int? = null,
    val auditRef: String? = null,
) {
    val isSealRefusal: Boolean
        get() = code.equals("SUBMISSION_IMMUTABLE", ignoreCase = true) ||
            code.equals("SEALED", ignoreCase = true)

    val isHaltRefusal: Boolean
        get() = code.equals("HALT_ACTIVE", ignoreCase = true)

    val isHeadDrift: Boolean
        get() = code.equals("HEAD_DRIFT", ignoreCase = true) ||
            code.equals("HEAD_MOVED", ignoreCase = true)

    val isReplay: Boolean
        get() = code.equals("NONCE_REPLAYED", ignoreCase = true)
}
