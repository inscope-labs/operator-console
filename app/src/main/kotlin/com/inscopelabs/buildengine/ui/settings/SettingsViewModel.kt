package com.inscopelabs.buildengine.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inscopelabs.buildengine.core.network.GatewayEndpoints
import com.inscopelabs.buildengine.data.local.GatewayBaseUrlStore
import com.inscopelabs.buildengine.data.local.OperatorCredentialStore
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
 * The settings screen is where the operator's credential and gateway base URL live.
 * [SettingsState] carries a *flag* for the credential, not its value. The gateway URL
 * is shown and edited here so operators can point at a real host without rebuilding.
 */
data class SettingsState(
    /** The effective gateway base URL (stored, debug default, or empty). */
    val gatewayBaseUrl: String,
    /** Whether the effective URL is considered configured for transport. */
    val gatewayConfigured: Boolean,
    /** Whether an operator credential is stored. Never the credential itself. */
    val credentialConfigured: Boolean,
    /** The live session, or `null` when none is open. */
    val session: OperatorSession?,
    /** The last successful action, for the screen to report. */
    val notice: SettingsNotice? = null,
    /** The last failed action, for the screen to report. */
    val error: SettingsError? = null,
)

/** Something the operator did that worked. */
enum class SettingsNotice {
    CREDENTIAL_SAVED,
    CREDENTIAL_CLEARED,
    GATEWAY_URL_SAVED,
    GATEWAY_URL_CLEARED,
    SESSION_STARTED,
    SESSION_ENDED,
}

/** Something the operator tried that did not work. */
enum class SettingsError {
    /** The value offered has the shape of a repository credential. See ADR 0002. */
    CREDENTIAL_REJECTED,

    /** The encrypted store could not be reached — a Keystore problem, not a mistake. */
    CREDENTIAL_STORAGE_UNAVAILABLE,

    /** The gateway URL was refused as empty or a scaffold placeholder when saving was required. */
    GATEWAY_URL_INVALID,
}

/**
 * The settings screen's state holder.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val credentialStore: OperatorCredentialStore,
    private val gatewayBaseUrlStore: GatewayBaseUrlStore,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<SettingsState>>(UiState.Idle)
    val uiState: StateFlow<UiState<SettingsState>> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** Re-reads gateway URL, credential presence, and the live session. */
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val baseUrl = gatewayBaseUrlStore.read()
            val configured = credentialStore.read() != null
            publish {
                it.copy(
                    gatewayBaseUrl = baseUrl,
                    gatewayConfigured = GatewayEndpoints.isConfigured(baseUrl),
                    credentialConfigured = configured,
                )
            }
        }
    }

    /**
     * Persists the gateway base URL.
     *
     * Blank [url] clears the stored override. Placeholder hosts are rejected so the
     * operator does not believe the scaffold is a live endpoint.
     */
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

    /** Clears the stored gateway base URL (debug default may still apply). */
    fun clearGatewayBaseUrl() {
        viewModelScope.launch(Dispatchers.IO) {
            gatewayBaseUrlStore.clear()
            val effective = gatewayBaseUrlStore.read()
            publish {
                it.copy(
                    gatewayBaseUrl = effective,
                    gatewayConfigured = GatewayEndpoints.isConfigured(effective),
                    notice = SettingsNotice.GATEWAY_URL_CLEARED,
                    error = null,
                )
            }
        }
    }

    /**
     * Stores the operator credential.
     *
     * @param credential the value the operator typed. It is validated and written
     *   here and is not retained.
     */
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

    /** Deletes the stored operator credential. */
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

    /** Opens a session. */
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

    /** Ends the session. */
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
