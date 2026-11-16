package com.inscopelabs.buildengine.manualpipeline.domain.error

import com.inscopelabs.buildengine.manualpipeline.domain.model.FailureCategory

/**
 * Sealed exception hierarchy for manual pipeline failures.
 */
sealed class ManualPipelineException(
    message: String,
    val category: FailureCategory,
    cause: Throwable? = null,
) : Exception(message, cause) {

    class NotConfigured(message: String) :
        ManualPipelineException(message, FailureCategory.NOT_CONFIGURED)

    class Unreachable(message: String, cause: Throwable? = null) :
        ManualPipelineException(message, FailureCategory.UNREACHABLE, cause)

    class TlsPinning(message: String, cause: Throwable? = null) :
        ManualPipelineException(message, FailureCategory.TLS_PINNING, cause)

    class Auth(message: String, val code: String = "AUTH") :
        ManualPipelineException(message, FailureCategory.AUTH)

    class Stale(message: String, val code: String = "HEAD_DRIFT") :
        ManualPipelineException(message, FailureCategory.STALE)

    class PolicyRefused(val refusal: GatewayRefusal) :
        ManualPipelineException("${refusal.code}: ${refusal.message}", FailureCategory.POLICY_REFUSED)

    class Conflict(val refusal: GatewayRefusal) :
        ManualPipelineException("${refusal.code}: ${refusal.message}", FailureCategory.CONFLICT)

    class Validation(message: String) :
        ManualPipelineException(message, FailureCategory.POLICY_REFUSED)
}
