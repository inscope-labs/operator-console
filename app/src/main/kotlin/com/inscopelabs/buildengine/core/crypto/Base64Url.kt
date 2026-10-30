package com.inscopelabs.buildengine.core.crypto

import java.util.Base64

/**
 * base64url encoding without padding.
 *
 * Design review §5: the capability signature is base64url-encoded without padding.
 */
object Base64Url {

    fun encode(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    fun decode(value: String): ByteArray {
        require(!value.contains('=')) { "base64url must not contain padding '=' characters" }
        require(!value.contains('+') && !value.contains('/')) { "base64url must not use standard base64 '+' or '/' characters" }
        for (ch in value) {
            require(ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '_') {
                "Invalid base64url character: '$ch'"
            }
        }
        return Base64.getUrlDecoder().decode(value)
    }
}
