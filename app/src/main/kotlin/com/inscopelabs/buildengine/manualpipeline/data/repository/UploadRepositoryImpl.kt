package com.inscopelabs.buildengine.manualpipeline.data.repository

import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationOutcome
import com.inscopelabs.buildengine.manualpipeline.domain.port.MintedCapabilityHandle
import com.inscopelabs.buildengine.manualpipeline.domain.port.UploadPort
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates immutable submission archive uploads through [UploadPort] -> host journaled executor.
 */
@Singleton
class UploadRepositoryImpl @Inject constructor(
    private val uploadPort: UploadPort,
) {
    suspend fun submit(
        planId: String,
        taskId: String,
        orderId: String,
        branch: String,
        headSha: String,
        submissionId: String,
        zipSha256: String,
        zipBase64: String,
        mintedCapability: MintedCapabilityHandle,
    ): OperationOutcome = uploadPort.submitUpload(
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
