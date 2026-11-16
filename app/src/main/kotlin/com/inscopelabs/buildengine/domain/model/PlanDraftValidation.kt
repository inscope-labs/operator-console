package com.inscopelabs.buildengine.domain.model

/**
 * One finding from validating a draft against the canonical Build Engine rules.
 *
 * `field` is a path into the draft (`plan.title`, `phases[1].name`, `tasks[2].acceptance`) so the
 * editor can point the operator at the thing that is wrong, rather than only saying that something
 * is wrong.
 */
data class DraftIssue(
    val severity: DraftIssueSeverity,
    val field: String,
    val message: String,
)

enum class DraftIssueSeverity {
    ERROR,
    WARNING,
}

/**
 * The result of validating a draft.
 *
 * The report separates two things that are easy to confuse:
 *
 * - **errors and warnings** are what the local validator found, against the canonical Build Engine
 *   rules. They are advisory in the sense that the gateway is always the authority on a write,
 *   but they are the same rules the canonical tooling applies.
 * - **canonicalServerValidationAvailable** says whether the *authoritative* validator — the one
 *   that runs the canonical schemas and semantic rules against repository state — could be
 *   consulted. It cannot, today: the gateway exposes no plan-authoring validation route, so the
 *   report says so rather than letting a local pass stand in for the canonical one.
 */
data class DraftValidationReport(
    val errors: List<DraftIssue>,
    val warnings: List<DraftIssue>,
    val canonicalServerValidationAvailable: Boolean = false,
    val canonicalServerValidationNote: String =
        "Canonical server-side validation is unavailable: Gateway A exposes no plan-authoring " +
            "validation route. The checks below are the Build Engine's rules applied locally, and " +
            "they are advisory until the authoritative validator can be consulted.",
) {
    val isValid: Boolean get() = errors.isEmpty()

    val issueCount: Int get() = errors.size + warnings.size

    /** Every issue that belongs to one field, errors first. */
    fun issuesFor(field: String): List<DraftIssue> =
        (errors + warnings).filter { it.field == field }
}
