package com.inscopelabs.buildengine.manualpipeline.domain.error

import com.inscopelabs.buildengine.manualpipeline.domain.model.FailureCategory

/**
 * Operator-facing error presentation derived strictly from [FailureCategory] and gateway code.
 */
data class ErrorPresentation(
    val category: FailureCategory,
    val title: String,
    val message: String,
    val gatewayCode: String? = null,
    val suggestedAction: String? = null,
    val isRetryable: Boolean = false,
) {
    companion object {
        fun from(category: FailureCategory, code: String? = null, detail: String = ""): ErrorPresentation {
            val title = when (category) {
                FailureCategory.NOT_CONFIGURED -> "Gateway Not Configured"
                FailureCategory.UNREACHABLE -> "Gateway Unreachable"
                FailureCategory.TLS_PINNING -> "TLS Pinning Failure"
                FailureCategory.AUTH -> "Authentication / Enrolment Required"
                FailureCategory.STALE -> "Stale Head or Expired Capability"
                FailureCategory.POLICY_REFUSED -> "Gateway Policy Refusal"
                FailureCategory.CONFLICT -> "State Conflict"
                FailureCategory.UNKNOWN -> "Gateway Failure"
            }
            val action = when (category) {
                FailureCategory.NOT_CONFIGURED -> "Configure the Gateway URL in Settings."
                FailureCategory.UNREACHABLE -> "Check network connectivity and retry read-only refresh."
                FailureCategory.TLS_PINNING -> "Verify certificate pins and gateway host."
                FailureCategory.AUTH -> "Check operator bearer credential and device enrolment."
                FailureCategory.STALE -> "Refresh the live branch head before confirming again."
                FailureCategory.POLICY_REFUSED -> "Inspect the gateway guard code; nothing was committed."
                FailureCategory.CONFLICT -> "Inspect live branch state or reconcile before retrying."
                FailureCategory.UNKNOWN -> "Review gateway error details and audit logs."
            }
            return ErrorPresentation(
                category = category,
                title = title,
                message = detail.ifBlank { title },
                gatewayCode = code,
                suggestedAction = action,
                isRetryable = category == FailureCategory.UNREACHABLE || category == FailureCategory.STALE,
            )
        }
    }
}
