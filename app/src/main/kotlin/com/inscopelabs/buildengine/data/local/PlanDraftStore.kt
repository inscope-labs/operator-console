package com.inscopelabs.buildengine.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.inscopelabs.buildengine.domain.model.PlanDraft
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Where plan drafts live on the device.
 *
 * A draft is authoring state: a document the operator is writing, kept beside them, that has
 * not touched the repository. It is stored in plain application storage, separate from the
 * selection store and from the gateway URL store, because it is a different concern with a
 * different lifetime — and because a draft may run to tens of kilobytes, which is more than a
 * selection preference is for.
 *
 * A draft holds no secret. It is a plan document: identifiers, titles, acceptance criteria and
 * paths. The operator credential, the device key and every capability live elsewhere, and a
 * draft never carries them (ADR 0002).
 */
class PlanDraftStore(
    private val dataStore: DataStore<Preferences>,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Every stored draft. A draft that cannot be decoded is skipped, not fatal. */
    suspend fun listDrafts(): List<PlanDraft> {
        val preferences = try {
            dataStore.data.first()
        } catch (_: IOException) {
            return emptyList()
        }
        return preferences.asMap()
            .filterKeys { name -> name.startsWith(DRAFT_KEY_PREFIX) }
            .mapNotNull { (_, value) -> decode(value as? String) }
    }

    /** One stored draft, or null when it is not on the device. */
    suspend fun read(draftId: String): PlanDraft? {
        val preferences = try {
            dataStore.data.first()
        } catch (_: IOException) {
            return null
        }
        return decode(preferences[draftKey(draftId)])
    }

    /** Writes a draft, replacing any earlier copy of it. */
    suspend fun write(draft: PlanDraft) {
        val encoded = json.encodeToString(draft)
        dataStore.edit { preferences ->
            preferences[draftKey(draft.draftId)] = encoded
        }
    }

    /** Removes a draft. Returns false when there was nothing stored under that id. */
    suspend fun delete(draftId: String): Boolean {
        var removed = false
        dataStore.edit { preferences ->
            removed = preferences.remove(draftKey(draftId)) != null
        }
        return removed
    }

    private fun decode(raw: String?): PlanDraft? {
        if (raw.isNullOrBlank()) return null
        return try {
            json.decodeFromString<PlanDraft>(raw)
        } catch (_: Exception) {
            null
        }
    }

    private fun draftKey(draftId: String) = stringPreferencesKey("$DRAFT_KEY_PREFIX$draftId")

    companion object {
        private const val DRAFT_KEY_PREFIX: String = "pma.draft."
    }
}
