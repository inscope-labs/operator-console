package com.inscopelabs.buildengine.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.inscopelabs.buildengine.core.network.GatewayActivityEvent
import com.inscopelabs.buildengine.core.network.GatewayActivityLog
import com.inscopelabs.buildengine.core.network.GatewayEndpoints
import com.inscopelabs.buildengine.diagnostics.DiagnosticPreferences
import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.core.time.Clock
import com.inscopelabs.buildengine.data.local.GatewayBaseUrlStore
import com.inscopelabs.buildengine.data.local.HealthSnapshotStore
import com.inscopelabs.buildengine.data.local.OperatorCredentialStore
import com.inscopelabs.buildengine.data.remote.GatewayRepository
import com.inscopelabs.buildengine.domain.model.GatewayHealthSnapshot
import com.inscopelabs.buildengine.domain.model.OperatorSession
import com.inscopelabs.buildengine.domain.repository.SessionRepository
import com.inscopelabs.buildengine.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Console settings state.
 *
 * Includes the gateway base URL, last health snapshot from **Test connection**,
 * credential presence, and session.
 */
data class SettingsState(
    val gatewayBaseUrl: String,
    val gatewayConfigured: Boolean,
    val expectedEnvironment: String = "test",
    /** Last successful health probe, or null if none / cleared after failure. */
    val healthSnapshot: GatewayHealthSnapshot? = null,
    /** True while a health probe is in flight. */
    val healthTesting: Boolean = false,
    /** Failure message from the last Test connection attempt. */
    val healthError: String? = null,
    val credentialConfigured: Boolean,
    val environmentMismatch: Boolean = false,
    val environmentMismatchMessage: String? = null,
    val session: OperatorSession?,
    /** When true, gateway exchanges are recorded in the activity log. */
    val gatewayActivityLogEnabled: Boolean = false,
    /** Recent gateway events (newest first), only meaningful when diagnostics enabled. */
    val gatewayActivityEvents: List<GatewayActivityEvent> = emptyList(),
    val notice: SettingsNotice? = null,
    val error: SettingsError? = null,
    val customError: String? = null,
)

enum class SettingsNotice {
    CREDENTIAL_SAVED,
    CREDENTIAL_CLEARED,
    GATEWAY_URL_SAVED,
    GATEWAY_URL_CLEARED,
    EXPECTED_ENV_SAVED,
    HEALTH_OK,
    SESSION_STARTED,
    SESSION_ENDED,
}

enum class SettingsError {
    CREDENTIAL_REJECTED,
    CREDENTIAL_STORAGE_UNAVAILABLE,
    GATEWAY_URL_INVALID,
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val credentialStore: OperatorCredentialStore,
    private val gatewayBaseUrlStore: GatewayBaseUrlStore,
    private val healthSnapshotStore: HealthSnapshotStore,
    private val gatewayRepository: GatewayRepository,
    private val sessionRepository: SessionRepository,
    private val clock: Clock,
    private val activityLog: GatewayActivityLog,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<SettingsState>>(UiState.Idle)
    val uiState: StateFlow<UiState<SettingsState>> = _uiState.asStateFlow()

    init {
        activityLog.isEnabled = DiagnosticPreferences.isGatewayActivityLogEnabled(appContext)
        refresh()
        viewModelScope.launch {
            activityLog.events.collect { events ->
                publish { it.copy(gatewayActivityEvents = events) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val baseUrl = gatewayBaseUrlStore.read()
            val expectedEnv = gatewayBaseUrlStore.readExpectedEnvironment()
            val configured = credentialStore.read(expectedEnv) != null
            val snapshot = healthSnapshotStore.read()
            val logEnabled = DiagnosticPreferences.isGatewayActivityLogEnabled(appContext)
            activityLog.isEnabled = logEnabled

            val mismatch = snapshot != null && snapshot.environment.isNotEmpty() && !snapshot.environment.equals(expectedEnv, ignoreCase = true)
            val mismatchMessage = if (mismatch) formatMismatchMessage(expectedEnv, snapshot.environment) else null

            publish {
                it.copy(
                    gatewayBaseUrl = baseUrl,
                    gatewayConfigured = GatewayEndpoints.isConfigured(baseUrl),
                    expectedEnvironment = expectedEnv,
                    healthSnapshot = snapshot,
                    credentialConfigured = configured,
                    environmentMismatch = mismatch,
                    environmentMismatchMessage = mismatchMessage,
                    gatewayActivityLogEnabled = logEnabled,
                    gatewayActivityEvents = activityLog.events.value,
                )
            }
        }
    }

    fun saveExpectedEnvironment(environment: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                gatewayBaseUrlStore.saveExpectedEnvironment(environment)
                val expectedEnv = gatewayBaseUrlStore.readExpectedEnvironment()
                val configured = credentialStore.read(expectedEnv) != null
                val snapshot = healthSnapshotStore.read()
                val mismatch = snapshot != null && snapshot.environment.isNotEmpty() && !snapshot.environment.equals(expectedEnv, ignoreCase = true)
                val mismatchMessage = if (mismatch) formatMismatchMessage(expectedEnv, snapshot.environment) else null

                publish {
                    it.copy(
                        expectedEnvironment = expectedEnv,
                        credentialConfigured = configured,
                        environmentMismatch = mismatch,
                        environmentMismatchMessage = mismatchMessage,
                        notice = SettingsNotice.EXPECTED_ENV_SAVED,
                        error = null,
                        customError = null,
                    )
                }
            } catch (e: IllegalArgumentException) {
                publish { it.copy(notice = null, customError = e.message) }
            }
        }
    }

    fun saveGatewayBaseUrl(url: String, isDebug: Boolean = com.inscopelabs.buildengine.BuildConfig.DEBUG) {
        viewModelScope.launch(Dispatchers.IO) {
            val trimmed = url.trim().trimEnd('/')
            if (trimmed.isNotEmpty()) {
                val validation = GatewayEndpoints.validateUrl(trimmed, isDebug = isDebug)
                if (!validation.isValid) {
                    val reason = (validation as? GatewayEndpoints.UrlValidationResult.Invalid)?.reason
                    publish {
                        it.copy(
                            notice = null,
                            error = SettingsError.GATEWAY_URL_INVALID,
                            customError = reason,
                        )
                    }
                    return@launch
                }
            }
            gatewayBaseUrlStore.save(trimmed)
            val effective = gatewayBaseUrlStore.read()
            publish {
                it.copy(
                    gatewayBaseUrl = effective,
                    gatewayConfigured = GatewayEndpoints.isConfigured(effective, isDebug = isDebug),
                    notice = SettingsNotice.GATEWAY_URL_SAVED,
                    error = null,
                    customError = null,
                )
            }
        }
    }

    fun clearGatewayBaseUrl() {
        viewModelScope.launch(Dispatchers.IO) {
            gatewayBaseUrlStore.clear()
            healthSnapshotStore.clear()
            val effective = gatewayBaseUrlStore.read()
            publish {
                it.copy(
                    gatewayBaseUrl = effective,
                    gatewayConfigured = GatewayEndpoints.isConfigured(effective),
                    healthSnapshot = null,
                    healthError = null,
                    environmentMismatch = false,
                    environmentMismatchMessage = null,
                    notice = SettingsNotice.GATEWAY_URL_CLEARED,
                    error = null,
                    customError = null,
                )
            }
        }
    }

    /**
     * Explicit connect: `GET /health`.
     *
     * On success, persists the snapshot (environment, version, timestamp) for the
     * status banner. On failure, clears any prior snapshot so the shell does not
     * claim connectivity it no longer has.
     */
    fun testConnection() {
        viewModelScope.launch(Dispatchers.IO) {
            publish { it.copy(healthTesting = true, healthError = null, notice = null, error = null) }
            when (val result = gatewayRepository.health()) {
                is AppResult.Success -> {
                    val dto = result.value
                    val snapshot = GatewayHealthSnapshot(
                        status = dto.status,
                        environment = dto.environment,
                        version = dto.version,
                        timestampMillis = dto.timestamp,
                        probedAtEpochSeconds = clock.nowEpochSeconds(),
                    )
                    healthSnapshotStore.save(snapshot)
                    val expectedEnv = gatewayBaseUrlStore.readExpectedEnvironment()
                    val mismatch = snapshot.environment.isNotEmpty() && !snapshot.environment.equals(expectedEnv, ignoreCase = true)
                    val mismatchMessage = if (mismatch) formatMismatchMessage(expectedEnv, snapshot.environment) else null

                    publish {
                        it.copy(
                            expectedEnvironment = expectedEnv,
                            healthTesting = false,
                            healthSnapshot = snapshot,
                            healthError = null,
                            environmentMismatch = mismatch,
                            environmentMismatchMessage = mismatchMessage,
                            notice = SettingsNotice.HEALTH_OK,
                        )
                    }
                }
                is AppResult.Failure -> {
                    healthSnapshotStore.clear()
                    publish {
                        it.copy(
                            healthTesting = false,
                            healthSnapshot = null,
                            healthError = result.message,
                            environmentMismatch = false,
                            environmentMismatchMessage = null,
                            notice = null,
                        )
                    }
                }
            }
        }
    }

    fun saveCredential(credential: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentEnv = gatewayBaseUrlStore.readExpectedEnvironment()
                credentialStore.save(credential, currentEnv)
                publish {
                    it.copy(
                        credentialConfigured = true,
                        notice = SettingsNotice.CREDENTIAL_SAVED,
                        error = null,
                    )
                }
            } catch (_: IllegalArgumentException) {
                publish { it.copy(notice = null, error = SettingsError.CREDENTIAL_REJECTED) }
            } catch (_: GeneralSecurityException) {
                publish {
                    it.copy(notice = null, error = SettingsError.CREDENTIAL_STORAGE_UNAVAILABLE)
                }
            } catch (_: IOException) {
                publish {
                    it.copy(notice = null, error = SettingsError.CREDENTIAL_STORAGE_UNAVAILABLE)
                }
            }
        }
    }

    fun clearCredential() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentEnv = gatewayBaseUrlStore.readExpectedEnvironment()
                credentialStore.clear(currentEnv)
                publish {
                    it.copy(
                        credentialConfigured = false,
                        notice = SettingsNotice.CREDENTIAL_CLEARED,
                        error = null,
                    )
                }
            } catch (_: GeneralSecurityException) {
                publish {
                    it.copy(notice = null, error = SettingsError.CREDENTIAL_STORAGE_UNAVAILABLE)
                }
            } catch (_: IOException) {
                publish {
                    it.copy(notice = null, error = SettingsError.CREDENTIAL_STORAGE_UNAVAILABLE)
                }
            }
        }
    }

    fun startSession() {
        viewModelScope.launch(Dispatchers.IO) {
            val session = sessionRepository.start()
            publish {
                it.copy(
                    session = session,
                    notice = SettingsNotice.SESSION_STARTED,
                    error = null,
                )
            }
        }
    }

    fun endSession() {
        viewModelScope.launch(Dispatchers.IO) {
            sessionRepository.end()
            publish {
                it.copy(
                    session = null,
                    notice = SettingsNotice.SESSION_ENDED,
                    error = null,
                )
            }
        }
    }


    fun setGatewayActivityLogEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            DiagnosticPreferences.setGatewayActivityLogEnabled(appContext, enabled)
            activityLog.isEnabled = enabled
            if (!enabled) {
                activityLog.clear()
            }
            publish {
                it.copy(
                    gatewayActivityLogEnabled = enabled,
                    gatewayActivityEvents = if (enabled) activityLog.events.value else emptyList(),
                )
            }
        }
    }

    fun clearGatewayActivityLog() {
        activityLog.clear()
        publish { it.copy(gatewayActivityEvents = emptyList()) }
    }

    /** Redacted activity log text for system share sheet. */
    fun gatewayActivityExportText(): String = activityLog.exportText()

    private fun formatMismatchMessage(expected: String, reported: String): String = try {
        appContext.getString(com.inscopelabs.buildengine.R.string.settings_env_mismatch_warning, expected, reported)
    } catch (_: Exception) {
        "Environment mismatch: configured expected environment '$expected' does not match gateway reported environment '$reported'. All privileged actions are disabled."
    }

    private fun publish(transform: (SettingsState) -> SettingsState) {
        _uiState.update { current ->
            val base = (current as? UiState.Success)?.data ?: SettingsState(
                gatewayBaseUrl = "",
                gatewayConfigured = false,
                expectedEnvironment = "test",
                credentialConfigured = false,
                session = null,
            )
            UiState.Success(transform(base))
        }
    }
}
