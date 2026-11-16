package com.inscopelabs.buildengine.domain.usecase.authoring

import com.inscopelabs.buildengine.domain.model.PlanDraft
import com.inscopelabs.buildengine.domain.repository.PlanDraftRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Creates a new plan draft (PMA Stage 1).
 *
 * The draft gets a local identity and a plan id. The plan id follows the canonical rule —
 * lowercase letters, digits and dashes, beginning with a letter or a digit — and is made unique
 * among the drafts on this device.
 *
 * Uniqueness on the device is a convenience, not authority: only the Build Engine's plan tooling
 * can say whether a plan id is free in the repository, and that check belongs to the submission
 * step. The plan number is left **blank** for the same reason — the authoritative allocator owns
 * it, so PMA does not mint one.
 */
class CreatePlanDraftUseCase @Inject constructor(
    private val repository: PlanDraftRepository,
) {

    suspend operator fun invoke(requestedPlanId: String = "", title: String = ""): PlanDraft {
        val existing = repository.listDrafts().map { it.planId }.toSet()
        val base = slugify(requestedPlanId).ifBlank { slugify(title) }.ifBlank { "plan" }
        val planId = uniquePlanId(base, existing)

        val now = System.currentTimeMillis()
        val draft = PlanDraft(
            draftId = UUID.randomUUID().toString(),
            planId = planId,
            title = title.ifBlank { planId },
            revision = 0,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        return repository.saveDraft(draft)
    }

    private fun uniquePlanId(base: String, existing: Set<String>): String {
        if (base !in existing) return base
        var suffix = 2
        while ("$base-$suffix" in existing) suffix++
        return "$base-$suffix"
    }

    private fun slugify(raw: String): String {
        val slug = raw.trim().lowercase()
            .map { character ->
                if (character.isLetterOrDigit()) character else '-'
            }
            .joinToString("")
            .replace(Regex("-+"), "-")
            .trim('-')
        // A plan id must begin with a letter or a digit.
        return slug.trimStart('-').takeIf { it.isNotEmpty() } ?: ""
    }
}
