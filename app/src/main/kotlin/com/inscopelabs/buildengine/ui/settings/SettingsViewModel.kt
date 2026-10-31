package com.inscopelabs.buildengine.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inscopelabs.buildengine.core.network.GatewayEndpoints
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
 * The settings screen is where the operator's credential lives, so it is the screen
 * that has to be honest about whether a credential is present and never about what it
 * contains. [SettingsState] therefore carries a *flag*, not a value: the credential
 * itself is read on demand by the transport, and the one time the screen handles it
 * is the moment it is typed, which is why the typed value lives in the composable
 * and not here.
 */
data class SettingsState(
    /** The gateway this console talks to. */
    val gatewayBaseUrl: String,
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
    SESSION_STARTED,
    SESSION_ENDED,
}

/** Something the operator tried that did not work. */
enum class SettingsError {
    /** The value offered has the shape of a repository credential. See ADR 0002. */
    CREDENTIAL_REJECTED,

    /** The encrypted store could not be reached — a Keystore problem, not a mistake. */
    CREDENTIAL_STORAGE_UNAVAILABLE,
}

/**
 * The settings screen's state holder.
 *
 * ### What this screen is
 *
 * It is the console's one credential surface. The operator credential is the only
 * secret the app holds (ADR 0002), it authorises the gateway's operator surface and
 * nothing else, and until it is set every endpoint except `/health` answers `401
 * OPERATOR_AUTH`. So this screen has to be able to set it, replace it and clear it,
 * and it has to refuse a repository credential with a clear reason rather than
 * storing it and failing later at the gateway.
 *
 * ### What it deliberately does not hold
 *
 * The credential value does not pass through this class's state. The typed value
 * lives in the composable, is handed over once to be written, and is dropped. A
 * `StateFlow` is a thing that can be observed, logged and restored; a credential
 * should be in as few of those as possible.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val credentialStore: OperatorCredentialStore,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<SettingsState>>(UiState.Idle)
    val uiState: StateFlow<UiState<SettingsState>> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** Re-reads the stored credential's presence and the live session. */
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            // The first read of the encrypted store touches the Keystore, so it is
            // not done on the main thread.
            val configured = credentialStore.read() != null
            publish { it.copy(credentialConfigured = configured) }
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
                // The refusal: this value has the shape of a repository credential.
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

    /**
     * Publishes a new state.
     *
     * The update is atomic, so two rapid actions cannot interleave and leave the
     * state describing neither of them.
     */
    private fun publish(transform: (SettingsState) -> SettingsState) {
        _uiState.update { current ->
            val base = (current as? UiState.Success)?.data ?: SettingsState(
                gatewayBaseUrl = GatewayEndpoints.BASE_URL,
                credentialConfigured = false,
                session = null,
            )
            UiState.Success(transform(base))
        }
    }
}
