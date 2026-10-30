package com.inscopelabs.buildengine.core.crypto

import java.util.Base64

/**
 * base64url encoding without padding.
 *
 * Design review §5: the capability signature is base64url-encoded without padding.
 *
 * The encoding is part of the `ctxop-v1` wire contract (docs/capability-format.md §5):
 * the alphabet is the URL-safe one (`-` and `_` in place of `+` and `/`) and the
 * padding is omitted. A value that is not in that form is *not* a capability
 * fragment, so [decode] refuses it rather than silently repairing it — a decoder
 * that accepts several encodings of the same bytes is a decoder that lets two
 * different representations of the same signed material into the same log line.
 */
object Base64Url {

    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

    private val decoder: Base64.Decoder = Base64.getUrlDecoder()

    /** The unpadded base64url alphabet: the URL-safe characters and nothing else. */
    private val BASE64URL_BODY = Regex("^[A-Za-z0-9_-]*$")

    /**
     * Encodes [bytes] as unpadded base64url.
     */
    fun encode(bytes: ByteArray): String = encoder.encodeToString(bytes)

    /**
     * Decodes an unpadded base64url [value].
     *
     * @throws IllegalArgumentException if [value] is padded, uses the standard
     *   base64 alphabet, or is not a decodable length.
     */
    fun decode(value: String): ByteArray {
        require(BASE64URL_BODY.matches(value)) {
            "value is not unpadded base64url"
        }
        // A base64 body is never one character short of a multiple of four.
        require(value.length % 4 != 1) {
            "value is not a decodable base64url length"
        }
        return decoder.decode(value)
    }
}
