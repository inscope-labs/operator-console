package com.inscopelabs.buildengine.domain.usecase.authoring

import com.inscopelabs.buildengine.domain.model.DraftValidationReport
import com.inscopelabs.buildengine.domain.model.PlanDraft
import com.inscopelabs.buildengine.domain.validation.PlanDraftValidator
import javax.inject.Inject

/**
 * Validates a plan draft against the canonical Build Engine rules (PMA Stage 2).
 *
 * The validation is local and immediate: the operator gets field-level feedback while typing, not
 * after a round trip. The report also states that the *authoritative* validator could not be
 * consulted, because Gateway A exposes no plan-authoring validation route — a local pass is never
 * presented as the canonical verdict.
 */
class ValidatePlanDraftUseCase @Inject constructor(
    private val validator: PlanDraftValidator,
) {

    operator fun invoke(draft: PlanDraft): DraftValidationReport = validator.validate(draft)
}
