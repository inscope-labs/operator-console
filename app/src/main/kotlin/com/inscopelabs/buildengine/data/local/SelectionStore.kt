package com.inscopelabs.buildengine.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.inscopelabs.buildengine.data.local.prefs.PreferencesKeys
import java.io.IOException
import kotlinx.coroutines.flow.first

/**
 * The operator's repository and plan selection.
 *
 * Design review §1: selection state stays on the device, and it is **not security
 * state** — it is a pointer at what the operator is currently looking at. So plain
 * application storage is sufficient, and this store uses DataStore Preferences
 * rather than the encrypted store that holds the operator credential.
 *
 * That distinction is the reason this class and [OperatorCredentialStore] are
 * separate. Splitting them means the encrypted surface stays as small as the design
 * allows: exactly one value, the operator credential, is worth the Keystore round
 * trip, and everything else is a name.
 *
 * The [DataStore] is injected rather than created here, because a DataStore may only
 * be instantiated once per file. Two instances of one file is not a style question —
 * DataStore throws when it detects it.
 */
class SelectionStore(
    private val dataStore: DataStore<Preferences>,
) {

    /** The selected plan id, or `null` when nothing is selected. */
    suspend fun selectedPlan(): String? = read(PreferencesKeys.SELECTED_PLAN)

    /** Records the selected plan. An empty id clears the selection. */
    suspend fun selectPlan(planId: String) {
        write(PreferencesKeys.SELECTED_PLAN, planId)
    }

    /** The selected repository, or `null` when nothing is selected. */
    suspend fun selectedRepository(): String? = read(PreferencesKeys.SELECTED_REPOSITORY)

    /** Records the selected repository. An empty value clears the selection. */
    suspend fun selectRepository(repository: String) {
        write(PreferencesKeys.SELECTED_REPOSITORY, repository)
    }

    /**
     * Reads one selection.
     *
     * A read that fails because the file is unreadable reports "nothing selected"
     * rather than throwing: the selection is a convenience, and a convenience is not
     * worth taking the screen down.
     */
    private suspend fun read(key: String): String? = try {
        dataStore.data.first()[stringPreferencesKey(key)]
    } catch (_: IOException) {
        null
    }

    /**
     * Writes one selection.
     *
     * An empty value removes the key rather than storing an empty string, so
     * "nothing selected" has exactly one representation on disk.
     */
    private suspend fun write(key: String, value: String) {
        val trimmed = value.trim()
        dataStore.edit { preferences ->
            val preferenceKey = stringPreferencesKey(key)
            if (trimmed.isEmpty()) {
                preferences.remove(preferenceKey)
            } else {
                preferences[preferenceKey] = trimmed
            }
        }
    }
}
