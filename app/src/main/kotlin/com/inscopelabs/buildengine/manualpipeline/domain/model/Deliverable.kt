package com.inscopelabs.buildengine.manualpipeline.domain.model

/**
 * Declared deliverable entry expected inside a manual submission archive.
 */
data class Deliverable(
    val relativePath: String,
    val description: String = "",
    val required: Boolean = true,
    val expectedSha256: String? = null,
    val supplied: Boolean = false,
    val suppliedSizeBytes: Long? = null,
    val suppliedSha256: String? = null,
)
