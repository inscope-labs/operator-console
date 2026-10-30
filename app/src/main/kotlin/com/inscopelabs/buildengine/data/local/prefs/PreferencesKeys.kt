package com.inscopelabs.buildengine.data.local.prefs

/**
 * Preference keys.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 */
object PreferencesKeys {

    const val SELECTED_REPOSITORY: String = "selected_repository"
    const val SELECTED_PLAN: String = "selected_plan"

    const val DEVICE_KID: String = "device_kid"
    const val DEVICE_KEY_ALIAS: String = "device_key_alias"
    const val DEVICE_PUBLIC_KEY: String = "device_public_key"

    /** Design review §4: a gateway-scoped credential. Never a GitHub token. */
    const val OPERATOR_CREDENTIAL: String = "operator_credential"
}
