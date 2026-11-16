package com.inscopelabs.buildengine.manualpipeline.domain.usecase

import com.inscopelabs.buildengine.manualpipeline.domain.model.OperatorActionRecord
import com.inscopelabs.buildengine.manualpipeline.domain.port.Clock
import com.inscopelabs.buildengine.manualpipeline.domain.port.OperatorActionStore
import com.inscopelabs.buildengine.manualpipeline.domain.port.RedactorPort
import java.util.UUID
import javax.inject.Inject

/**
 * MP-013: Records redacted, non-secret operator action history entries in the module database.
 */
class RecordOperatorActionUseCase @Inject constructor(
    private val store: OperatorActionStore,
    private val redactor: RedactorPort,
    private val clock: Clock,
) {

    suspend operator fun invoke(
        taskId: String,
        planId: String,
        actionName: String,
        outcomeLabel: String,
        detail: String,
        idempotencyKey: String? = null,
        auditRef: String? = null,
        headSha: String? = null,
    ) {
        val safeDetail = redactor.redact(detail)
        store.recordAction(
            OperatorActionRecord(
                id = UUID.randomUUID().toString(),
                taskId = taskId,
                planId = planId,
                actionName = actionName,
                outcomeLabel = outcomeLabel,
                idempotencyKey = idempotencyKey,
                auditRef = auditRef,
                headSha = headSha,
                detail = safeDetail,
                timestampEpochMs = clock.nowEpochMillis(),
            ),
        )
    }
}
