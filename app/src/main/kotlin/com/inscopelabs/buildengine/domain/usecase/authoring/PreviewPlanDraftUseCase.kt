package com.inscopelabs.buildengine.domain.usecase.authoring

import com.inscopelabs.buildengine.domain.authoring.PlannedFile
import com.inscopelabs.buildengine.domain.authoring.PlannedFileKind
import com.inscopelabs.buildengine.domain.authoring.PlanDraftAllocator
import com.inscopelabs.buildengine.domain.model.PlanDraft
import javax.inject.Inject

/**
 * Builds the preview of a plan draft (PMA Stage 2).
 *
 * The preview is the exact set of files a submission would create, with the content each would
 * carry — so the operator sees the repository change before anything is submitted, including the
 * generated files, which are marked as generated because the tooling writes them.
 */
class PreviewPlanDraftUseCase @Inject constructor() {

    operator fun invoke(draft: PlanDraft): PlanPreview = PlanPreview(
        planId = draft.planId,
        title = draft.title,
        planNumber = draft.planNumber,
        isPlanNumberReserved = draft.isPlanNumberReserved,
        phaseCount = draft.phases.size,
        stageCount = draft.stages.size,
        taskCount = draft.tasks.size,
        files = PlanDraftAllocator.materialize(draft),
    )
}

/** What a submission would create, and what it still needs. */
data class PlanPreview(
    val planId: String,
    val title: String,
    val planNumber: String,
    val isPlanNumberReserved: Boolean,
    val phaseCount: Int,
    val stageCount: Int,
    val taskCount: Int,
    val files: List<PlannedFile>,
) {
    /** The files a submission would author, excluding the ones the tooling generates. */
    val authoredFiles: List<PlannedFile>
        get() = files.filter { it.kind != PlannedFileKind.GENERATED }
}
