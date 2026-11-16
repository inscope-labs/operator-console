package com.inscopelabs.buildengine.manualpipeline.data.gateway

import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationOutcome
import com.inscopelabs.buildengine.manualpipeline.domain.port.ExecutorPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.MintedCapabilityHandle
import com.inscopelabs.buildengine.manualpipeline.domain.port.UploadPort
import javax.inject.Inject

/**
 * Gateway A upload adapter routing immutable submission archives through [ExecutorPort]
 * -> existing `OperationRepositoryImpl.executeOperation` (never bypasses the encrypted journal).
 */
class GatewayAUploadAdapter @Inject constructor(
    private val executorPort: ExecutorPort,
) : UploadPort {

    override suspend fun submitUpload(
        planId: String,
        taskId: String,
        orderId: String,
        branch: String,
        headSha: String,
        submissionId: String,
        zipSha256: String,
        zipBase64: String,
        mintedCapability: MintedCapabilityHandle,
    ): OperationOutcome = executorPort.executeUpload(
        planId = planId,
        taskId = taskId,
        orderId = orderId,
        branch = branch,
        headSha = headSha,
        submissionId = submissionId,
        zipSha256 = zipSha256,
        zipBase64 = zipBase64,
        mintedCapability = mintedCapability,
    )
}
