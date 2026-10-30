package com.inscopelabs.buildengine.data.local

/**
 * Records the device's enrolment with the gateway.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §5: Model B needs a device enrolment registry, mapping a key selector
 * to the enrolled public key. See docs/device-enrollment.md.
 */
class DeviceEnrollmentStore {

    fun recordEnrollment(kid: String, keyAlias: String, publicKeyBase64: String): Unit =
        TODO("SCAFFOLD: persist the enrolment record.")

    fun enrollmentFor(kid: String): String? = TODO("SCAFFOLD: look up the public key.")

    fun keyAliasFor(kid: String): String? = TODO("SCAFFOLD: look up the key alias.")

    fun clear(): Unit = TODO("SCAFFOLD: drop the enrolment, e.g. on device loss.")
}
