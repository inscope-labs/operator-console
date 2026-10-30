package com.inscopelabs.buildengine.data.local

/**
 * Holds the gateway-scoped operator credential.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §4: this credential authorises the gateway's /admin surface and
 * grants NO repository access. It must be stored in Keystore-backed encrypted
 * storage, and it must never be a GitHub token. See docs/adr/0002.
 */
class OperatorCredentialStore {

    fun save(credential: String): Unit =
        TODO("SCAFFOLD: write to EncryptedSharedPreferences over a Keystore key.")

    fun read(): String? = TODO("SCAFFOLD: read the stored credential.")

    fun clear(): Unit = TODO("SCAFFOLD: revoke and delete the stored credential.")
}
