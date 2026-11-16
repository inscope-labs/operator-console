package com.inscopelabs.buildengine.manualpipeline.data.integration

import com.inscopelabs.buildengine.capability.model.CapabilityScope
import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.data.repository.OperationRepositoryImpl
import com.inscopelabs.buildengine.domain.model.OperationRequest
import com.inscopelabs.buildengine.domain.repository.OperationRepository
import com.inscopelabs.buildengine.manualpipeline.data.gateway.mapper.OperationMapper
import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationOutcome
import com.inscopelabs.buildengine.manualpipeline.domain.model.TransitionRequest
import com.inscopelabs.buildengine.manualpipeline.domain.port.ExecutorPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.MintedCapabilityHandle
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared executor adapter binding [ExecutorPort] to the host's encrypted-journaled
 * [OperationRepository] / [OperationRepositoryImpl] (I-03).
 */
@Singleton
class SharedExecutorAdapter @Inject constructor(
    private val operationRepository: OperationRepository,
    private val operationRepositoryImpl: OperationRepositoryImpl,
    private val mintingAdapter: ExistingMintingAdapter,
) : ExecutorPort {

    override suspend fun executeTransition(
        request: TransitionRequest,
        mintedCapability: MintedCapabilityHandle,
    ): OperationOutcome {
        val signed = mintingAdapter.consumeCapability(mintedCapability.handleId)
            ?: return OperationOutcome.Refused(
                code = "SCOPE_REFUSED",
                message = "Capability handle missing or already consumed.",
            )

        val hostRequest = OperationRequest(
            op = signed.capability.op,
            scope = CapabilityScope(
                op = signed.capability.op,
                branch = signed.capability.branch,
                head = signed.capability.head,
                nonce = signed.capability.nonce,
            ),
            plan = signed.capability.plan,
            task = signed.capability.task,
            order = signed.capability.order,
            typedHeadConfirmation = request.typedHeadConfirmation,
        )

        val hostOutcome = operationRepository.setTaskStatus(
            request = hostRequest,
            targetStatus = request.kind.wireTargetStatus,
            capability = signed,
        )
        return OperationMapper.toDomainOutcome(hostOutcome)
    }

    override suspend fun executeUpload(
        planId: String,
        taskId: String,
        orderId: String,
        branch: String,
        headSha: String,
        submissionId: String,
        zipSha256: String,
        zipBase64: String,
        mintedCapability: MintedCapabilityHandle,
    ): OperationOutcome {
        val signed = mintingAdapter.consumeCapability(mintedCapability.handleId)
            ?: return OperationOutcome.Refused(
                code = "SCOPE_REFUSED",
                message = "Capability handle missing or already consumed.",
            )

        val hostRequest = OperationRequest(
            op = OperationType.UPLOAD,
            scope = CapabilityScope(
                op = OperationType.UPLOAD,
                branch = signed.capability.branch,
                head = signed.capability.head,
                nonce = signed.capability.nonce,
            ),
            plan = signed.capability.plan,
            task = signed.capability.task,
            order = signed.capability.order,
        )

        val hostOutcome = operationRepository.upload(
            request = hostRequest,
            submissionId = submissionId,
            zipSha256 = zipSha256,
            zipBase64 = zipBase64,
            capability = signed,
        )
        return OperationMapper.toDomainOutcome(hostOutcome)
    }

    override suspend fun lookupOperation(idempotencyKey: String): OperationOutcome =
        OperationMapper.toDomainOutcome(operationRepositoryImpl.lookupOperation(idempotencyKey))

    override suspend fun recoverUncertainOperations(): Map<String, OperationOutcome> =
        operationRepositoryImpl.recoverUncertainOperations().outcomes.mapValues { (_, outcome) ->
            OperationMapper.toDomainOutcome(outcome)
        }
}
