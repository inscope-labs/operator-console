@file:Suppress("DEPRECATION")

package com.inscopelabs.buildengine.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.inscopelabs.buildengine.data.local.prefs.PreferencesKeys
import java.io.IOException
import java.security.GeneralSecurityException

// `EncryptedSharedPreferences` and `MasterKey` are deprecated in
// androidx.security:security-crypto 1.1.0, which points at the library's DataStore
// and Tink integration instead. They are used here deliberately: the console's own
// specification names this mechanism — docs/threat-model.md calls the operator
// credential "Keystore-backed encrypted storage (`EncryptedSharedPreferences`)",
// docs/directory-structure.md lists it as the secure-prefs dependency, and ADR 0002
// requires Keystore-backed encrypted storage. Hand-rolling Keystore encryption
// instead would be more code, more risk, and a divergence from the docs. The
// deprecation is worth raising with the team as its own decision.

/**
 * Holds the gateway-scoped operator credential.
 *
 * ### What this credential is, and what it is not
 *
 * It authorises the gateway's operator surface — requesting a capability, reading
 * status, revoking a task — and nothing else. It grants **no repository access of any
 * kind** (ADR 0002). The gateway holds the repository credential; the console never
 * does, and never will.
 *
 * ### The refusal policy is the point of this class
 *
 * ADR 0002 says the rule is mechanically checkable: *"There is no code path that
 * accepts a GitHub token. No class, no preference key, no request field."* A policy
 * that is only a sentence in a design review is a policy that erodes, one well-meaning
 * paste at a time. So [save] refuses any value shaped like a repository credential,
 * before it touches storage, and says why.
 *
 * The shapes are the ones the build engine's own secret scanner detects — `ghp_` and
 * `github_pat_`, plus the AWS access-key form the same scanner also flags. The
 * console's `util/Redactor` carries the capability signature and nonce in the same
 * forbidden set (ADR 0002), so the console is already built to recognise these
 * strings; refusing them here is the same knowledge pointed in the other direction.
 *
 * ### Storage
 *
 * The value is written to `EncryptedSharedPreferences` over an AES256-GCM master key
 * held in the Android Keystore, so the credential is encrypted at rest with a key
 * that never enters the app's process. The app asks for exactly two permissions —
 * `INTERNET` and `USE_BIOMETRIC` — and needs none to use this storage.
 *
 * ### Failure is a refusal, not a repair
 *
 * If the Keystore key has been invalidated — a changed lock screen does that — the
 * stored value can no longer be read. [read] then reports "no credential" rather than
 * throwing, because the operator's recovery is to enter it again, and a screen that
 * crashes on a stale key cannot offer that. [save] still fails loudly, because a
 * credential that cannot be written is a problem the operator must see.
 */
class OperatorCredentialStore(
    private val context: Context,
) {

    /**
     * The encrypted preferences, created on first use.
     *
     * Lazy, because building the master key reads the Keystore, and the app should
     * not pay for a credential store it has not used yet.
     */
    private val preferences: SharedPreferences by lazy { createEncryptedPreferences(context) }

    /**
     * Stores the gateway-scoped operator credential.
     *
     * @throws IllegalArgumentException if the credential is empty, or if it looks like
     *   a repository credential. Both are refusals, not repairs: the store does not
     *   quietly trim a token down to something that looks acceptable, because the
     *   operator's paste is either the credential they meant to present or it is not
     *   the console's business.
     */
    fun save(credential: String) {
        val value = credential.trim()
        require(value.isNotEmpty()) {
            "the operator credential is empty; the console presents no credential rather than an empty one"
        }
        require(!looksLikeRepositoryCredential(value)) {
            "that credential has the shape of a repository credential; the console holds no " +
                "GitHub credential of any kind (ADR 0002) and the gateway's credential is " +
                "gateway-scoped"
        }
        preferences.edit().putString(PreferencesKeys.OPERATOR_CREDENTIAL, value).apply()
    }

    /**
     * The stored credential, or `null` when there is none.
     *
     * Returns `null` rather than throwing when the stored value can no longer be
     * decrypted — a Keystore key invalidated by a lock-screen change leaves an
     * unreadable value behind, and "not configured" is the honest report.
     */
    fun read(): String? = try {
        preferences.getString(PreferencesKeys.OPERATOR_CREDENTIAL, null)
    } catch (_: GeneralSecurityException) {
        null
    } catch (_: IOException) {
        null
    }

    /**
     * Deletes the stored credential.
     *
     * This is a local deletion. It is not a revocation: the credential stays valid at
     * the gateway until the gateway's own rotation or expiry policy acts on it, and
     * that surface is the gateway's, not this store's (ADR 0002 lists the operator
     * credential as "rotatable; revocable server-side at any time").
     */
    fun clear() {
        preferences.edit().remove(PreferencesKeys.OPERATOR_CREDENTIAL).apply()
    }

    companion object {

        /**
         * The shapes of a repository credential, as the build engine's secret scanner
         * defines them.
         *
         * `gh[pousr]_` covers GitHub's personal access, OAuth, user-to-server,
         * server-to-server and refresh token prefixes; the engine's own scanner names
         * only `ghp_`, but the others are equally repository-scoped and equally
         * unwelcome here. `github_pat_` is GitHub's fine-grained token prefix, and
         * `AKIA…` is the AWS access-key form the same scanner flags.
         *
         * The match is a *search*, not an anchored match. A credential is refused
         * wherever the shape appears inside it, because the cost of refusing a
         * legitimate credential is one confused operator, and the cost of accepting a
         * repository credential is the whole crown jewel.
         */
        private val REPOSITORY_CREDENTIAL_SHAPES: List<Regex> = listOf(
            Regex("gh[pousr]_[A-Za-z0-9]{16,}"),
            Regex("github_pat_[A-Za-z0-9_]{16,}"),
            Regex("AKIA[0-9A-Z]{16}"),
        )

        /**
         * Whether [value] has the shape of a repository credential.
         *
         * Exposed so the policy can be exercised directly: the refusal is the
         * control ADR 0002 asks to be mechanically checkable, and a check that can
         * only be reached by constructing an Android-backed store is a check nobody
         * runs.
         */
        fun looksLikeRepositoryCredential(value: String): Boolean =
            REPOSITORY_CREDENTIAL_SHAPES.any { it.containsMatchIn(value) }

        private fun createEncryptedPreferences(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

        /** The encrypted preferences file. Not backed up — see [android:allowBackup]. */
        private const val FILE_NAME: String = "operator_credential"
    }
}
