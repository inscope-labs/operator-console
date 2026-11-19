package com.inscopelabs.buildengine.ui.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inscopelabs.buildengine.core.network.ErrorMapper
import com.inscopelabs.buildengine.core.network.GatewayHttpException
import com.inscopelabs.buildengine.core.network.GatewayNotConfiguredException
import com.inscopelabs.buildengine.core.network.GatewayProtocolException
import com.inscopelabs.buildengine.core.network.dto.AuditEntryDto
import com.inscopelabs.buildengine.data.remote.GatewayApi
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
 * State holder for [AuditInspectionScreen] (Stage 4.4 / T4-401, PoC Tasks 21–22).
 *
 * Reads `GET /audit` and `GET /audit/{ref}` directly from [GatewayApi] on explicit user
 * demand (no network calls in [init]). Every rendered string passes through [Redactor.redact],
 * and resolving an `auditRef` always queries the gateway rather than searching the local list.
 */
@HiltViewModel
class AuditInspectionViewModel @Inject constructor(
    private val gatewayApi: GatewayApi,
    private val errorMapper: ErrorMapper = ErrorMapper(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<AuditInspectionData>>(UiState.Idle)
    val uiState: StateFlow<UiState<AuditInspectionData>> = _uiState.asStateFlow()

    fun load() {
        val currentSuccess = (_uiState.value as? UiState.Success)?.data
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val entries = gatewayApi.listAudit().map(::toRedactedRecord)
                _uiState.value = UiState.Success(
                    AuditInspectionData(
                        records = entries,
                        resolved = currentSuccess?.resolved,
                        resolveError = null,
                        isResolving = false,
                    ),
                )
            } catch (error: Throwable) {
                _uiState.value = UiState.Error(mapFailureMessage(error))
            }
        }
    }

    fun resolve(auditRef: String) {
        val trimmed = auditRef.trim()
        if (trimmed.isEmpty()) return

        val baseData = (_uiState.value as? UiState.Success)?.data ?: AuditInspectionData()
        _uiState.value = UiState.Success(
            baseData.copy(
                isResolving = true,
                resolveError = null,
            ),
        )

        viewModelScope.launch {
            try {
                val resolved = toRedactedRecord(gatewayApi.resolveAudit(trimmed))
                val latest = (_uiState.value as? UiState.Success)?.data ?: baseData
                _uiState.value = UiState.Success(
                    latest.copy(
                        resolved = resolved,
                        resolveError = null,
                        isResolving = false,
                    ),
                )
            } catch (error: GatewayHttpException) {
                val latest = (_uiState.value as? UiState.Success)?.data ?: baseData
                val mapped = Redactor.redact(errorMapper.map(error.statusCode, error.body).message)
                val message = if (error.statusCode == 404) {
                    "Audit reference '${Redactor.redact(trimmed)}' was not found on the gateway ($mapped)."
                } else {
                    mapped
                }
                _uiState.value = UiState.Success(
                    latest.copy(
                        resolved = null,
                        resolveError = message,
                        isResolving = false,
                    ),
                )
            } catch (error: Throwable) {
                val latest = (_uiState.value as? UiState.Success)?.data ?: baseData
                _uiState.value = UiState.Success(
                    latest.copy(
                        resolved = null,
                        resolveError = mapFailureMessage(error),
                        isResolving = false,
                    ),
                )
            }
        }
    }

    private fun toRedactedRecord(dto: AuditEntryDto): AuditRecord =
        AuditRecord(
            auditRef = Redactor.redact(dto.auditRef.trim()),
            timestampIso = Redactor.redact(dto.timestamp.trim()),
            operation = Redactor.redact(dto.operation.trim()),
            plan = Redactor.redact(dto.plan.orEmpty().trim()),
            task = Redactor.redact(dto.task.orEmpty().trim()),
            branch = Redactor.redact(dto.branch.orEmpty().trim()),
            headSha = Redactor.redact(dto.headSha.orEmpty().trim()),
            kid = Redactor.redact(dto.kid.orEmpty().trim()),
            authTier = Redactor.redact(dto.authTier.orEmpty().trim()),
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
            else -> error.message ?: "Unable to read the audit journal"
        }
        return Redactor.redact(raw)
    }
}
