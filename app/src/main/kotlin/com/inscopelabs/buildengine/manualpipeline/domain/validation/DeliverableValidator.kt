package com.inscopelabs.buildengine.manualpipeline.domain.validation

import com.inscopelabs.buildengine.manualpipeline.domain.model.Deliverable
import com.inscopelabs.buildengine.manualpipeline.domain.model.SubmissionManifest
import javax.inject.Inject

/**
 * Validates supplied archive deliverables against work-order declarations (MP-005 / MP-006).
 * Blocks path traversal, denied repository paths, empty archives, and missing required items.
 */
class DeliverableValidator @Inject constructor() {

    data class Result(
        val valid: Boolean,
        val missingRequiredPaths: List<String>,
        val deniedPaths: List<String>,
        val digestMismatches: List<String>,
        val summary: String,
    )

    fun validate(
        declared: List<Deliverable>,
        manifest: SubmissionManifest?,
    ): Result {
        if (manifest == null) {
            return Result(
                valid = false,
                missingRequiredPaths = declared.filter { it.required }.map { it.relativePath },
                deniedPaths = emptyList(),
                digestMismatches = emptyList(),
                summary = "No submission archive attached yet.",
            )
        }

        val suppliedSet = manifest.suppliedPaths.map { normalize(it) }.toSet()
        val denied = suppliedSet.filter { path ->
            path.contains("..") ||
                path.startsWith("/") ||
                DENIED_PREFIXES.any { prefix -> path == prefix.trimEnd('/') || path.startsWith(prefix) }
        }

        val missing = declared.filter { it.required }
            .map { it.relativePath }
            .filter { req -> normalize(req) !in suppliedSet }

        val mismatches = declared.mapNotNull { item ->
            val expected = item.expectedSha256?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val actual = manifest.digest.fileDigests[normalize(item.relativePath)]?.lowercase()
            if (actual != null && actual != expected) item.relativePath else null
        }

        val valid = denied.isEmpty() && missing.isEmpty() && mismatches.isEmpty() && manifest.suppliedPaths.isNotEmpty()
        val summary = when {
            denied.isNotEmpty() -> "Archive contains denied repository paths: ${denied.joinToString()}"
            manifest.suppliedPaths.isEmpty() -> "Archive contains no file entries."
            missing.isNotEmpty() -> "Missing required deliverable(s): ${missing.joinToString()}"
            mismatches.isNotEmpty() -> "SHA-256 mismatch for: ${mismatches.joinToString()}"
            else -> "All ${declared.size} declared deliverables validated (${manifest.suppliedPaths.size} archive files)."
        }

        return Result(
            valid = valid,
            missingRequiredPaths = missing,
            deniedPaths = denied,
            digestMismatches = mismatches,
            summary = summary,
        )
    }

    private fun normalize(path: String): String =
        path.replace('\\', '/').trimStart('/').lowercase()

    companion object {
        private val DENIED_PREFIXES = listOf("engine/", "plans/", ".github/")
    }
}
