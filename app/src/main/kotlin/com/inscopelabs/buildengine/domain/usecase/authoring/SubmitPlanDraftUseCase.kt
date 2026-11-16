package com.inscopelabs.buildengine.domain.usecase.authoring

import com.inscopelabs.buildengine.domain.model.PlanDraft
import com.inscopelabs.buildengine.domain.validation.PlanDraftValidator
import javax.inject.Inject

/**
 * Decides whether a plan draft can be submitted, and says why when it cannot (PMA Stage 3).
 *
 * **This use case sends nothing.** The repository write path a submission needs does not exist
 * yet on either side of the boundary:
 *
 * - Gateway A exposes no plan-authoring route — no draft store, no revision compare-and-swap, no
 *   submission request, no submission status lookup. The plan routes it does expose
 *   (`PUT /plans/{id}`, `POST /plans/{id}/validate`) operate on a simplified catalogue record
 *   that cannot hold a plan document, and that record is not the canonical plan store.
 * - The Build Engine has no workflow that materialises a submitted plan into
 *   `plans/<plan_id>/` and opens the reviewable pull request.
 *
 * So the app fails closed and says exactly what is missing, instead of offering a submit button
 * that silently does nothing or, worse, reports success it cannot know about. The acceptance
 * criteria for PMA are explicit that a plan is not accepted until the repository change is merged
 * and confirmed.
 */
class SubmitPlanDraftUseCase @Inject constructor(
    private val validator: PlanDraftValidator,
) {

    operator fun invoke(draft: PlanDraft): PlanSubmissionOutcome {
        val blockers = mutableListOf<String>()

        val report = validator.validate(draft)
        if (!report.isValid) {
            blockers += "${report.errors.size} validation error(s) must be resolved before submission"
        }
        if (!draft.isPlanNumberReserved) {
            blockers += "the plan number is not reserved by the authoritative Build Engine allocator"
        }

        blockers += "Gateway A exposes no plan-authoring draft or submission route (no draft store, " +
            "no revision compare-and-swap, no submission status lookup)"
        blockers += "the Build Engine has no workflow that materialises a submitted plan into " +
            "plans/<plan_id>/ and opens the reviewable pull request"

        return PlanSubmissionOutcome(
            isSubmittable = false,
            blockers = blockers,
            message = if (report.isValid && draft.isPlanNumberReserved) {
                "The draft is ready to submit, but the repository write path is not available yet: " +
                    "no gateway plan-authoring route and no Build Engine plan workflow exist."
            } else {
                "The draft is not ready to submit."
            },
        )
    }
}

/** The outcome of asking whether a draft can be submitted. */
data class PlanSubmissionOutcome(
    val isSubmittable: Boolean,
    val blockers: List<String>,
    val message: String,
)
