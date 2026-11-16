package com.inscopelabs.buildengine.manualpipeline.domain.model

/**
 * Acceptance criterion parsed from a work order specification.
 */
data class AcceptanceCriterion(
    val id: String,
    val description: String,
    val verifiedLocally: Boolean = false,
)
