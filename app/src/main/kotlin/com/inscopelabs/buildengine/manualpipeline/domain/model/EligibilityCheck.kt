package com.inscopelabs.buildengine.manualpipeline.domain.model

/**
 * Individual local advisory check contributing to an [EligibilityVerdict].
 */
data class EligibilityCheck(
    val name: String,
    val passed: Boolean,
    val reason: String,
)
