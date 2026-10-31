package com.inscopelabs.buildengine.capability.codec

import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.capability.model.OperatorCapability
import com.inscopelabs.buildengine.core.crypto.Base64Url

/**
 * The ctxop-v1 wire format.
 *
 * Design review §5: ten LF-separated fields, HMAC-SHA256 or ECDSA P-256, base64url without padding.
 *
 *     ctxop-v1 \n kid \n exp \n order \n plan \n task \n op \n branch \n head \n nonce
 *
 * The exact format is specified in docs/capability-format.md.
 */
object OperatorCapabilityCodec {

    const val DOMAIN_SEPARATOR: String = "ctxop-v1"

    /** Design review §5: the minted branch is always a task branch for task-scoped operations. */
    const val BRANCH_PATTERN: String = "^task-[A-Za-z0-9._-]+$"

    /** Design review §5: kid is a selector, never a secret. */
    /** Canonical decimal: no sign, no leading zeros, fits in a Long. */
    private val EXP_REGEX = Regex("^[1-9][0-9]{0,18}$")

    const val KID_PATTERN: String = "^[A-Za-z0-9_]{1,16}$"

    /** Design review §5: the head is a full 40-character git SHA. */
    const val HEAD_PATTERN: String = "^[0-9a-f]{40}$"

    fun encode(capability: OperatorCapability): String = listOf(
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
    ).joinToString("\n")

    fun encode(capability: OperatorCapability, signature: ByteArray): String {
        val message = encode(capability)
        val sigString = Base64Url.encode(signature)
        return "$message\n$sigString"
    }

    fun decode(message: String): OperatorCapability {
        require(!message.contains('\r')) { "CR is forbidden in capability encoding" }
        require(!message.endsWith('\n')) { "Trailing LF is forbidden in capability encoding" }
        require(!message.startsWith('\n')) { "Leading LF is forbidden in capability encoding" }

        val lines = message.split('\n')
        require(lines.size == 10) { "Expected exactly 10 fields, found ${lines.size}" }

        val domain = lines[0]
        require(domain == DOMAIN_SEPARATOR) { "Invalid domain separator" }

        val kid = lines[1]
        val expText = lines[2]
        require(EXP_REGEX.matches(expText)) { "Invalid exp: must be a canonical positive decimal integer" }
        val exp = expText.toLongOrNull() ?: throw IllegalArgumentException("Invalid exp: out of range")
        val order = lines[3]
        val plan = lines[4]
        val task = lines[5]

        val op = try {
            OperationType.valueOf(lines[6])
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Unknown operation: '${lines[6].take(32)}'")
        }

        val branch = lines[7]
        val head = lines[8]
        val nonce = lines[9]

        return OperatorCapability(
            kid = kid,
            exp = exp,
            order = order,
            plan = plan,
            task = task,
            op = op,
            branch = branch,
            head = head,
            nonce = nonce,
        )
    }

    fun isValidBranch(branch: String): Boolean = Regex(BRANCH_PATTERN).matches(branch)

    fun isValidBranch(branch: String, op: OperationType): Boolean =
        if (op.isTaskScoped) isValidBranch(branch) else OperatorCapability.isEngineBranch(branch)

    fun isValidHead(head: String): Boolean = Regex(HEAD_PATTERN).matches(head)

    fun isValidKid(kid: String): Boolean = Regex(KID_PATTERN).matches(kid)
}
