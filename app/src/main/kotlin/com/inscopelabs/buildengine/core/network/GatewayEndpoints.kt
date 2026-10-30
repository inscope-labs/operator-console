package com.inscopelabs.buildengine.core.network

/**
 * The gateway's HTTP surface.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * The base URL is an RFC 2606 reserved name on purpose: a scaffold must not be able
 * to reach a real host. Replace it with the deployed gateway URL, and keep it out of
 * source control if that host is environment-specific.
 */
object GatewayEndpoints {

    const val BASE_URL: String = "https://gateway.invalid"

    const val HEALTH: String = "/health"
    const val PLANS: String = "/plans"
    const val TASKS_NEXT: String = "/tasks/next"
    const val PLAN_STATUS: String = "/plans/{planId}/status"
    const val PLAN_VALIDATE: String = "/plans/{planId}/validate"
    const val PLAN_RENDER_CHECK: String = "/plans/{planId}/render-check"

    const val PUBLISH: String = "/tasks/{taskId}/publish"
    const val RECONCILE: String = "/tasks/{taskId}/reconcile"
    const val UPLOAD: String = "/tasks/{taskId}/upload"
    const val HALT: String = "/halt"
    const val RESUME: String = "/resume"
    const val DECISION: String = "/decisions/{decisionId}"
}
