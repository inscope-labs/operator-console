package com.inscopelabs.buildengine.domain.authoring

import com.inscopelabs.buildengine.domain.model.PlanDraft
import com.inscopelabs.buildengine.domain.model.PlanDraftSummary
import com.inscopelabs.buildengine.domain.model.PlanPhase
import com.inscopelabs.buildengine.domain.model.PlanStage
import com.inscopelabs.buildengine.domain.model.PlanTaskDraft
import com.inscopelabs.buildengine.domain.repository.PlanDraftRepository

/**
 * An in-memory plan draft repository for the PMA tests.
 *
 * It behaves like the device store in the ways the tests depend on: drafts are keyed by their
 * draft id, and a draft that is not stored reads back as null rather than as a blank draft.
 */
class InMemoryPlanDraftRepository : PlanDraftRepository {

    private val drafts = linkedMapOf<String, PlanDraft>()

    override suspend fun listDrafts(): List<PlanDraftSummary> = drafts.values.map { draft ->
        PlanDraftSummary(
            draftId = draft.draftId,
            planId = draft.planId,
            title = draft.title,
            updatedAtEpochMs = draft.updatedAtEpochMs,
            revision = draft.revision,
            phaseCount = draft.phases.size,
            stageCount = draft.stages.size,
            taskCount = draft.tasks.size,
            isPlanNumberReserved = draft.isPlanNumberReserved,
        )
    }

    override suspend fun loadDraft(draftId: String): PlanDraft? = drafts[draftId]

    override suspend fun saveDraft(draft: PlanDraft): PlanDraft {
        drafts[draft.draftId] = draft
        return draft
    }

    override suspend fun deleteDraft(draftId: String): Boolean = drafts.remove(draftId) != null

    /** The number of drafts currently stored. */
    fun size(): Int = drafts.size
}

/** Draft builders for the PMA tests. */
object PlanDraftFixtures {

    const val DRAFT_ID: String = "draft-1"
    const val PLAN_ID: String = "my-plan"
    const val PLAN_NUMBER: String = "10001"

    fun phase(id: String = "1", name: String = "Phase 1"): PlanPhase =
        PlanPhase(id = id, name = name, goal = "goal $id")

    fun stage(id: String = "1-1", name: String = "Stage 1-1", gate: Boolean = false): PlanStage =
        PlanStage(id = id, name = name, goal = "goal $id", gate = gate)

    fun task(
        localId: String = "task-local-1",
        title: String = "Do the thing",
        stageId: String = "1-1",
        ordinal: Int = 0,
        acceptance: List<String> = listOf("the tests pass"),
        deliverables: List<String> = listOf("src/main.ts"),
        companions: List<String> = emptyList(),
        blockedBy: List<com.inscopelabs.buildengine.domain.model.PlanDependency> = emptyList(),
        context: List<com.inscopelabs.buildengine.domain.model.PlanContextRef> = emptyList(),
    ): PlanTaskDraft = PlanTaskDraft(
        localId = localId,
        title = title,
        stageId = stageId,
        ordinal = ordinal,
        acceptance = acceptance,
        deliverables = deliverables,
        companions = companions,
        blockedBy = blockedBy,
        context = context,
    )

    fun draft(
        draftId: String = DRAFT_ID,
        planId: String = PLAN_ID,
        title: String = "My plan",
        planNumber: String = PLAN_NUMBER,
        phases: List<PlanPhase> = listOf(phase()),
        stages: List<PlanStage> = listOf(stage()),
        tasks: List<PlanTaskDraft> = listOf(task()),
        deliverableRoots: List<String> = emptyList(),
        deliverablePatterns: List<String> = emptyList(),
        coverageExemptions: List<com.inscopelabs.buildengine.domain.model.PlanCoverageExemption> = emptyList(),
        updatedAtEpochMs: Long = 1L,
    ): PlanDraft = PlanDraft(
        draftId = draftId,
        planId = planId,
        title = title,
        planNumber = planNumber,
        deliverableRoots = deliverableRoots,
        deliverablePatterns = deliverablePatterns,
        phases = phases,
        stages = stages,
        tasks = tasks,
        coverageExemptions = coverageExemptions,
        revision = 1,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = updatedAtEpochMs,
    )
}
