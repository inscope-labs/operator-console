package com.inscopelabs.buildengine.manualpipeline.domain.model

/**
 * Structured gateway guard or policy refusal preserved verbatim from Gateway A.
 */
data class GuardRefusal(
    val code: String,
    val message: String,
    val guardName: String? = null,
    val isSealRefusal: Boolean = false,
    val isHaltRefusal: Boolean = false,
)
