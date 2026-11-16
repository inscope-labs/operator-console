package com.inscopelabs.buildengine.manualpipeline.data.gateway.mapper

import com.inscopelabs.buildengine.capability.model.CapabilityScope
import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.domain.model.OperationRequest

object UploadMapper {

    fun toHostOperationRequest(
        planId: String,
        taskId: String,
        orderId: String,
        branch: String,
        headSha: String,
        nonce: String,
        typedHeadConfirmation: String? = null,
    ): OperationRequest = OperationRequest(
        op = OperationType.UPLOAD,
        scope = CapabilityScope(
            op = OperationType.UPLOAD,
            branch = branch,
            head = headSha.trim().lowercase(),
            nonce = nonce,
        ),
        plan = planId,
        task = taskId,
        order = orderId,
        typedHeadConfirmation = typedHeadConfirmation,
    )
}
