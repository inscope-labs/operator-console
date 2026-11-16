package com.inscopelabs.buildengine.manualpipeline.domain.model

/**
 * Optional or supporting companion file referenced by a work order (e.g. REPORT.md, diff notes).
 */
data class CompanionFile(
    val relativePath: String,
    val purpose: String = "",
    val present: Boolean = false,
)
