package com.inscopelabs.buildengine.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.inscopelabs.buildengine.data.local.prefs.PreferencesKeys
import com.inscopelabs.buildengine.domain.model.GatewayHealthSnapshot
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Persists the last successful [GatewayHealthSnapshot] for the connection status banner.
 *
 * Plain storage: health metadata is not a secret.
 */
class HealthSnapshotStore(
    private val dataStore: DataStore<Preferences>,
) {

    private val statusKey = stringPreferencesKey(PreferencesKeys.HEALTH_STATUS)
    private val environmentKey = stringPreferencesKey(PreferencesKeys.HEALTH_ENVIRONMENT)
    private val versionKey = stringPreferencesKey(PreferencesKeys.HEALTH_VERSION)
    private val timestampKey = longPreferencesKey(PreferencesKeys.HEALTH_TIMESTAMP_MILLIS)
    private val probedAtKey = longPreferencesKey(PreferencesKeys.HEALTH_PROBED_AT_EPOCH)

    val snapshotFlow: Flow<GatewayHealthSnapshot?> = dataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { preferences -> preferences.toSnapshot() }

    suspend fun read(): GatewayHealthSnapshot? = try {
        dataStore.data.first().toSnapshot()
    } catch (_: IOException) {
        null
    }

    suspend fun save(snapshot: GatewayHealthSnapshot) {
        dataStore.edit { preferences ->
            preferences[statusKey] = snapshot.status
            preferences[environmentKey] = snapshot.environment
            preferences[versionKey] = snapshot.version
            preferences[timestampKey] = snapshot.timestampMillis
            preferences[probedAtKey] = snapshot.probedAtEpochSeconds
        }
    }

    suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(statusKey)
            preferences.remove(environmentKey)
            preferences.remove(versionKey)
            preferences.remove(timestampKey)
            preferences.remove(probedAtKey)
        }
    }

    private fun Preferences.toSnapshot(): GatewayHealthSnapshot? {
        val environment = this[environmentKey] ?: return null
        val status = this[statusKey] ?: return null
        val version = this[versionKey] ?: ""
        val timestamp = this[timestampKey] ?: 0L
        val probedAt = this[probedAtKey] ?: 0L
        return GatewayHealthSnapshot(
            status = status,
            environment = environment,
            version = version,
            timestampMillis = timestamp,
            probedAtEpochSeconds = probedAt,
        )
    }
}
