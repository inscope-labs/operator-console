package com.inscopelabs.buildengine.domain.usecase.write

import com.inscopelabs.buildengine.domain.model.Task

/**
 * Supplies the work-order identifier that a publish capability must carry.
 *
 * The capability format requires a non-blank `order`, but the gateway's read endpoints
 * (`GET /tasks/next`, contract §3.6) and the [Task] model carry no order identifier, and
 * the contract defines no way to obtain one (see `docs/release/PHASE-3-REMEDIATION-REGISTER.md`,
 * PR3-003 and PR3-027). The console therefore does not invent one: the default
 * implementation returns `null`, which makes publish fail closed until the contract
 * says where the order comes from.
 */
fun interface PublishOrderResolver {
    suspend fun resolve(task: Task): String?
}

/** Fail-closed default: no order identifier is available. */
class UnresolvedPublishOrderResolver : PublishOrderResolver {
    override suspend fun resolve(task: Task): String? = null
}
