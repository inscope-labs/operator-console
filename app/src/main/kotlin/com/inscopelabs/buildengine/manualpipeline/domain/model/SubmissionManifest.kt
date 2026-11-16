package com.inscopelabs.buildengine.manualpipeline.domain.model

/**
 * Manifest comparing work-order declared deliverables against supplied archive entries.
 */
data class SubmissionManifest(
    val submissionId: String,
    val taskId: String,
    val declaredPaths: List<String>,
    val suppliedPaths: List<String>,
    val missingDeclaredPaths: List<String>,
    val extraSuppliedPaths: List<String>,
    val digest: ArchiveDigest,
    val sourceDisplayName: String = "",
) {
    val matchesDeclared: Boolean get() = missingDeclaredPaths.isEmpty()
}
