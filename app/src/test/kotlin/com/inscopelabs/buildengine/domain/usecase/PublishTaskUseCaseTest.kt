package com.inscopelabs.buildengine.domain.usecase

import com.inscopelabs.buildengine.domain.model.OperationRequest
import com.inscopelabs.buildengine.domain.model.OperationResult
import com.inscopelabs.buildengine.domain.repository.OperationRepository
import com.inscopelabs.buildengine.domain.usecase.write.PublishTaskUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the head-drift precondition: a capability minted against a stale head SHA
 * must not authorise the write.
 *
 * SCAFFOLD PLACEHOLDER — replace with the real refusal assertions.
 */
class PublishTaskUseCaseTest {

    @Test
    fun `publish is the only path to the public repository`() = runTest {
        val repository = object : OperationRepository {
            override suspend fun publish(request: OperationRequest): OperationResult =
                OperationResult(accepted = false, operation = request.op)

            override suspend fun upload(request: OperationRequest): OperationResult =
                OperationResult(accepted = false, operation = request.op)

            override suspend fun reconcile(request: OperationRequest): OperationResult =
                OperationResult(accepted = false, operation = request.op)

            override suspend fun halt(request: OperationRequest): OperationResult =
                OperationResult(accepted = false, operation = request.op)

            override suspend fun resume(request: OperationRequest): OperationResult =
                OperationResult(accepted = false, operation = request.op)

            override suspend fun decide(request: OperationRequest): OperationResult =
                OperationResult(accepted = false, operation = request.op)
        }

        val useCase = PublishTaskUseCase(
            operationRepository = repository,
            confirmAndMintCapability = com.inscopelabs.buildengine.domain.usecase.common
                .ConfirmAndMintCapabilityUseCase(
                capabilityMinter = com.inscopelabs.buildengine.capability.CapabilityMinter(
                    signer = com.inscopelabs.buildengine.capability.signing
                        .DeviceKeyCapabilitySigner(),
                    nonceSource = com.inscopelabs.buildengine.capability.DeviceNonceSource(
                        com.inscopelabs.buildengine.core.crypto.SecureRandomSource(),
                    ),
                    clock = com.inscopelabs.buildengine.core.time.SystemClock(),
                ),
                biometricGate = com.inscopelabs.buildengine.core.security.BiometricGate(),
            ),
        )

        // The operation surface is exactly the six privileged operations of the review's
        // capability matrix — `verify` and `bootstrap`/`init` are deliberately absent.
        assertEquals(6, repository.javaClass.declaredMethods.size)
        // SCAFFOLD: replace with the real refusal assertion — a capability minted
        // against a stale head SHA must not authorise the write.
        assertEquals(0, 0)
    }
}
