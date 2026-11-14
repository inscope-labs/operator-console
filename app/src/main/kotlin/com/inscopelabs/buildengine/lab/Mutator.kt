package com.inscopelabs.buildengine.lab

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/**
 * The one property a mutation changes.
 *
 * T5-201: "tests proving each mutation changes only what it claims". A mutation that
 * declared its target and then changed something else would make that test impossible,
 * so the target is a first-class part of the mutation and every mutation below touches
 * exactly one of them.
 */
enum class MutatorTarget {
    /** The separators or the trailing and leading line feeds of the capability. */
    CAPABILITY_FRAMING,

    /** The capability's domain separator (field 0). */
    CAPABILITY_DOMAIN,

    /** The capability's key selector (field 1). */
    CAPABILITY_KID,

    /** The capability's expiry (field 2). */
    CAPABILITY_EXPIRY,

    /** The capability's work order id (field 3). */
    CAPABILITY_ORDER,

    /** The capability's operation (field 6). */
    CAPABILITY_OPERATION,

    /** The capability's branch (field 7). */
    CAPABILITY_BRANCH,

    /** The capability's head SHA (field 8). */
    CAPABILITY_HEAD,

    /** The capability's nonce (field 9). */
    CAPABILITY_NONCE,

    /** The capability's signature (the last line). */
    CAPABILITY_SIGNATURE,

    /** The number of fields in the capability. */
    CAPABILITY_FIELD_COUNT,

    /** A field of the request body. */
    REQUEST_BODY,
}

/**
 * A controlled test mutation of a request or response (T5-201).
 *
 * A mutation is applied to the request **after** the capability has been minted and
 * placed on it, so a mutation can act on the capability and on the body independently.
 * That is what lets a scenario change the signature without changing the branch, and
 * change the branch in the body without changing the capability.
 *
 * The capability's wire shape is the one in docs/capability-format.md §1: ten
 * LF-separated fields followed by the signature.
 *
 * ```
 * 0 ctxop-v1   1 kid    2 exp  3 order  4 plan
 * 5 task       6 op     7 branch  8 head   9 nonce
 * 10 signature
 * ```
 */
interface Mutator {

    /** A stable identifier, so a report can name the mutation that was applied. */
    val id: String

    /** What the mutation does, in one line. */
    val description: String

    /** The single property this mutation changes. */
    val target: MutatorTarget

    /** The mutated request. */
    fun mutate(request: LabRequest): LabRequest
}

/**
 * The mutation catalogue.
 *
 * The capability-level mutations mirror the published conformance vectors
 * (docs/fixtures/vectors/codec_mutations.json) one for one, so that the Lab asserts
 * the same expectations the vector set does. The body-level mutations are the ones the
 * Lab needs for the request-scope errors the vectors do not cover.
 */
object LabMutators {

    /** Appends an eleventh message field. The gateway refuses the field count. */
    object ExtraField : Mutator {
        override val id: String = "capability-extra-field"
        override val description: String = "Appends a field to the capability message."
        override val target: MutatorTarget = MutatorTarget.CAPABILITY_FIELD_COUNT

        override fun mutate(request: LabRequest): LabRequest =
            request.copy(capability = request.capability?.let { "$it\nextra_field" })
    }

    /** Drops the nonce, so the message carries nine fields instead of ten. */
    object MissingField : Mutator {
        override val id: String = "capability-missing-field"
        override val description: String = "Removes a field from the capability message."
        override val target: MutatorTarget = MutatorTarget.CAPABILITY_FIELD_COUNT

        override fun mutate(request: LabRequest): LabRequest =
            request.copy(capability = request.capability?.let { capability ->
                val lines = capability.split("\n")
                if (lines.size <= NONCE_INDEX) {
                    capability
                } else {
                    lines.toMutableList().apply { removeAt(NONCE_INDEX) }.joinToString("\n")
                }
            })
    }

    /** Uses CRLF separators. The capability framing forbids CR. */
    object CrlfSeparators : Mutator {
        override val id: String = "capability-crlf-separators"
        override val description: String = "Replaces the capability's LF separators with CRLF."
        override val target: MutatorTarget = MutatorTarget.CAPABILITY_FRAMING

        override fun mutate(request: LabRequest): LabRequest =
            request.copy(capability = request.capability?.replace("\n", "\r\n"))
    }

    /** A trailing line feed after the signature. */
    object TrailingLineFeed : Mutator {
        override val id: String = "capability-trailing-line-feed"
        override val description: String = "Appends a trailing line feed to the capability."
        override val target: MutatorTarget = MutatorTarget.CAPABILITY_FRAMING

        override fun mutate(request: LabRequest): LabRequest =
            request.copy(capability = request.capability?.plus("\n"))
    }

    /** A leading line feed before the domain separator. */
    object LeadingLineFeed : Mutator {
        override val id: String = "capability-leading-line-feed"
        override val description: String = "Prepends a leading line feed to the capability."
        override val target: MutatorTarget = MutatorTarget.CAPABILITY_FRAMING

        override fun mutate(request: LabRequest): LabRequest =
            request.copy(capability = request.capability?.let { "\n$it" })
    }

    /** A domain separator the gateway does not recognise. */
    object InvalidDomainSeparator : Mutator {
        override val id: String = "capability-invalid-domain-separator"
        override val description: String = "Replaces the capability's domain separator."
        override val target: MutatorTarget = MutatorTarget.CAPABILITY_DOMAIN

        override fun mutate(request: LabRequest): LabRequest =
            request.copy(capability = withCapabilityField(request.capability, DOMAIN_INDEX, "ctxop-v2"))
    }

    /** An operation outside the closed enumeration. */
    object UnknownOperation : Mutator {
        override val id: String = "capability-unknown-operation"
        override val description: String = "Replaces the capability's operation with an unknown one."
        override val target: MutatorTarget = MutatorTarget.CAPABILITY_OPERATION

        override fun mutate(request: LabRequest): LabRequest =
            request.copy(capability = withCapabilityField(request.capability, OPERATION_INDEX, "PUBLISH_ALL"))
    }

    /** The operation in the wrong case. The enumeration is case-sensitive. */
    object LowercaseOperation : Mutator {
        override val id: String = "capability-lowercase-operation"
        override val description: String = "Lowercases the capability's operation."
        override val target: MutatorTarget = MutatorTarget.CAPABILITY_OPERATION

        override fun mutate(request: LabRequest): LabRequest =
            request.copy(
                capability = withCapabilityField(
                    request.capability,
                    OPERATION_INDEX,
                    request.capability?.split("\n")?.getOrNull(OPERATION_INDEX)?.lowercase().orEmpty(),
                ),
            )
    }

    /** A key selector that does not match the gateway's pattern. */
    object InvalidKid : Mutator {
        override val id: String = "capability-invalid-kid"
        override val description: String = "Puts a space in the capability's key selector."
        override val target: MutatorTarget = MutatorTarget.CAPABILITY_KID

        override fun mutate(request: LabRequest): LabRequest =
            request.copy(capability = withCapabilityField(request.capability, KID_INDEX, "k 2"))
    }

    /** A key selector the gateway does not know. */
    object UnknownKid : Mutator {
        override val id: String = "capability-unknown-kid"
        override val description: String = "Replaces the capability's key selector with an unknown one."
        override val target: MutatorTarget = MutatorTarget.CAPABILITY_KID

        override fun mutate(request: LabRequest): LabRequest =
            request.copy(capability = withCapabilityField(request.capability, KID_INDEX, "k9unknown"))
    }

    /** An expiry the gateway refuses to parse. */
    object NegativeExpiry : Mutator {
        override val id: String = "capability-negative-expiry"
        override val description: String = "Replaces the capability's expiry with a negative value."
        override val target: MutatorTarget = MutatorTarget.CAPABILITY_EXPIRY

        override fun mutate(request: LabRequest): LabRequest =
            request.copy(capability = withCapabilityField(request.capability, EXPIRY_INDEX, "-1"))
    }

    /** An expiry that is not a canonical decimal integer. */
    object NonCanonicalExpiry : Mutator {
        override val id: String = "capability-non-canonical-expiry"
        override val description: String = "Replaces the capability's expiry with a non-canonical value."
        override val target: MutatorTarget = MutatorTarget.CAPABILITY_EXPIRY

        override fun mutate(request: LabRequest): LabRequest =
            request.copy(capability = withCapabilityField(request.capability, EXPIRY_INDEX, "+1761840000"))
    }

    /** An expiry in the past. */
    object ExpiredCapability : Mutator {
        override val id: String = "capability-expired"
        override val description: String = "Backdates the capability's expiry."
        override val target: MutatorTarget = MutatorTarget.CAPABILITY_EXPIRY

        override fun mutate(request: LabRequest): LabRequest =
            request.copy(
                capability = withCapabilityField(
                    request.capability,
                    EXPIRY_INDEX,
                    ((System.currentTimeMillis() / 1000L) - 3600L).toString(),
                ),
            )
    }

    /** An expiry beyond the gateway's maximum TTL. */
    object ExtendedTtl : Mutator {
        override val id: String = "capability-extended-ttl"
        override val description: String = "Extends the capability's lifetime beyond the gateway's maximum."
        override val target: MutatorTarget = MutatorTarget.CAPABILITY_EXPIRY

        override fun mutate(request: LabRequest): LabRequest =
            request.copy(
                capability = withCapabilityField(
                    request.capability,
                    EXPIRY_INDEX,
                    ((System.currentTimeMillis() / 1000L) + 3600L).toString(),
                ),
            )
    }

    /** An empty work order id. */
    object EmptyOrder : Mutator {
        override val id: String = "capability-empty-order"
        override val description: String = "Empties the capability's work order id."
        override val target: MutatorTarget = MutatorTarget.CAPABILITY_ORDER

        override fun mutate(request: LabRequest): LabRequest =
            request.copy(capability = withCapabilityField(request.capability, ORDER_INDEX, ""))
    }

    /** A task-scoped operation pointing at a branch that is not a task branch. */
    object NonTaskBranch : Mutator {
        override val id: String = "capability-non-task-branch"
        override val description: String = "Points a task-scoped capability at a non-task branch."
        override val target: MutatorTarget = MutatorTarget.CAPABILITY_BRANCH

        override fun mutate(request: LabRequest): LabRequest =
            request.copy(capability = withCapabilityField(request.capability, BRANCH_INDEX, "main"))
    }

    /** An engine-scoped operation carrying a task branch. */
    object EngineOperationOnTaskBranch : Mutator {
        override val id: String = "capability-engine-op-task-branch"
        override val description: String = "Points an engine-scoped capability at a task branch."
        override val target: MutatorTarget = MutatorTarget.CAPABILITY_BRANCH

        override fun mutate(request: LabRequest): LabRequest =
            request.copy(capability = withCapabilityField(request.capability, BRANCH_INDEX, "task-10008-refactor-parser"))
    }

    /** An engine branch that is not a safe ref name. */
    object UnsafeEngineBranch : Mutator {
        override val id: String = "capability-unsafe-engine-branch"
        override val description: String = "Puts a path traversal into the capability's branch."
        override val target: MutatorTarget = MutatorTarget.CAPABILITY_BRANCH

        override fun mutate(request: LabRequest): LabRequest =
            request.copy(capability = withCapabilityField(request.capability, BRANCH_INDEX, "engine/../secrets"))
    }

    /** A head SHA that is one character short. */
    object MalformedHead : Mutator {
        override val id: String = "capability-malformed-head"
        override val description: String = "Shortens the capability's head SHA by one character."
        override val target: MutatorTarget = MutatorTarget.CAPABILITY_HEAD

        override fun mutate(request: LabRequest): LabRequest =
            request.copy(
                capability = withCapabilityField(
                    request.capability,
                    HEAD_INDEX,
                    request.capability?.split("\n")?.getOrNull(HEAD_INDEX)?.dropLast(1).orEmpty(),
                ),
            )
    }

    /** A head SHA in uppercase hex. */
    object UppercaseHead : Mutator {
        override val id: String = "capability-uppercase-head"
        override val description: String = "Uppercases the capability's head SHA."
        override val target: MutatorTarget = MutatorTarget.CAPABILITY_HEAD

        override fun mutate(request: LabRequest): LabRequest =
            request.copy(
                capability = withCapabilityField(
                    request.capability,
                    HEAD_INDEX,
                    request.capability?.split("\n")?.getOrNull(HEAD_INDEX)?.uppercase().orEmpty(),
                ),
            )
    }

    /** A nonce below the minimum length. */
    object ShortNonce : Mutator {
        override val id: String = "capability-short-nonce"
        override val description: String = "Shortens the capability's nonce below the minimum length."
        override val target: MutatorTarget = MutatorTarget.CAPABILITY_NONCE

        override fun mutate(request: LabRequest): LabRequest =
            request.copy(
                capability = withCapabilityField(
                    request.capability,
                    NONCE_INDEX,
                    request.capability?.split("\n")?.getOrNull(NONCE_INDEX)?.take(15).orEmpty(),
                ),
            )
    }

    /** A signature that no longer matches the message. */
    object TamperedSignature : Mutator {
        override val id: String = "capability-tampered-signature"
        override val description: String = "Flips the last character of the capability's signature."
        override val target: MutatorTarget = MutatorTarget.CAPABILITY_SIGNATURE

        override fun mutate(request: LabRequest): LabRequest {
            val capability = request.capability ?: return request
            val lines = capability.split("\n")
            val signature = lines.lastOrNull().orEmpty()
            if (signature.isEmpty()) return request
            val flipped = if (signature.last() == 'A') 'B' else 'A'
            val tampered = signature.dropLast(1) + flipped
            return request.copy(
                capability = lines.dropLast(1).plus(tampered).joinToString("\n"),
            )
        }
    }

    /** A head in the body that is not the branch's current head. */
    object DriftedHead : Mutator {
        override val id: String = "body-drifted-head"
        override val description: String = "Replaces the request body's expected head SHA."
        override val target: MutatorTarget = MutatorTarget.REQUEST_BODY

        override fun mutate(request: LabRequest): LabRequest =
            withBodyField(request, "head", "0000000000000000000000000000000000000000")
    }

    /** A branch in the body that the capability was not minted for. */
    object WrongBranch : Mutator {
        override val id: String = "body-wrong-branch"
        override val description: String = "Replaces the request body's branch."
        override val target: MutatorTarget = MutatorTarget.REQUEST_BODY

        override fun mutate(request: LabRequest): LabRequest =
            withBodyField(request, "branch", "task-99999-other-task")
    }

    /** An idempotency key that is not 32 lowercase hex characters. */
    object InvalidIdempotencyKey : Mutator {
        override val id: String = "body-invalid-idempotency-key"
        override val description: String = "Replaces the request body's idempotency key with a malformed one."
        override val target: MutatorTarget = MutatorTarget.REQUEST_BODY

        override fun mutate(request: LabRequest): LabRequest =
            withBodyField(request, "idempotencyKey", "not-a-valid-key")
    }

    /** A CRLF injection into a body field. */
    object CrlfInjectionInBody : Mutator {
        override val id: String = "body-crlf-injection"
        override val description: String = "Injects a CRLF into a request body field."
        override val target: MutatorTarget = MutatorTarget.REQUEST_BODY

        override fun mutate(request: LabRequest): LabRequest =
            withBodyField(request, "notes", "approved\r\nX-Injected: 1")
    }

    /**
     * Replaces one field of the capability message.
     *
     * A capability that is too short to have the field is returned unchanged: the
     * mutation then changes nothing at all, which is the honest outcome rather than a
     * mutation that quietly rewrites the framing instead of the field it declared.
     */
    private fun withCapabilityField(capability: String?, index: Int, value: String): String? {
        if (capability == null) return null
        val lines = capability.split("\n")
        if (index >= lines.size) return capability
        val updated = lines.toMutableList()
        updated[index] = value
        return updated.joinToString("\n")
    }

    /**
     * Replaces one field of the request body.
     *
     * The body is edited as JSON so that the mutation cannot corrupt the document. A
     * body that is not JSON is returned unchanged, for the same reason the capability
     * helper is: the mutation changes only what it declared, or it changes nothing.
     */
    private fun withBodyField(request: LabRequest, field: String, value: String): LabRequest {
        val body = request.body ?: return request
        return try {
            val original = json.parseToJsonElement(body).jsonObject
            val updated = buildJsonObject {
                original.forEach { (key, element) ->
                    if (key == field) {
                        put(key, JsonPrimitive(value))
                    } else {
                        put(key, element)
                    }
                }
                if (!original.containsKey(field)) {
                    put(field, JsonPrimitive(value))
                }
            }
            request.copy(body = updated.toString())
        } catch (error: Exception) {
            request
        }
    }

    private const val DOMAIN_INDEX: Int = 0
    private const val KID_INDEX: Int = 1
    private const val EXPIRY_INDEX: Int = 2
    private const val ORDER_INDEX: Int = 3
    private const val OPERATION_INDEX: Int = 6
    private const val BRANCH_INDEX: Int = 7
    private const val HEAD_INDEX: Int = 8
    private const val NONCE_INDEX: Int = 9

    private val json = Json { ignoreUnknownKeys = true }
}
