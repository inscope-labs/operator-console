package com.inscopelabs.buildengine.manualpipeline.domain.port

import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationOutcome

/**
 * Port for Gateway A archive upload (`POST /tasks/{id}/upload`).
 */
interface UploadPort {
    suspend fun submitUpload(
        planId: String,
        taskId: String,
        orderId: String,
        branch: String,
        headSha: String,
        submissionId: String,
        zipSha256: String,
        zipBase64: String,
        mintedCapability: MintedCapabilityHandle,
    ): OperationOutcome
}
