package com.inscopelabs.buildengine.capability.codec

import com.inscopelabs.buildengine.capability.model.OperatorCapability
import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.core.crypto.Base64Url

/**
 * The ctxop-v1 wire format.
 *
 * Design review §5: ten LF-separated fields, HMAC-SHA256, base64url without padding.
 *
 *     ctxop-v1 \n kid \n exp \n order \n plan \n task \n op \n branch \n head \n nonce
 *
 * The exact format is specified in docs/capability-format.md.
 *
 * ### Three things this codec keeps apart
 *
 * - The **message** is the ten fields, LF-separated. Those are the exact bytes that
 *   are signed — nothing else is covered.
 * - The **signature** is over those bytes, and travels base64url-encoded without
 *   padding on a line of its own.
 * - The **transmission** is the message and the signature together.
 *
 * Conflating them is how a signature ends up covering the wrong bytes, and the
 * failure is silent: the artefact still parses, and the gateway simply refuses it.
 * So they are three named functions here rather than one function with a flag.
 *
 * ### What is refused
 *
 * The codec is strict in both directions: it will not encode a capability that
 * breaks a field rule, and it will not decode a message that breaks one. A codec
 * that repairs an invalid value is a codec that lets two different capabilities
 * have the same signed bytes.
 *
 * The field rules are the ones in docs/capability-format.md §2. `kid`, `branch` and
 * `head` carry published patterns. `op` is the closed enumeration, and an
 * unrecognised value is an error rather than a default. `exp` must be a Unix
 * timestamp. The three free-form identifiers (`order`, `plan`, `task`) and the
 * nonce must be present and free of control characters — the wire format puts no
 * pattern on them, but an absent identifier names nothing.
 */
object OperatorCapabilityCodec {

    const val DOMAIN_SEPARATOR: String = "ctxop-v1"

    /** Design review §5: the minted branch is always a task branch. */
    const val BRANCH_PATTERN: String = "^task-[A-Za-z0-9._-]+$"

    /** Design review §5: kid is a selector, never a secret. */
    const val KID_PATTERN: String = "^[A-Za-z0-9_]{1,16}$"

    /** Design review §5: the head is a full 40-character git SHA. */
    const val HEAD_PATTERN: String = "^[0-9a-f]{40}$"

    /**
     * The number of LF-separated fields in a signed capability message.
     *
     * A message with an extra field is not a valid capability, and neither is one
     * with a missing field: the field count is fixed, which is what makes the
     * encoding unambiguous.
     */
    const val FIELD_COUNT: Int = 10

    /** The field separator. Exactly one LF, no CR, no trailing LF. */
    private const val SEPARATOR: String = "\n"

    private val BRANCH_REGEX = Regex(BRANCH_PATTERN)
    private val KID_REGEX = Regex(KID_PATTERN)
    private val HEAD_REGEX = Regex(HEAD_PATTERN)

    // -- field positions, in the fixed order of the wire format --
    private const val INDEX_KID = 1
    private const val INDEX_EXP = 2
    private const val INDEX_ORDER = 3
    private const val INDEX_PLAN = 4
    private const val INDEX_TASK = 5
    private const val INDEX_OP = 6
    private const val INDEX_BRANCH = 7
    private const val INDEX_HEAD = 8
    private const val INDEX_NONCE = 9

    /**
     * The signed message: the ten fields, LF-separated.
     *
     * These are the bytes the signature covers. The signature is *not* part of them.
     */
    fun message(capability: OperatorCapability): String {
        validate(capability)
        return listOf(
            DOMAIN_SEPARATOR,
            capability.kid,
            capability.exp.toString(),
            capability.order,
            capability.plan,
            capability.task,
            capability.op.name,
            capability.branch,
            capability.head,
            capability.nonce,
        ).joinToString(SEPARATOR)
    }

    /**
     * The exact bytes that are signed: the UTF-8 encoding of [message].
     */
    fun signedBytes(capability: OperatorCapability): ByteArray =
        message(capability).toByteArray(Charsets.UTF_8)

    /**
     * The transmission: the signed message and the signature, the signature
     * base64url-encoded without padding on a line of its own.
     */
    fun encode(capability: OperatorCapability, signature: ByteArray): String {
        require(signature.isNotEmpty()) { "a capability transmission carries a signature" }
        return message(capability) + SEPARATOR + Base64Url.encode(signature)
    }

    /**
     * Splits a transmission back into the signed message and the signature bytes.
     *
     * The signature is returned, not verified: the gateway is the sole enforcement
     * point, and the console does not hold the keys that would let it verify one.
     * A caller that receives a transmission must take it to the gateway and let the
     * gateway decide.
     */
    fun split(transmission: String): Pair<String, ByteArray> {
        val fields = transmission.split(SEPARATOR)
        require(fields.size == FIELD_COUNT + 1) {
            "a transmission is a $FIELD_COUNT-field message and a signature, found ${fields.size} fields"
        }
        val message = fields.subList(0, FIELD_COUNT).joinToString(SEPARATOR)
        return message to Base64Url.decode(fields[FIELD_COUNT])
    }

    /**
     * Parses and validates a signed capability message.
     *
     * @throws IllegalArgumentException if the message breaks any field rule. A
     *   malformed capability is refused, never repaired.
     */
    fun decode(message: String): OperatorCapability {
        val fields = message.split(SEPARATOR)
        require(fields.size == FIELD_COUNT) {
            "a capability message has $FIELD_COUNT LF-separated fields, found ${fields.size}"
        }
        require(message.none { it == '\r' }) {
            "a capability message carries no CR"
        }
        require(fields[0] == DOMAIN_SEPARATOR) {
            "a capability message starts with '$DOMAIN_SEPARATOR', found '${fields[0]}'"
        }

        val kid = fields[INDEX_KID]
        require(isValidKid(kid)) { "kid '$kid' does not match $KID_PATTERN" }

        val exp = fields[INDEX_EXP].toLongOrNull()
        require(exp != null && exp >= 0L) {
            "exp '${fields[INDEX_EXP]}' is not a unix timestamp"
        }

        val order = fields[INDEX_ORDER]
        require(isIdentifier(order)) { "order must name a work order" }

        val plan = fields[INDEX_PLAN]
        require(isIdentifier(plan)) { "plan must name a plan" }

        val task = fields[INDEX_TASK]
        require(isIdentifier(task)) { "task must name a task" }

        val op = fields[INDEX_OP]
        require(isValidOperation(op)) {
            "op '$op' is not one of ${OperationType.entries.joinToString(", ", "[", "]") { it.name }}"
        }

        val branch = fields[INDEX_BRANCH]
        require(isValidBranch(branch)) { "branch '$branch' does not match $BRANCH_PATTERN" }

        val head = fields[INDEX_HEAD]
        require(isValidHead(head)) { "head '$head' does not match $HEAD_PATTERN" }

        val nonce = fields[INDEX_NONCE]
        require(isIdentifier(nonce)) { "nonce must not be empty" }

        return OperatorCapability(
            kid = kid,
            exp = exp,
            order = order,
            plan = plan,
            task = task,
            op = OperationType.valueOf(op),
            branch = branch,
            head = head,
            nonce = nonce,
        )
    }

    /** Design review §5: the minted branch is always a task branch. */
    fun isValidBranch(branch: String): Boolean = BRANCH_REGEX.matches(branch)

    /** Design review §5: the head is a full 40-character git SHA. */
    fun isValidHead(head: String): Boolean = HEAD_REGEX.matches(head)

    /** Design review §5: kid is a selector, never a secret. */
    fun isValidKid(kid: String): Boolean = KID_REGEX.matches(kid)

    /**
     * Design review §5: authorisation is never inferred from an unrecognised
     * request, so an operation the console does not know is refused rather than
     * mapped onto a default.
     */
    fun isValidOperation(op: String): Boolean =
        OperationType.entries.any { it.name == op }

    /**
     * Checks a capability against every field rule, so that an invalid capability
     * cannot be signed in the first place.
     */
    private fun validate(capability: OperatorCapability) {
        require(isValidKid(capability.kid)) {
            "kid '${capability.kid}' does not match $KID_PATTERN"
        }
        require(capability.exp >= 0L) { "exp must not be negative" }
        require(isIdentifier(capability.order)) { "order must name a work order" }
        require(isIdentifier(capability.plan)) { "plan must name a plan" }
        require(isIdentifier(capability.task)) { "task must name a task" }
        require(isValidBranch(capability.branch)) {
            "branch '${capability.branch}' does not match $BRANCH_PATTERN"
        }
        require(isValidHead(capability.head)) {
            "head '${capability.head}' does not match $HEAD_PATTERN"
        }
        require(isIdentifier(capability.nonce)) { "nonce must not be empty" }
    }

    /**
     * Whether [value] is usable as an identifier: not empty, and free of control
     * characters.
     *
     * The wire format puts no pattern on these fields, but a field that is absent
     * or that carries a control character names nothing — and a control character
     * is how a field boundary gets smuggled.
     */
    private fun isIdentifier(value: String): Boolean =
        value.isNotEmpty() && value.none { isControl(it) }

    /** ISO control characters: C0 (0x00–0x1F) and C1 (0x7F–0x9F). */
    private fun isControl(character: Char): Boolean =
        character.code <= 0x1F || character.code in 0x7F..0x9F
}
