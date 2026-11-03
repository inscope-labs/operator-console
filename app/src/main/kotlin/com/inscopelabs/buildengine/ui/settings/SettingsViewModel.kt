package com.inscopelabs.buildengine.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inscopelabs.buildengine.core.network.GatewayEndpoints
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
    /** Last successful health probe, or null if none / cleared after failure. */
    val healthSnapshot: GatewayHealthSnapshot? = null,
    /** True while a health probe is in flight. */
    val healthTesting: Boolean = false,
    /** Failure message from the last Test connection attempt. */
    val healthError: String? = null,
    val credentialConfigured: Boolean,
    val session: OperatorSession?,
    val notice: SettingsNotice? = null,
    val error: SettingsError? = null,
)

enum class SettingsNotice {
    CREDENTIAL_SAVED,
    CREDENTIAL_CLEARED,
    GATEWAY_URL_SAVED,
    GATEWAY_URL_CLEARED,
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
    private val credentialStore: OperatorCredentialStore,
    private val gatewayBaseUrlStore: GatewayBaseUrlStore,
    private val healthSnapshotStore: HealthSnapshotStore,
    private val gatewayRepository: GatewayRepository,
    private val sessionRepository: SessionRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<SettingsState>>(UiState.Idle)
    val uiState: StateFlow<UiState<SettingsState>> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val baseUrl = gatewayBaseUrlStore.read()
            val configured = credentialStore.read() != null
            val snapshot = healthSnapshotStore.read()
            publish {
                it.copy(
                    gatewayBaseUrl = baseUrl,
                    gatewayConfigured = GatewayEndpoints.isConfigured(baseUrl),
                    healthSnapshot = snapshot,
                    credentialConfigured = configured,
                )
            }
        }
    }

    fun saveGatewayBaseUrl(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val trimmed = url.trim().trimEnd('/')
            if (trimmed.isNotEmpty() && !GatewayEndpoints.isConfigured(trimmed)) {
                publish { it.copy(notice = null, error = SettingsError.GATEWAY_URL_INVALID) }
                return@launch
            }
            gatewayBaseUrlStore.save(trimmed)
            val effective = gatewayBaseUrlStore.read()
            publish {
                it.copy(
                    gatewayBaseUrl = effective,
                    gatewayConfigured = GatewayEndpoints.isConfigured(effective),
                    notice = SettingsNotice.GATEWAY_URL_SAVED,
                    error = null,
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
                    notice = SettingsNotice.GATEWAY_URL_CLEARED,
                    error = null,
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
                    publish {
                        it.copy(
                            healthTesting = false,
                            healthSnapshot = snapshot,
                            healthError = null,
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
                credentialStore.save(credential)
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
                credentialStore.clear()
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

    private fun publish(transform: (SettingsState) -> SettingsState) {
        _uiState.update { current ->
            val base = (current as? UiState.Success)?.data ?: SettingsState(
                gatewayBaseUrl = "",
                gatewayConfigured = false,
                credentialConfigured = false,
                session = null,
            )
            UiState.Success(transform(base))
        }
    }
}
