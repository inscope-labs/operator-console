package com.inscopelabs.buildengine.data.local.prefs

/**
 * The preference keys the console writes.
 *
 * Every key the app stores is named here, so that "what does this app persist, and
 * where" is one file to read rather than a grep across the tree.
 *
 * The keys fall into two groups, and the difference matters:
 *
 * - **Selection state** — which repository and which plan the operator is looking at.
 *   This is not security state (design review §1), so it lives in plain application
 *   storage. Losing it costs nothing.
 * - **Security state** — the operator credential, and the device's enrolment record.
 *   This is written to Keystore-backed encrypted storage, because losing it is not
 *   nothing and because a leaked credential is a leaked credential.
 *
 * ### What is deliberately absent
 *
 * There is no key for a GitHub token, a GitHub App credential, an OAuth token, a
 * personal access token, a repository clone URL with a token in it, or a signing key.
 * ADR 0002 requires that the console hold no repository credential of any kind, and
 * that it be mechanically checkable rather than a matter of reviewer vigilance. The
 * absence of these keys is part of that control: [SECRET_KEYS] names the one key in
 * this file whose value is a secret, and a key for a repository credential would have
 * to be added to this object to exist.
 *
 * The device's *private* key is not here either, and never could be: it is generated
 * inside the Android Keystore and is not exportable (design review §5). Only its
 * public half, its alias and its selector — the `kid` — are stored.
 */
object PreferencesKeys {

    /** The selected repository. Plain storage: it is a name, not a secret. */
    const val SELECTED_REPOSITORY: String = "selected_repository"

    /** The selected plan. Plain storage: it is an identifier, not a secret. */
    const val SELECTED_PLAN: String = "selected_plan"

    /** The device key's selector. A selector, never a secret — the gateway resolves it. */
    const val DEVICE_KID: String = "device_kid"

    /** The alias of the device key inside the app's own Keystore. */
    const val DEVICE_KEY_ALIAS: String = "device_key_alias"

    /** The device key's public half, base64. Public by definition. */
    const val DEVICE_PUBLIC_KEY: String = "device_public_key"

    /**
     * The gateway-scoped operator credential.
     *
     * Design review §4: this credential authorises the gateway's operator surface and
     * grants no repository access whatsoever. It is the only secret in this file.
     */
    const val OPERATOR_CREDENTIAL: String = "operator_credential"

    /**
     * The keys whose values are secret, and therefore the only keys that belong in
     * encrypted storage.
     *
     * Anything added to this set must also be written somewhere encrypted. A secret
     * written to plain storage is a leak with a delay.
     */
    val SECRET_KEYS: Set<String> = setOf(OPERATOR_CREDENTIAL)

    /**
     * The keys that are written in plain application storage, because their values
     * are not secret.
     */
    val PLAIN_KEYS: Set<String> =
        setOf(SELECTED_REPOSITORY, SELECTED_PLAN, DEVICE_KID, DEVICE_KEY_ALIAS, DEVICE_PUBLIC_KEY)
}
