package com.inscopelabs.buildengine.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.inscopelabs.buildengine.core.network.GatewayEndpoints
import com.inscopelabs.buildengine.data.local.prefs.PreferencesKeys
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/**
 * The operator-configured gateway base URL.
 *
 * Default is **unset**. A scaffold placeholder (`gateway.invalid`) is never treated as
 * configured. Operators set the real host in Settings without rebuilding; debug builds
 * may optionally seed a LAN default via BuildConfig without shipping production URLs
 * in release source.
 *
 * Plain DataStore storage: a host name is not a secret.
 */
class GatewayBaseUrlStore(
    private val dataStore: DataStore<Preferences>,
    private val debugDefault: String? = null,
) {

    private val key = stringPreferencesKey(PreferencesKeys.GATEWAY_BASE_URL)

    /** Emits the effective base URL (stored, else debug default, else empty). */
    val baseUrlFlow: Flow<String> = dataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { preferences -> resolve(preferences[key]) }

    /**
     * The effective base URL for transport.
     *
     * Order: stored non-blank value → debug default (if any) → empty string (not configured).
     * Blocking read is intentional for the credential-style provider used on the HTTP path.
     */
    fun current(): String = try {
        runBlocking {
            val stored = dataStore.data.first()[key]
            resolve(stored)
        }
    } catch (_: IOException) {
        resolve(null)
    } catch (_: Exception) {
        resolve(null)
    }

    /** Suspend read of the effective base URL. */
    suspend fun read(): String = try {
        val stored = dataStore.data.first()[key]
        resolve(stored)
    } catch (_: IOException) {
        resolve(null)
    }

    /**
     * Persists [url] as the gateway base URL.
     *
     * Blank clears the override. Values that fail [GatewayEndpoints.isConfigured]
     * are still stored so the operator can see what they typed; the transport refuses
     * to dial them.
     */
    suspend fun save(url: String) {
        val trimmed = url.trim().trimEnd('/')
        dataStore.edit { preferences ->
            if (trimmed.isEmpty()) {
                preferences.remove(key)
            } else {
                preferences[key] = trimmed
            }
        }
    }

    /** Removes the stored base URL (debug default may still apply). */
    suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(key)
        }
    }

    private fun resolve(stored: String?): String {
        val candidate = stored?.trim()?.trimEnd('/').orEmpty()
        if (candidate.isNotEmpty()) return candidate
        val debug = debugDefault?.trim()?.trimEnd('/').orEmpty()
        if (debug.isNotEmpty()) return debug
        return ""
    }
}
