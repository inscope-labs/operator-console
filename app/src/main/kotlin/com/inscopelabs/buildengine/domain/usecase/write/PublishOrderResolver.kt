package com.inscopelabs.buildengine.domain.usecase.write

import com.inscopelabs.buildengine.domain.model.Task

/**
 * Supplies the work-order identifier that a publish capability must carry.
 *
 * Contract revision 1.2 (ADR 0014 D1, ADR 0015) requires a non-empty `order` on
 * every `GET /tasks/next` item. The console copies that value unchanged into the
 * capability `order` field and never invents one.
 */
fun interface PublishOrderResolver {
    suspend fun resolve(task: Task): String?
}

/**
 * Resolves the order from the task model populated by the gateway task read.
 * Returns null when the field is blank so publish continues to fail closed rather
 * than minting with an empty order.
 */
class TaskFieldPublishOrderResolver : PublishOrderResolver {
    override suspend fun resolve(task: Task): String? =
        task.order.takeIf { it.isNotBlank() }
}

/**
 * Fail-closed fallback retained for tests that intentionally withhold an order.
 */
class UnresolvedPublishOrderResolver : PublishOrderResolver {
    override suspend fun resolve(task: Task): String? = null
}
