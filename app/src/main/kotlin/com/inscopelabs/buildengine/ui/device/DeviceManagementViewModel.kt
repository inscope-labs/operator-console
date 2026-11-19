package com.inscopelabs.buildengine.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inscopelabs.buildengine.core.crypto.DeviceKeyManager
import com.inscopelabs.buildengine.core.crypto.DeviceKeySpec
import com.inscopelabs.buildengine.core.crypto.DeviceKeyStatus
import com.inscopelabs.buildengine.core.network.ErrorMapper
import com.inscopelabs.buildengine.core.network.GatewayHttpException
import com.inscopelabs.buildengine.core.network.GatewayNotConfiguredException
import com.inscopelabs.buildengine.core.network.GatewayProtocolException
import com.inscopelabs.buildengine.core.network.dto.EnrolledDeviceDto
import com.inscopelabs.buildengine.core.network.dto.RevokeKeyRequestDto
import com.inscopelabs.buildengine.core.security.AuthTier
import com.inscopelabs.buildengine.data.enrollment.DeviceEnrollmentStore
import com.inscopelabs.buildengine.data.remote.GatewayApi
import com.inscopelabs.buildengine.domain.usecase.enrollment.RevokeDeviceKeyUseCase
import com.inscopelabs.buildengine.ui.common.UiState
import com.inscopelabs.buildengine.util.Redactor
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.net.ssl.SSLPeerUnverifiedException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State holder for [DeviceManagementScreen] (Stage 4.4 / T4-401, PoC Tasks 23–24, PR3-032).
 *
 * Reads active enrolments from `GET /admin/enrolments`, checks local Keystore health via
 * [DeviceKeyManager], and routes revocation of the local active key through
 * [RevokeDeviceKeyUseCase] so retiring this device's key transitions cleanly to
 * [DeviceKeyStatus.NeedsReenrollment].
 */
@HiltViewModel
class DeviceManagementViewModel @Inject constructor(
    private val gatewayApi: GatewayApi,
    private val enrollmentStore: DeviceEnrollmentStore,
    private val deviceKeyManager: DeviceKeyManager,
    private val revokeDeviceKeyUseCase: RevokeDeviceKeyUseCase,
    private val errorMapper: ErrorMapper = ErrorMapper(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<DeviceManagementData>>(UiState.Idle)
    val uiState: StateFlow<UiState<DeviceManagementData>> = _uiState.asStateFlow()

    fun load() {
        val previousData = (_uiState.value as? UiState.Success)?.data
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val devices = gatewayApi.listEnrolments().enrolments.map(::toUiDevice)
                val (localKid, keyStatus) = readLocalKeyState()
                val invalidatedReason = (keyStatus as? DeviceKeyStatus.NeedsReenrollment)?.reason
                val needsReenrollment = invalidatedReason != null ||
                    (previousData?.needsReenrollment == true && localKid.isEmpty())

                _uiState.value = UiState.Success(
                    DeviceManagementData(
                        devices = devices,
                        localActiveKid = localKid,
                        revokeOutcome = previousData?.revokeOutcome,
                        needsReenrollment = needsReenrollment,
                        keyStatusReason = invalidatedReason ?: if (needsReenrollment) previousData?.keyStatusReason else null,
                        isRevoking = false,
                    ),
                )
            } catch (error: Throwable) {
                _uiState.value = UiState.Error(mapFailureMessage(error))
            }
        }
    }

    fun revoke(kid: String, reason: String) {
        val targetKid = kid.trim()
        val effectiveReason = reason.trim().ifBlank { DEFAULT_REVOKE_REASON }
        if (targetKid.isEmpty()) return

        val current = (_uiState.value as? UiState.Success)?.data ?: return
        _uiState.value = UiState.Success(current.copy(isRevoking = true))

        viewModelScope.launch {
            val activeRecord = enrollmentStore.activeRecord()
            if (activeRecord != null && activeRecord.kid == targetKid) {
                when (val result = revokeDeviceKeyUseCase(effectiveReason)) {
                    is RevokeDeviceKeyUseCase.Result.Revoked -> {
                        val refreshedDevices = refreshDevicesOrFallback(current.devices.filterNot { it.kid == result.kid })
                        val latest = (_uiState.value as? UiState.Success)?.data ?: current
                        _uiState.value = UiState.Success(
                            latest.copy(
                                devices = refreshedDevices,
                                localActiveKid = "",
                                revokeOutcome = RevokeOutcome(
                                    kid = result.kid,
                                    revoked = true,
                                    revokedAtEpochSeconds = result.revokedAt,
                                    auditRef = null,
                                ),
                                needsReenrollment = true,
                                keyStatusReason = "Local device key ${result.kid} was revoked; enrol a new key to sign operations.",
                                isRevoking = false,
                            ),
                        )
                    }
                    is RevokeDeviceKeyUseCase.Result.GatewayRefused -> {
                        val latest = (_uiState.value as? UiState.Success)?.data ?: current
                        _uiState.value = UiState.Success(
                            latest.copy(
                                revokeOutcome = RevokeOutcome(
                                    kid = targetKid,
                                    revoked = false,
                                    revokedAtEpochSeconds = null,
                                    auditRef = null,
                                    errorMessage = Redactor.redact(result.detail),
                                ),
                                isRevoking = false,
                            ),
                        )
                    }
                    is RevokeDeviceKeyUseCase.Result.Unreachable -> {
                        val latest = (_uiState.value as? UiState.Success)?.data ?: current
                        _uiState.value = UiState.Success(
                            latest.copy(
                                revokeOutcome = RevokeOutcome(
                                    kid = targetKid,
                                    revoked = false,
                                    revokedAtEpochSeconds = null,
                                    auditRef = null,
                                    errorMessage = Redactor.redact(result.detail),
                                ),
                                isRevoking = false,
                            ),
                        )
                    }
                    RevokeDeviceKeyUseCase.Result.NotEnrolled -> {
                        val latest = (_uiState.value as? UiState.Success)?.data ?: current
                        _uiState.value = UiState.Success(
                            latest.copy(
                                localActiveKid = "",
                                revokeOutcome = RevokeOutcome(
                                    kid = targetKid,
                                    revoked = false,
                                    revokedAtEpochSeconds = null,
                                    auditRef = null,
                                    errorMessage = "Device is not currently enrolled locally.",
                                ),
                                needsReenrollment = true,
                                keyStatusReason = "No active local enrolment found; enrol a new device key.",
                                isRevoking = false,
                            ),
                        )
                    }
                }
                return@launch
            }

            try {
                val response = gatewayApi.revokeKey(
                    RevokeKeyRequestDto(
                        kid = targetKid,
                        reason = effectiveReason,
                        confirmation = revokeConfirmationFor(targetKid),
                    ),
                )
                val accepted = response.revoked && response.kid == targetKid
                val refreshedDevices = if (accepted) {
                    refreshDevicesOrFallback(current.devices.filterNot { it.kid == targetKid })
                } else {
                    current.devices
                }
                val (localKid, keyStatus) = readLocalKeyState()
                val invalidatedReason = (keyStatus as? DeviceKeyStatus.NeedsReenrollment)?.reason
                val latest = (_uiState.value as? UiState.Success)?.data ?: current
                _uiState.value = UiState.Success(
                    latest.copy(
                        devices = refreshedDevices,
                        localActiveKid = localKid,
                        revokeOutcome = RevokeOutcome(
                            kid = targetKid,
                            revoked = accepted,
                            revokedAtEpochSeconds = response.revokedAt.takeIf { accepted && it > 0L },
                            auditRef = null,
                            errorMessage = if (accepted) {
                                null
                            } else {
                                "The gateway did not confirm revocation of $targetKid."
                            },
                        ),
                        needsReenrollment = invalidatedReason != null || latest.needsReenrollment,
                        keyStatusReason = invalidatedReason ?: latest.keyStatusReason,
                        isRevoking = false,
                    ),
                )
            } catch (error: Throwable) {
                val latest = (_uiState.value as? UiState.Success)?.data ?: current
                _uiState.value = UiState.Success(
                    latest.copy(
                        revokeOutcome = RevokeOutcome(
                            kid = targetKid,
                            revoked = false,
                            revokedAtEpochSeconds = null,
                            auditRef = null,
                            errorMessage = mapFailureMessage(error),
                        ),
                        isRevoking = false,
                    ),
                )
            }
        }
    }

    private suspend fun refreshDevicesOrFallback(fallback: List<EnrolledDevice>): List<EnrolledDevice> =
        try {
            gatewayApi.listEnrolments().enrolments.map(::toUiDevice)
        } catch (_: Throwable) {
            fallback
        }

    private fun readLocalKeyState(): Pair<String, DeviceKeyStatus?> {
        val activeRecord = enrollmentStore.activeRecord() ?: return "" to null
        val tier = activeRecord.authTier?.let { AuthTier.fromWireValue(it) } ?: AuthTier.HARDWARE_CONFIRM
        val status = runCatching {
            deviceKeyManager.checkDeviceKey(
                DeviceKeySpec(
                    alias = activeRecord.keyAlias,
                    authTier = tier,
                ),
            )
        }.getOrNull()
        return activeRecord.kid to status
    }

    private fun toUiDevice(dto: EnrolledDeviceDto): EnrolledDevice =
        EnrolledDevice(
            kid = dto.kid,
            alias = dto.alias,
            authTier = dto.authTier,
            status = dto.status,
            enrolledAtEpochSeconds = dto.enrolledAt,
        )

    private fun mapFailureMessage(error: Throwable): String {
        val raw = when (error) {
            is GatewayNotConfiguredException -> errorMapper.mapNotConfigured(error.message).message
            is SSLPeerUnverifiedException -> errorMapper.mapTlsPinning(error.message).message
            is UnknownHostException -> errorMapper.mapUnreachable(
                error.message?.let { "the gateway host could not be resolved: $it" }
                    ?: "the gateway host could not be resolved",
            ).message
            is IOException -> errorMapper.mapUnreachable(
                error.message?.let { "the gateway could not be reached: $it" }
                    ?: "the gateway could not be reached",
            ).message
            is GatewayHttpException -> errorMapper.map(error.statusCode, error.body).message
            is GatewayProtocolException -> errorMapper.mapProtocolFailure(
                error.message ?: "the gateway's answer could not be read",
            ).message
            else -> error.message ?: "Unable to manage enrolled devices"
        }
        return Redactor.redact(raw)
    }

    private companion object {
        const val DEFAULT_REVOKE_REASON = "Revoked by operator from device management"
    }
}
