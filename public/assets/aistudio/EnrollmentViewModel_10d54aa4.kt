package com.inscopelabs.buildengine.ui.enrollment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inscopelabs.buildengine.domain.model.SecurityPosture
import com.inscopelabs.buildengine.domain.usecase.enrollment.EnrollDeviceUseCase
import com.inscopelabs.buildengine.domain.usecase.enrollment.ReportDevicePostureUseCase
import com.inscopelabs.buildengine.domain.usecase.enrollment.RotateDeviceKeyUseCase
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

data class EnrollmentUiState(
    val loading: Boolean = false,
    val activeKid: String? = null,
    val posture: SecurityPosture? = null,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class EnrollmentViewModel @Inject constructor(
    private val enrollDevice: EnrollDeviceUseCase,
    private val rotateDeviceKey: RotateDeviceKeyUseCase,
    private val reportDevicePosture: ReportDevicePostureUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EnrollmentUiState())
    val uiState: StateFlow<EnrollmentUiState> = _uiState.asStateFlow()

    fun refreshPosture(alias: String) = launchAction {
        val posture = reportDevicePosture(alias)
        _uiState.update { it.copy(posture = posture, message = null, error = null) }
    }

    fun enroll(alias: String) = launchAction {
        val kid = enrollDevice(alias)
        _uiState.update { it.copy(activeKid = kid, message = "Device enrolled as $kid", error = null) }
        val posture = reportDevicePosture(alias)
        _uiState.update { it.copy(posture = posture) }
    }

    fun rotate(oldAlias: String, newAlias: String) = launchAction {
        val kid = rotateDeviceKey(oldAlias, newAlias)
        _uiState.update { it.copy(activeKid = kid, message = "Device key rotated; active key id is $kid", error = null) }
        val posture = reportDevicePosture(newAlias)
        _uiState.update { it.copy(posture = posture) }
    }

    private fun launchAction(block: suspend () -> Unit) {
        if (_uiState.value.loading) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(loading = true, error = null, message = null) }
            try {
                block()
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(error = safeMessage(error))
                }
            } finally {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    private fun safeMessage(error: Exception): String = when (error) {
        is GeneralSecurityException -> "Android Keystore or attestation refused the operation."
        is IOException -> "Secure local storage is unavailable."
        is IllegalArgumentException -> error.message ?: "The supplied value is invalid."
        else -> error.message?.take(240) ?: "The operation failed closed."
    }
}
