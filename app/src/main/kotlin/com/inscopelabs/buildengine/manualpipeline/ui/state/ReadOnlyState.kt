package com.inscopelabs.buildengine.manualpipeline.ui.state

import com.inscopelabs.buildengine.manualpipeline.domain.model.Environment

/**
 * Summarizes why the console may currently be locked in read-only mode (I-11 / authority / halt).
 */
data class ReadOnlyState(
    val isReadOnly: Boolean = false,
    val expectedEnvironment: Environment = Environment.UNPROBED,
    val reportedEnvironment: Environment = Environment.UNPROBED,
    val environmentMismatch: Boolean = false,
    val isHalted: Boolean = false,
    val notEnrolled: Boolean = false,
    val missingCredential: Boolean = false,
    val reason: String? = null,
)
