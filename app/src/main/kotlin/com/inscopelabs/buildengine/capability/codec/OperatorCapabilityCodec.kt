package com.inscopelabs.buildengine.capability.codec

import com.inscopelabs.buildengine.capability.model.OperatorCapability

/**
 * The ctxop-v1 wire format.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §5: ten LF-separated fields, HMAC-SHA256, base64url without padding.
 *
 *     ctxop-v1 \n kid \n exp \n order \n plan \n task \n op \n branch \n head \n nonce
 *
 * The exact format is specified in docs/capability-format.md.
 */
object OperatorCapabilityCodec {

    const val DOMAIN_SEPARATOR: String = "ctxop-v1"

    /** Design review §5: the minted branch is always a task branch. */
    const val BRANCH_PATTERN: String = "^task-[A-Za-z0-9._-]+$"

    /** Design review §5: kid is a selector, never a secret. */
    const val KID_PATTERN: String = "^[A-Za-z0-9_]{1,16}$"

    /** Design review §5: the head is a full 40-character git SHA. */
    const val HEAD_PATTERN: String = "^[0-9a-f]{40}$"

    fun encode(capability: OperatorCapability, signature: ByteArray): String =
        TODO("SCAFFOLD: build the LF-separated message and append the signature.")

    fun decode(message: String): OperatorCapability =
        TODO("SCAFFOLD: parse and validate the LF-separated message.")

    fun isValidBranch(branch: String): Boolean =
        TODO("SCAFFOLD: enforce BRANCH_PATTERN.")

    fun isValidHead(head: String): Boolean =
        TODO("SCAFFOLD: enforce HEAD_PATTERN.")
}
