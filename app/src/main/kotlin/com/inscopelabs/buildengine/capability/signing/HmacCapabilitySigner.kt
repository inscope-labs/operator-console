package com.inscopelabs.buildengine.capability.signing

import com.inscopelabs.buildengine.core.crypto.Base64Url
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Signs capabilities with HMAC-SHA256 using a shared secret.
 *
 * Used by Lab Mode (ADR 0004) to exercise test gateway endpoints without
 * requiring biometric authorization on every test scenario.
 */
class HmacCapabilitySigner(
    private val keyId: String = "lab-k1",
    private val secret: ByteArray = "default-lab-secret-key-32-bytes!".toByteArray(Charsets.UTF_8),
) : CapabilitySigner {

    constructor(keyId: String, secretUtf8: String) : this(keyId, secretUtf8.toByteArray(Charsets.UTF_8))

    override fun kid(): String = keyId

    override fun sign(message: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(secret, "HmacSHA256")
        mac.init(keySpec)
        return mac.doFinal(message)
    }

    fun signBase64Url(message: ByteArray): String = Base64Url.encode(sign(message))
}
