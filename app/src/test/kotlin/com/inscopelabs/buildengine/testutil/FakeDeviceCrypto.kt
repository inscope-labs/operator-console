package com.inscopelabs.buildengine.testutil

import com.inscopelabs.buildengine.core.crypto.DeviceKeyManager
import com.inscopelabs.buildengine.core.crypto.DeviceKeySpec
import com.inscopelabs.buildengine.core.crypto.KeyNeedsRotationException
import com.inscopelabs.buildengine.core.crypto.NeedsReenrollmentException
import com.inscopelabs.buildengine.core.security.DeviceAttestation
import java.security.GeneralSecurityException

class FakeDeviceKeyManager : DeviceKeyManager() {

    private val keys = mutableMapOf<String, String>()
    val specs = mutableMapOf<String, DeviceKeySpec>()
    var lastSpec: DeviceKeySpec? = null
    var simulatedInvalidated: Boolean = false

    override fun ensureDeviceKey(spec: DeviceKeySpec): String {
        lastSpec = spec
        if (simulatedInvalidated) {
            throw NeedsReenrollmentException("Simulated permanently invalidated key")
        }
        val existing = specs[spec.alias]
        if (existing != null) {
            if (existing.authTier != spec.authTier) {
                throw KeyNeedsRotationException("Key auth tier mismatch: ${existing.authTier} vs ${spec.authTier}")
            }
            if (existing.attestationChallenge == null) {
                throw KeyNeedsRotationException("Key was created without attestation challenge")
            }
            return spec.alias
        }
        specs[spec.alias] = spec
        if (!keys.containsKey(spec.alias)) {
            val fakeBytes = ByteArray(65) { (it + 1).toByte() }
            fakeBytes[0] = 0x04
            keys[spec.alias] = com.inscopelabs.buildengine.core.crypto.Base64Url.encode(fakeBytes)
        }
        return spec.alias
    }

    override fun publicKeyBase64(alias: String): String =
        keys[alias] ?: throw IllegalStateException("Key $alias not found")

    override fun hasDeviceKey(alias: String): Boolean = keys.containsKey(alias)

    override fun deleteDeviceKey(alias: String) {
        keys.remove(alias)
        specs.remove(alias)
    }
}

class FakeDeviceAttestation(
    deviceKeyManager: DeviceKeyManager,
) : DeviceAttestation(deviceKeyManager) {

    var chainProvider: (String) -> List<String> = { alias ->
        listOf("MIIBfakeCertChainItem1==", "MIIBfakeCertChainItem2==")
    }

    override fun keyAttestationChain(alias: String): List<String> {
        val chain = chainProvider(alias)
        if (chain.size < 2) {
            throw GeneralSecurityException("Key-attestation chain size must be at least 2 (no attestation)")
        }
        return chain
    }

    override fun hasKeyAttestation(alias: String): Boolean = try {
        chainProvider(alias).size >= 2
    } catch (_: Exception) {
        false
    }
}
