package com.inscopelabs.buildengine.manualpipeline.domain.usecase

import com.inscopelabs.buildengine.manualpipeline.domain.model.ArchiveDigest
import com.inscopelabs.buildengine.manualpipeline.domain.model.Deliverable
import com.inscopelabs.buildengine.manualpipeline.domain.model.SubmissionManifest
import com.inscopelabs.buildengine.manualpipeline.domain.model.WorkOrder
import com.inscopelabs.buildengine.manualpipeline.domain.validation.DeliverableValidator
import javax.inject.Inject

/**
 * MP-005: Reconciles work-order declared deliverables against supplied `.zip` archive entries
 * and attaches non-secret archive digest metadata without altering the immutable `.zip` bytes.
 */
class ManageDeliverablesUseCase @Inject constructor(
    private val deliverableValidator: DeliverableValidator,
) {

    data class ManagedDeliverables(
        val deliverables: List<Deliverable>,
        val manifest: SubmissionManifest?,
        val validation: DeliverableValidator.Result,
    )

    operator fun invoke(
        workOrder: WorkOrder,
        submissionId: String,
        suppliedPaths: List<String>,
        digest: ArchiveDigest?,
        sourceDisplayName: String = "",
    ): ManagedDeliverables {
        if (digest == null) {
            val emptyResult = deliverableValidator.validate(workOrder.deliverables, null)
            return ManagedDeliverables(
                deliverables = workOrder.deliverables,
                manifest = null,
                validation = emptyResult,
            )
        }

        val normalizedSupplied = suppliedPaths.associateBy { normalizePath(it) }
        val updatedDeliverables = workOrder.deliverables.map { declared ->
            val key = normalizePath(declared.relativePath)
            val matchedPath = normalizedSupplied[key]
            declared.copy(
                supplied = matchedPath != null,
                suppliedSizeBytes = matchedPath?.let { digest.fileSizesBytes[it] ?: digest.fileSizesBytes[key] },
                suppliedSha256 = matchedPath?.let { digest.fileDigests[it] ?: digest.fileDigests[key] },
            )
        }

        val declaredSet = workOrder.deliverables.map { normalizePath(it.relativePath) }.toSet()
        val missing = workOrder.deliverables
            .filter { normalizePath(it.relativePath) !in normalizedSupplied.keys }
            .map { it.relativePath }
        val extra = suppliedPaths.filter { normalizePath(it) !in declaredSet }

        val manifest = SubmissionManifest(
            submissionId = submissionId,
            taskId = workOrder.taskId,
            declaredPaths = workOrder.deliverables.map { it.relativePath },
            suppliedPaths = suppliedPaths,
            missingDeclaredPaths = missing,
            extraSuppliedPaths = extra,
            digest = digest,
            sourceDisplayName = sourceDisplayName,
        )

        val validation = deliverableValidator.validate(updatedDeliverables, manifest)
        return ManagedDeliverables(
            deliverables = updatedDeliverables,
            manifest = manifest,
            validation = validation,
        )
    }

    private fun normalizePath(path: String): String =
        path.replace('\\', '/').trimStart('/').lowercase()
}
