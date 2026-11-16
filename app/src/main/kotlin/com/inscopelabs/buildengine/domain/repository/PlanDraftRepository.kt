package com.inscopelabs.buildengine.domain.repository

import com.inscopelabs.buildengine.domain.model.PlanDraft
import com.inscopelabs.buildengine.domain.model.PlanDraftSummary

/**
 * Local storage for plan drafts.
 *
 * A draft is authoring state, not a plan: it is not executable, it is not a work order, and it
 * does not become an accepted plan until a repository change carrying it has been created and
 * merged. Drafts therefore live on the device, beside the operator, and are never a second
 * canonical plan store.
 *
 * The port is deliberately small. It is a store, not a service: the gateway owns plan state, and
 * nothing here writes to it.
 */
interface PlanDraftRepository {

    /** Every draft on the device, most recently updated first. */
    suspend fun listDrafts(): List<PlanDraftSummary>

    /** One draft, or null when it is not on the device any more. */
    suspend fun loadDraft(draftId: String): PlanDraft?

    /** Persists a draft and returns the stored copy. */
    suspend fun saveDraft(draft: PlanDraft): PlanDraft

    /** Removes a draft. Returns false when there was nothing to remove. */
    suspend fun deleteDraft(draftId: String): Boolean
}
