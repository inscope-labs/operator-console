package com.inscopelabs.buildengine.core.network

/**
 * The gateway's HTTP surface.
 *
 * docs/gateway-contract.md §3 — every endpoint the gateway exposes.
 *
 * Every path the console can reach is named here, so that the set of reachable
 * endpoints is one file rather than strings scattered through the data layer.
 *
 * The base URL is an RFC 2606 reserved name on purpose: a scaffold must not be able
 * to reach a real host. Replace it with the deployed gateway URL, and keep it out of
 * source control if that host is environment-specific.
 *
 * Paths are built from templates whose placeholders are percent-encoded. An
 * identifier that arrives from the gateway is not trusted to be a single path
 * segment, and a path segment that is not one is how a caller reaches an endpoint
 * that was never on the list above.
 */
object GatewayEndpoints {

    const val BASE_URL: String = "https://gateway.invalid"

    const val HEALTH: String = "/health"
    const val PLANS: String = "/plans"
    const val PLAN_STATUS: String = "/plans/{planId}/status"
    const val PLAN_VALIDATE: String = "/plans/{planId}/validate"
    const val PLAN_RENDER_CHECK: String = "/plans/{planId}/render-check"
    const val TASKS_NEXT: String = "/tasks/next"
    const val BRANCH_HEAD: String = "/branches/{branchName}/head"

    const val PUBLISH: String = "/tasks/{taskId}/publish"
    const val RECONCILE: String = "/tasks/{taskId}/reconcile"
    const val UPLOAD: String = "/tasks/{taskId}/upload"
    const val HALT: String = "/halt"
    const val RESUME: String = "/resume"

    const val SYNC: String = "/sync"
    const val DOCTOR: String = "/doctor"

    const val ENROLL: String = "/enroll"
    const val ENROLL_ROTATE: String = "/enroll/rotate"

    /**
     * The framework decision endpoint.
     *
     * `DECIDE` is the highest-authority action in the operator surface (D-e) and it
     * is in the closed `OperationType` enumeration, but the gateway contract does not
     * yet define a route for it. The path is kept so the surface is complete, and it
     * is called out here because a route the contract does not define is a route the
     * gateway does not serve.
     */
    const val DECISION: String = "/decisions/{decisionId}"

    /** `GET /health` — the liveness and environment probe. */
    fun health(): String = HEALTH

    /** `GET /plans` — the plan list. */
    fun plans(): String = PLANS

    /** `GET /plans/{planId}/status`. */
    fun planStatus(planId: String): String = path(PLAN_STATUS, "planId" to planId)

    /** `POST /plans/{planId}/validate`. */
    fun planValidate(planId: String): String = path(PLAN_VALIDATE, "planId" to planId)

    /** `GET /plans/{planId}/render-check`. */
    fun planRenderCheck(planId: String): String = path(PLAN_RENDER_CHECK, "planId" to planId)

    /** `GET /tasks/next` — the next actionable tasks. */
    fun tasksNext(): String = TASKS_NEXT

    /** `GET /branches/{branchName}/head` — the current remote head of a branch. */
    fun branchHead(branchName: String): String = path(BRANCH_HEAD, "branchName" to branchName)

    /** `POST /tasks/{taskId}/publish`. */
    fun publish(taskId: String): String = path(PUBLISH, "taskId" to taskId)

    /** `POST /tasks/{taskId}/reconcile`. */
    fun reconcile(taskId: String): String = path(RECONCILE, "taskId" to taskId)

    /** `POST /tasks/{taskId}/upload`. */
    fun upload(taskId: String): String = path(UPLOAD, "taskId" to taskId)

    /** `POST /halt` — the repository-wide halt. */
    fun halt(): String = HALT

    /** `POST /resume` — the repository-wide resume. */
    fun resume(): String = RESUME

    /** `POST /sync` — the consumer repository fast-forward. */
    fun sync(): String = SYNC

    /** `GET /doctor` — the diagnostic report. */
    fun doctor(): String = DOCTOR

    /** `POST /enroll` — device enrolment. */
    fun enroll(): String = ENROLL

    /** `POST /enroll/rotate` — key rotation. */
    fun enrollRotate(): String = ENROLL_ROTATE

    /** `POST /decisions/{decisionId}` — the framework decision. */
    fun decision(decisionId: String): String = path(DECISION, "decisionId" to decisionId)

    /**
     * Fills the placeholders of [template] with [parameters].
     *
     * Every placeholder must be filled: a path that still carries one would be sent
     * to the gateway literally, and the failure would surface as a 404 rather than
     * as the bug it is.
     */
    fun path(template: String, vararg parameters: Pair<String, String>): String {
        var path = template
        for ((name, value) in parameters) {
            path = path.replace("{$name}", encodePathSegment(value))
        }
        require(!path.contains('{') && !path.contains('}')) {
            "path template '$template' has an unfilled placeholder"
        }
        return path
    }

    /**
     * The absolute URL of [path] against [baseUrl].
     */
    fun url(path: String, baseUrl: String = BASE_URL): String =
        baseUrl.trimEnd('/') + path

    /**
     * Percent-encodes [value] as a single path segment.
     *
     * RFC 3986: the unreserved set is letters, digits, `-`, `.`, `_` and `~`, and
     * everything else is encoded from its UTF-8 bytes. A `/` in an identifier would
     * otherwise become a path separator and address a different endpoint.
     */
    private fun encodePathSegment(value: String): String {
        val encoded = StringBuilder(value.length)
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val character = byte.toInt().toChar()
            if (character in UNRESERVED) {
                encoded.append(character)
            } else {
                encoded.append('%')
                encoded.append(HEX_DIGITS[(byte.toInt() shr 4) and 0x0F])
                encoded.append(HEX_DIGITS[byte.toInt() and 0x0F])
            }
        }
        return encoded.toString()
    }

    private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

    private const val HEX_DIGITS = "0123456789ABCDEF"
}
