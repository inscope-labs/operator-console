package com.inscopelabs.buildengine.data.repository

import com.inscopelabs.buildengine.data.remote.GatewayRepository
import com.inscopelabs.buildengine.domain.model.OperationRequest
import com.inscopelabs.buildengine.domain.model.OperationResult
import com.inscopelabs.buildengine.domain.repository.OperationRepository

/**
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §12: every method here is privileged and runs the full guard set
 * G1-G13 on the gateway side.
 */
class OperationRepositoryImpl(
    private val gateway: GatewayRepository,
) : OperationRepository {

    override suspend fun publish(request: OperationRequest): OperationResult =
        TODO("SCAFFOLD: publish — the only path to the public repository.")

    override suspend fun upload(request: OperationRequest): OperationResult =
        TODO("SCAFFOLD: upload a submission (content-addressed, immutable).")

    override suspend fun reconcile(request: OperationRequest): OperationResult =
        TODO("SCAFFOLD: reconcile after the operator's review.")

    override suspend fun halt(request: OperationRequest): OperationResult =
        TODO("SCAFFOLD: halt — repository-wide stop, separately scoped.")

    override suspend fun resume(request: OperationRequest): OperationResult =
        TODO("SCAFFOLD: resume — clears the halt marker.")

    override suspend fun decide(request: OperationRequest): OperationResult =
        TODO("SCAFFOLD: decide — the highest-authority action in the system.")
}
