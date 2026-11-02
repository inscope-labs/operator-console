package com.inscopelabs.buildengine.testutil

import com.inscopelabs.buildengine.core.crypto.DeviceKeyManager
import com.inscopelabs.buildengine.core.crypto.DeviceKeySpec
import com.inscopelabs.buildengine.core.security.DeviceAttestation
import java.security.GeneralSecurityException

class FakeDeviceKeyManager : DeviceKeyManager() {

    private val keys = mutableMapOf<String, String>()

    override fun ensureDeviceKey(spec: DeviceKeySpec): String {
        if (!keys.containsKey(spec.alias)) {
            // Generate simulated 65-byte uncompressed EC point and base64url encode it
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
        if (chain.isEmpty()) throw GeneralSecurityException("Key-attestation chain is empty")
        return chain
    }

    override fun hasKeyAttestation(alias: String): Boolean = try {
        chainProvider(alias).isNotEmpty()
    } catch (_: Exception) {
        false
    }
}
