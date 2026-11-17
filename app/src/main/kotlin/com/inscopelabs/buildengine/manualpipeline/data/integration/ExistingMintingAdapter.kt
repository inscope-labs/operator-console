package com.inscopelabs.buildengine.manualpipeline.data.integration

import com.inscopelabs.buildengine.capability.CapabilityMinter
import com.inscopelabs.buildengine.capability.NonceSource
import com.inscopelabs.buildengine.capability.model.CapabilityScope
import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.capability.signing.SignedCapability
import com.inscopelabs.buildengine.core.security.AuthTier
import com.inscopelabs.buildengine.data.enrollment.DeviceEnrollmentStore
import com.inscopelabs.buildengine.data.local.GatewayBaseUrlStore
import com.inscopelabs.buildengine.domain.model.OperationRequest
import com.inscopelabs.buildengine.domain.usecase.common.ConfirmAndMintCapabilityUseCase
import com.inscopelabs.buildengine.domain.usecase.common.MintResult
import com.inscopelabs.buildengine.manualpipeline.domain.model.ConfirmationTier
import com.inscopelabs.buildengine.manualpipeline.domain.model.TransitionKind
import com.inscopelabs.buildengine.manualpipeline.domain.model.TransitionRequest
import com.inscopelabs.buildengine.manualpipeline.domain.port.MintOutcome
import com.inscopelabs.buildengine.manualpipeline.domain.port.MintedCapabilityHandle
import com.inscopelabs.buildengine.manualpipeline.domain.port.MintingPort
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Delegates all capability minting strictly through the host's [ConfirmAndMintCapabilityUseCase]
 * (I-01, ADR 0012). Keeps signed capability bytes strictly ephemeral in memory and hands out only
 * an opaque single-use handle ID to the domain layer.
 */
@Singleton
class ExistingMintingAdapter @Inject constructor(
    private val confirmAndMintCapabilityUseCase: ConfirmAndMintCapabilityUseCase,
    private val nonceSource: NonceSource,
    private val enrollmentStore: DeviceEnrollmentStore,
    private val baseUrlStore: GatewayBaseUrlStore,
    private val capabilityMinter: CapabilityMinter,
) : MintingPort {

    private val ephemeralCapabilities = ConcurrentHashMap<String, SignedCapability>()

    override suspend fun mintForTransition(request: TransitionRequest): MintOutcome {
        val opType = when (request.kind) {
            TransitionKind.SET_IN_PROGRESS -> OperationType.SET_IN_PROGRESS
            TransitionKind.SET_DONE -> OperationType.SET_DONE
        }
        val hostRequest = OperationRequest(
            op = opType,
            scope = CapabilityScope(
                op = opType,
                branch = request.branch,
                head = request.expectedHead.trim().lowercase(),
                nonce = nonceSource.newNonce(),
            ),
            plan = request.planId,
            task = request.taskId,
            order = request.orderId,
            typedHeadConfirmation = request.typedHeadConfirmation,
        )
        return executeMint(
            hostRequest = hostRequest,
            operationLabel = opType.name,
        )
    }

    override suspend fun mintForUpload(
        planId: String,
        taskId: String,
        orderId: String,
        branch: String,
        headSha: String,
        submissionId: String,
        zipSha256: String,
        typedHeadConfirmation: String?,
    ): MintOutcome {
        val hostRequest = OperationRequest(
            op = OperationType.UPLOAD,
            scope = CapabilityScope(
                op = OperationType.UPLOAD,
                branch = branch,
                head = headSha.trim().lowercase(),
                nonce = nonceSource.newNonce(),
            ),
            plan = planId,
            task = taskId,
            order = orderId,
            typedHeadConfirmation = typedHeadConfirmation,
        )
        return executeMint(
            hostRequest = hostRequest,
            operationLabel = OperationType.UPLOAD.name,
        )
    }

    internal fun consumeCapability(handleId: String): SignedCapability? =
        ephemeralCapabilities.remove(handleId)

    private suspend fun executeMint(
        hostRequest: OperationRequest,
        operationLabel: String,
    ): MintOutcome {
        return when (val result = confirmAndMintCapabilityUseCase(hostRequest)) {
            is MintResult.Minted -> {
                val handleId = UUID.randomUUID().toString()
                ephemeralCapabilities[handleId] = result.capability
                val env = baseUrlStore.expectedEnvironment()
                val activeTierWire = enrollmentStore.activeRecord(env)?.authTier
                val tier = when (activeTierWire?.let { AuthTier.fromWireValue(it) }) {
                    AuthTier.IN_APP_CONFIRM -> ConfirmationTier.IN_APP_CONFIRM
                    else -> ConfirmationTier.HARDWARE_CONFIRM
                }
                MintOutcome.Minted(
                    MintedCapabilityHandle(
                        handleId = handleId,
                        operation = operationLabel,
                        planId = hostRequest.plan,
                        taskId = hostRequest.task,
                        orderId = hostRequest.order,
                        branch = hostRequest.scope.branch,
                        headSha = hostRequest.scope.head,
                        ttlSeconds = capabilityMinter.ttlSeconds,
                        tier = tier,
                    ),
                )
            }
            is MintResult.HeadDrifted -> MintOutcome.HeadDrifted(result.liveSha)
            is MintResult.HeadUnavailable -> MintOutcome.HeadUnavailable(result.error)
            is MintResult.Declined -> MintOutcome.Declined
            is MintResult.AuthFailed -> MintOutcome.AuthFailed(result.error)
            is MintResult.LockedOut -> MintOutcome.LockedOut
            is MintResult.KeyInvalidated -> MintOutcome.KeyInvalidated
            is MintResult.NotEnrolled -> MintOutcome.NotEnrolled
            is MintResult.ExpiredWhileConfirming -> MintOutcome.ExpiredWhileConfirming
            is MintResult.TierNotAllowed -> MintOutcome.TierNotAllowed(result.reason)
            is MintResult.EnvironmentMismatch -> MintOutcome.EnvironmentMismatch(result.expected, result.reported)
        }
    }
}
