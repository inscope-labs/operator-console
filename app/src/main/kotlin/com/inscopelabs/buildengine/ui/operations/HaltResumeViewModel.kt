package com.inscopelabs.buildengine.ui.operations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inscopelabs.buildengine.capability.CapabilityMinter
import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.core.security.AuthTier
import com.inscopelabs.buildengine.core.security.ConfirmationTierPolicy
import com.inscopelabs.buildengine.data.enrollment.DeviceEnrollmentStore
import com.inscopelabs.buildengine.domain.model.OperationOutcome
import com.inscopelabs.buildengine.domain.repository.OperationRepository
import com.inscopelabs.buildengine.domain.repository.PlanRepository
import com.inscopelabs.buildengine.domain.repository.TaskRepository
import com.inscopelabs.buildengine.domain.usecase.common.MintResult
import com.inscopelabs.buildengine.domain.usecase.enrollment.ReportDevicePostureUseCase
import com.inscopelabs.buildengine.domain.usecase.write.HaltResult
import com.inscopelabs.buildengine.domain.usecase.write.HaltTarget
import com.inscopelabs.buildengine.domain.usecase.write.HaltUseCase
import com.inscopelabs.buildengine.domain.usecase.write.ResumeResult
import com.inscopelabs.buildengine.domain.usecase.write.ResumeTarget
import com.inscopelabs.buildengine.domain.usecase.write.ResumeUseCase
import com.inscopelabs.buildengine.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State holder for [HaltResumeScreen] (Stage 4.4 / T4-401).
 *
 * Does not load in [init]: network and journal reads happen only on explicit [load] / refresh.
 * Every halt or resume execution flows exclusively through [HaltUseCase] / [ResumeUseCase],
 * which bind capabilities strictly to the `engine` branch (I-07) and enforce recorded-cause
 * gating on resume (PR3-024).
 */
@HiltViewModel
class HaltResumeViewModel @Inject constructor(
    private val haltUseCase: HaltUseCase,
    private val resumeUseCase: ResumeUseCase,
    private val operationRepository: OperationRepository,
    private val planRepository: PlanRepository,
    private val taskRepository: TaskRepository,
    private val enrollmentStore: DeviceEnrollmentStore,
    private val reportDevicePostureUseCase: ReportDevicePostureUseCase,
    private val capabilityMinter: CapabilityMinter,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<HaltResumeData>>(UiState.Idle)
    val uiState: StateFlow<UiState<HaltResumeData>> = _uiState.asStateFlow()

    private var activePlanId: String = ""
    private var activeOrder: String = ""

    fun load(planId: String? = null, order: String? = null) {
        val previousOutcome = (_uiState.value as? UiState.Success)?.data?.lastOutcome
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            val resolvedPlanId = if (!planId.isNullOrBlank()) {
                planId.trim()
            } else {
                when (val plansResult = planRepository.getPlans()) {
                    is AppResult.Failure -> {
                        _uiState.value = UiState.Error(plansResult.message)
                        return@launch
                    }
                    is AppResult.Success -> {
                        val plans = plansResult.value
                        if (plans.isEmpty()) {
                            _uiState.value = UiState.Error("No plans available on the gateway")
                            return@launch
                        }
                        val preferred = plans.firstOrNull { !it.status.equals("archived", ignoreCase = true) }
                            ?: plans.first()
                        preferred.id
                    }
                }
            }

            val status = when (val statusResult = planRepository.getStatus(resolvedPlanId)) {
                is AppResult.Failure -> {
                    _uiState.value = UiState.Error(statusResult.message)
                    return@launch
                }
                is AppResult.Success -> statusResult.value
            }

            val engineHeadSha = when (val headResult = taskRepository.liveHead(ENGINE_BRANCH)) {
                is AppResult.Failure -> {
                    _uiState.value = UiState.Error(headResult.message)
                    return@launch
                }
                is AppResult.Success -> headResult.value
            }

            val resolvedOrder = if (!order.isNullOrBlank()) {
                order.trim()
            } else {
                val tasks = (taskRepository.getNextTasks() as? AppResult.Success)?.value.orEmpty()
                tasks.firstOrNull { it.planId == resolvedPlanId && it.order.isNotBlank() }?.order
                    ?: tasks.firstOrNull { it.order.isNotBlank() }?.order
                    ?: ""
            }

            activePlanId = resolvedPlanId
            activeOrder = resolvedOrder

            val recordedCause = operationRepository.recordedHaltCause().orEmpty()
            val activeRecord = enrollmentStore.activeRecord()
            val authTier = activeRecord?.authTier?.let { AuthTier.fromWireValue(it) }
            val posture = activeRecord?.let { record ->
                runCatching { reportDevicePostureUseCase(record.keyAlias) }.getOrNull()
            }

            _uiState.value = UiState.Success(
                HaltResumeData(
                    engine = EngineState(
                        isHalted = status.halted,
                        haltReason = recordedCause,
                        engineHeadSha = engineHeadSha,
                        branch = ENGINE_BRANCH,
                    ),
                    lastOutcome = previousOutcome,
                    authTier = authTier,
                    posture = posture,
                    capabilityTtlSeconds = capabilityMinter.ttlSeconds,
                ),
            )
        }
    }

    fun halt(reason: String, typedHeadConfirmation: String? = null) {
        val current = (_uiState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            val result = haltUseCase(
                target = HaltTarget(
                    plan = activePlanId,
                    order = activeOrder,
                    headSha = current.engine.engineHeadSha,
                    reason = reason,
                ),
                typedHeadConfirmation = typedHeadConfirmation,
            )
            val latest = (_uiState.value as? UiState.Success)?.data ?: current
            when (result) {
                is HaltResult.Executed -> {
                    val outcome = result.outcome
                    if (outcome is OperationOutcome.Succeeded) {
                        val recordedCause = operationRepository.recordedHaltCause()
                            ?.takeIf { it.isNotBlank() }
                            ?: reason.trim()
                        _uiState.value = UiState.Success(
                            latest.copy(
                                engine = latest.engine.copy(
                                    isHalted = result.authoritativeHalted ?: true,
                                    haltReason = recordedCause,
                                ),
                                lastOutcome = EngineOperationOutcome(
                                    operation = OP_HALT,
                                    accepted = true,
                                    auditRef = outcome.auditRef,
                                    headSha = outcome.headSha,
                                ),
                            ),
                        )
                    } else {
                        _uiState.value = UiState.Success(
                            latest.copy(lastOutcome = mapOutcome(OP_HALT, outcome)),
                        )
                    }
                }
                is HaltResult.NotAuthorized -> {
                    _uiState.value = UiState.Success(
                        latest.copy(lastOutcome = mapMintRefusal(OP_HALT, result.reason)),
                    )
                }
                is HaltResult.InvalidTarget -> {
                    _uiState.value = UiState.Success(
                        latest.copy(
                            lastOutcome = EngineOperationOutcome(
                                operation = OP_HALT,
                                accepted = false,
                                errorCode = CODE_INVALID_TARGET,
                                errorMessage = result.reason,
                            ),
                        ),
                    )
                }
            }
        }
    }

    fun resume(typedHeadConfirmation: String? = null) {
        val current = (_uiState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            val result = resumeUseCase(
                target = ResumeTarget(
                    plan = activePlanId,
                    order = activeOrder,
                    headSha = current.engine.engineHeadSha,
                ),
                typedHeadConfirmation = typedHeadConfirmation,
            )
            val latest = (_uiState.value as? UiState.Success)?.data ?: current
            when (result) {
                is ResumeResult.Executed -> {
                    val outcome = result.outcome
                    if (outcome is OperationOutcome.Succeeded) {
                        val remainingCause = operationRepository.recordedHaltCause().orEmpty()
                        _uiState.value = UiState.Success(
                            latest.copy(
                                engine = latest.engine.copy(
                                    isHalted = result.authoritativeHalted ?: false,
                                    haltReason = remainingCause,
                                ),
                                lastOutcome = EngineOperationOutcome(
                                    operation = OP_RESUME,
                                    accepted = true,
                                    auditRef = outcome.auditRef,
                                    headSha = outcome.headSha,
                                ),
                            ),
                        )
                    } else {
                        _uiState.value = UiState.Success(
                            latest.copy(lastOutcome = mapOutcome(OP_RESUME, outcome)),
                        )
                    }
                }
                is ResumeResult.MissingRecordedCause -> {
                    _uiState.value = UiState.Success(
                        latest.copy(
                            lastOutcome = EngineOperationOutcome(
                                operation = OP_RESUME,
                                accepted = false,
                                errorCode = CODE_MISSING_RECORDED_CAUSE,
                                errorMessage = result.reason,
                            ),
                        ),
                    )
                }
                is ResumeResult.NotAuthorized -> {
                    _uiState.value = UiState.Success(
                        latest.copy(lastOutcome = mapMintRefusal(OP_RESUME, result.reason)),
                    )
                }
                is ResumeResult.InvalidTarget -> {
                    _uiState.value = UiState.Success(
                        latest.copy(
                            lastOutcome = EngineOperationOutcome(
                                operation = OP_RESUME,
                                accepted = false,
                                errorCode = CODE_INVALID_TARGET,
                                errorMessage = result.reason,
                            ),
                        ),
                    )
                }
            }
        }
    }

    fun explainConfirmationTier(): String {
        val current = (_uiState.value as? UiState.Success)?.data
        val op = if (current?.engine?.isHalted == true) OperationType.RESUME else OperationType.HALT
        return ConfirmationTierPolicy.explainForSummary(op, current?.authTier)
    }

    private fun mapOutcome(operation: String, outcome: OperationOutcome): EngineOperationOutcome =
        when (outcome) {
            is OperationOutcome.Succeeded -> EngineOperationOutcome(
                operation = operation,
                accepted = true,
                auditRef = outcome.auditRef,
                headSha = outcome.headSha,
            )
            is OperationOutcome.Refused -> EngineOperationOutcome(
                operation = operation,
                accepted = false,
                auditRef = outcome.auditRef.orEmpty(),
                errorCode = outcome.code,
                errorMessage = "Refused by gateway (${outcome.code}).",
            )
            is OperationOutcome.Conflict -> EngineOperationOutcome(
                operation = operation,
                accepted = false,
                auditRef = outcome.auditRef.orEmpty(),
                errorCode = outcome.code,
                errorMessage = "Conflict reported by gateway (${outcome.code}).",
            )
            is OperationOutcome.Timeout -> EngineOperationOutcome(
                operation = operation,
                accepted = false,
                errorCode = "TIMEOUT",
                errorMessage = "No response from gateway; look up status via GET /operations/${outcome.idempotencyKey} rather than re-sending.",
            )
            is OperationOutcome.UncertainCompletion -> EngineOperationOutcome(
                operation = operation,
                accepted = false,
                auditRef = outcome.auditRef.orEmpty(),
                headSha = outcome.headSha.orEmpty(),
                errorCode = "UNCERTAIN",
                errorMessage = "Completion could not be verified against the live engine head.",
            )
            is OperationOutcome.RecoveryRequired -> EngineOperationOutcome(
                operation = operation,
                accepted = false,
                auditRef = outcome.auditRef.orEmpty(),
                errorCode = outcome.code ?: "RECOVERY_REQUIRED",
                errorMessage = "Operation requires operator recovery review; never auto-resent.",
            )
        }

    private fun mapMintRefusal(operation: String, reason: MintResult): EngineOperationOutcome =
        when (reason) {
            is MintResult.Minted -> error("Minted capability is not a refusal")
            is MintResult.HeadDrifted -> EngineOperationOutcome(
                operation = operation,
                accepted = false,
                headSha = reason.liveSha,
                errorCode = "HEAD_DRIFTED",
                errorMessage = "The engine head moved to ${reason.liveSha}. Reload before confirming.",
            )
            is MintResult.HeadUnavailable -> EngineOperationOutcome(
                operation = operation,
                accepted = false,
                errorCode = "HEAD_UNAVAILABLE",
                errorMessage = reason.error,
            )
            MintResult.Declined -> EngineOperationOutcome(
                operation = operation,
                accepted = false,
                errorCode = "DECLINED",
                errorMessage = "Operator confirmation was declined. Nothing was sent.",
            )
            is MintResult.AuthFailed -> EngineOperationOutcome(
                operation = operation,
                accepted = false,
                errorCode = "AUTH_FAILED",
                errorMessage = reason.error,
            )
            MintResult.LockedOut -> EngineOperationOutcome(
                operation = operation,
                accepted = false,
                errorCode = "LOCKED_OUT",
                errorMessage = "Biometric authentication is locked out. Nothing was sent.",
            )
            MintResult.KeyInvalidated -> EngineOperationOutcome(
                operation = operation,
                accepted = false,
                errorCode = "KEY_INVALIDATED",
                errorMessage = "Device key is invalidated; re-enrol before acting.",
            )
            MintResult.NotEnrolled -> EngineOperationOutcome(
                operation = operation,
                accepted = false,
                errorCode = "NOT_ENROLLED",
                errorMessage = "Device is not enrolled. Enrol a key first.",
            )
            MintResult.ExpiredWhileConfirming -> EngineOperationOutcome(
                operation = operation,
                accepted = false,
                errorCode = "TTL_EXPIRED",
                errorMessage = "Confirmation expired before it could be used.",
            )
            is MintResult.TierNotAllowed -> EngineOperationOutcome(
                operation = operation,
                accepted = false,
                errorCode = "TIER_NOT_ALLOWED",
                errorMessage = reason.reason,
            )
            is MintResult.EnvironmentMismatch -> EngineOperationOutcome(
                operation = operation,
                accepted = false,
                errorCode = "ENVIRONMENT_MISMATCH",
                errorMessage = "Environment mismatch: expected ${reason.expected}, gateway reported ${reason.reported}.",
            )
        }

    private companion object {
        const val OP_HALT = "HALT"
        const val OP_RESUME = "RESUME"
        const val CODE_INVALID_TARGET = "INVALID_TARGET"
        const val CODE_MISSING_RECORDED_CAUSE = "MISSING_RECORDED_CAUSE"
    }
}
