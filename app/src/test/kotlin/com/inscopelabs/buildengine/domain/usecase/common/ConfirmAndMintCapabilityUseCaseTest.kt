package com.inscopelabs.buildengine.domain.usecase.common

import com.inscopelabs.buildengine.capability.CapabilityMinter
import com.inscopelabs.buildengine.capability.DeviceNonceSource
import com.inscopelabs.buildengine.capability.model.CapabilityScope
import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.capability.signing.HmacCapabilitySigner
import com.inscopelabs.buildengine.core.crypto.DeviceKeyManager
import com.inscopelabs.buildengine.core.crypto.SecureRandomSource
import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.core.security.AuthTier
import com.inscopelabs.buildengine.core.security.AuthorizedSignatureResult
import com.inscopelabs.buildengine.core.security.BiometricGate
import com.inscopelabs.buildengine.core.time.Clock
import com.inscopelabs.buildengine.core.time.FixedClock
import com.inscopelabs.buildengine.data.enrollment.DeviceEnrollmentStore
import com.inscopelabs.buildengine.domain.model.OperationRequest
import com.inscopelabs.buildengine.domain.model.TaskBranch
import com.inscopelabs.buildengine.testutil.FakeSharedPreferences
import com.inscopelabs.buildengine.testutil.FakeTaskRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature

class ConfirmAndMintCapabilityUseCaseTest {

    private val fixedClock = FixedClock(1700000000L)
    private val signer = HmacCapabilitySigner(keyId = "test_kid", secretUtf8 = "test-secret-key-32-bytes-long!!")
    private val nonceSource = DeviceNonceSource(SecureRandomSource())
    private val minter = CapabilityMinter(signer, nonceSource, fixedClock)

    private val validHead = "a".repeat(40)
    private val validBranch = "task-demo"
    private val validNonce = "0123456789abcdef0123456789abcdef"
    private val validScope = CapabilityScope(
        op = OperationType.PUBLISH,
        branch = validBranch,
        head = validHead,
        nonce = validNonce,
    )
    private val validRequest = OperationRequest(
        op = OperationType.PUBLISH,
        scope = validScope,
        plan = "p1",
        task = "t1",
        order = "ord-1",
    )

    @Test
    fun `successful confirmation verifies head, prompts biometric, and returns Minted`() = runTest {
        val taskRepository = FakeTaskRepository().apply {
            branchResult = { taskId ->
                AppResult.Success(TaskBranch(name = validBranch, headSha = validHead))
            }
        }
        val verifyBranchHead = VerifyBranchHeadUseCase(taskRepository)

        var biometricPrompted = false
        val biometricGate = BiometricGate(
            authenticator = { _, _ ->
                biometricPrompted = true
                true
            }
        )

        var summaryShown: OperationRequest? = null
        val useCase = ConfirmAndMintCapabilityUseCase(
            capabilityMinter = minter,
            biometricGate = biometricGate,
            verifyBranchHeadUseCase = verifyBranchHead,
            onShowSummary = { summaryShown = it },
            clock = fixedClock,
        )

        val result = useCase(validRequest)
        assertTrue("Expected Minted, got $result", result is MintResult.Minted)
        val signed = (result as MintResult.Minted).capability

        assertTrue("Biometric authentication must be invoked", biometricPrompted)
        assertEquals(validRequest, summaryShown)
        assertEquals("test_kid", signed.capability.kid)
        assertEquals(OperationType.PUBLISH, signed.capability.op)
        assertEquals(validBranch, signed.capability.branch)
        assertEquals(validHead, signed.capability.head)
        assertEquals("p1", signed.capability.plan)
        assertEquals("t1", signed.capability.task)
        assertEquals("ord-1", signed.capability.order)
        assertEquals(1700000000L + 300L, signed.capability.exp)
        assertTrue(signed.signature.isNotEmpty())
    }

    @Test
    fun `returns HeadDrifted when remote head drifted`() = runTest {
        val driftedHead = "b".repeat(40)
        val taskRepository = FakeTaskRepository().apply {
            branchResult = { taskId ->
                AppResult.Success(TaskBranch(name = validBranch, headSha = driftedHead))
            }
        }
        val verifyBranchHead = VerifyBranchHeadUseCase(taskRepository)

        var biometricPrompted = false
        val biometricGate = BiometricGate(
            authenticator = { _, _ ->
                biometricPrompted = true
                true
            }
        )

        val useCase = ConfirmAndMintCapabilityUseCase(
            capabilityMinter = minter,
            biometricGate = biometricGate,
            verifyBranchHeadUseCase = verifyBranchHead,
        )

        val result = useCase(validRequest)
        assertTrue("Expected HeadDrifted, got $result", result is MintResult.HeadDrifted)
        assertEquals(driftedHead, (result as MintResult.HeadDrifted).liveSha)
        assertFalse("Biometric prompt must NOT be shown if head has drifted", biometricPrompted)
    }

    @Test
    fun `returns HeadUnavailable when branch lookup fails`() = runTest {
        val taskRepository = FakeTaskRepository().apply {
            branchResult = { _ ->
                AppResult.Failure("not_found", "network timeout")
            }
        }
        val verifyBranchHead = VerifyBranchHeadUseCase(taskRepository)
        val biometricGate = BiometricGate(authenticator = { _, _ -> true })

        val useCase = ConfirmAndMintCapabilityUseCase(
            capabilityMinter = minter,
            biometricGate = biometricGate,
            verifyBranchHeadUseCase = verifyBranchHead,
        )

        val result = useCase(validRequest)
        assertTrue("Expected HeadUnavailable, got $result", result is MintResult.HeadUnavailable)
    }

    @Test
    fun `returns Declined when operator cancels biometrics`() = runTest {
        val taskRepository = FakeTaskRepository().apply {
            branchResult = { taskId ->
                AppResult.Success(TaskBranch(name = validBranch, headSha = validHead))
            }
        }
        val verifyBranchHead = VerifyBranchHeadUseCase(taskRepository)
        val biometricGate = BiometricGate(authenticator = { _, _ -> false })

        val useCase = ConfirmAndMintCapabilityUseCase(
            capabilityMinter = minter,
            biometricGate = biometricGate,
            verifyBranchHeadUseCase = verifyBranchHead,
        )

        val result = useCase(validRequest)
        assertEquals(MintResult.Declined, result)
    }

    @Test
    fun `TTL starts after approval, not before prompt`() = runTest {
        val taskRepository = FakeTaskRepository().apply {
            branchResult = { taskId ->
                AppResult.Success(TaskBranch(name = validBranch, headSha = validHead))
            }
        }
        val verifyBranchHead = VerifyBranchHeadUseCase(taskRepository)

        var currentEpoch = 1700000000L
        val mutableClock = object : Clock {
            override fun nowEpochSeconds(): Long = currentEpoch
        }

        // Simulate operator taking 45 seconds to approve the biometric prompt
        val biometricGate = BiometricGate(
            authenticator = { _, _ ->
                currentEpoch += 45L
                true
            }
        )

        val useCase = ConfirmAndMintCapabilityUseCase(
            capabilityMinter = CapabilityMinter(signer, nonceSource, mutableClock),
            biometricGate = biometricGate,
            verifyBranchHeadUseCase = verifyBranchHead,
            clock = mutableClock,
        )

        val result = useCase(validRequest)
        assertTrue(result is MintResult.Minted)
        val signed = (result as MintResult.Minted).capability
        // Exp must be approvedAt (1700000045) + 300 = 1700000345
        assertEquals(1700000045L + 300L, signed.capability.exp)
    }

    @Test
    fun `in_app_confirm succeeds when typed head matches first 7 hex characters`() = runTest {
        val taskRepository = FakeTaskRepository().apply {
            branchResult = { taskId ->
                AppResult.Success(TaskBranch(name = validBranch, headSha = validHead))
            }
        }
        val verifyBranchHead = VerifyBranchHeadUseCase(taskRepository)

        val prefs = FakeSharedPreferences()
        val store = DeviceEnrollmentStore(preferences = prefs)
        store.recordPending("k_inapp", "alias1", "dummyPublicKey", 1700000000L, AuthTier.IN_APP_CONFIRM.wireValue)
        store.activate("k_inapp", 1700000000L)

        val biometricGate = BiometricGate(authenticator = { _, _ ->
            fail("Biometric prompt should NOT be called for in_app_confirm")
            false
        })

        val useCase = ConfirmAndMintCapabilityUseCase(
            capabilityMinter = minter,
            biometricGate = biometricGate,
            verifyBranchHeadUseCase = verifyBranchHead,
            enrollmentStore = store,
            clock = fixedClock,
        )

        // validHead is "a".repeat(40), first 7 chars are "aaaaaaa"
        val requestWithCorrectHead = validRequest.copy(typedHeadConfirmation = "aaaaaaa")
        val result = useCase(requestWithCorrectHead)

        assertTrue("Expected Minted for correct typed head, got $result", result is MintResult.Minted)
    }

    @Test
    fun `in_app_confirm returns Declined when typed head does not match`() = runTest {
        val taskRepository = FakeTaskRepository().apply {
            branchResult = { taskId ->
                AppResult.Success(TaskBranch(name = validBranch, headSha = validHead))
            }
        }
        val verifyBranchHead = VerifyBranchHeadUseCase(taskRepository)

        val prefs = FakeSharedPreferences()
        val store = DeviceEnrollmentStore(preferences = prefs)
        store.recordPending("k_inapp", "alias1", "dummyPublicKey", 1700000000L, AuthTier.IN_APP_CONFIRM.wireValue)
        store.activate("k_inapp", 1700000000L)

        val biometricGate = BiometricGate()
        val useCase = ConfirmAndMintCapabilityUseCase(
            capabilityMinter = minter,
            biometricGate = biometricGate,
            verifyBranchHeadUseCase = verifyBranchHead,
            enrollmentStore = store,
        )

        val requestWithWrongHead = validRequest.copy(typedHeadConfirmation = "bbbbbbb")
        val result = useCase(requestWithWrongHead)

        assertEquals(MintResult.Declined, result)
    }

    @Test
    fun `returns NotEnrolled when enrollment store has no active record`() = runTest {
        val taskRepository = FakeTaskRepository().apply {
            branchResult = { taskId ->
                AppResult.Success(TaskBranch(name = validBranch, headSha = validHead))
            }
        }
        val verifyBranchHead = VerifyBranchHeadUseCase(taskRepository)
        val prefs = FakeSharedPreferences()
        val emptyStore = DeviceEnrollmentStore(preferences = prefs)

        val useCase = ConfirmAndMintCapabilityUseCase(
            capabilityMinter = minter,
            biometricGate = BiometricGate(),
            verifyBranchHeadUseCase = verifyBranchHead,
            enrollmentStore = emptyStore,
        )

        val result = useCase(validRequest)
        assertEquals(MintResult.NotEnrolled, result)
    }

    @Test
    fun `returns LockedOut when biometric gate returns Lockout`() = runTest {
        val taskRepository = FakeTaskRepository().apply {
            branchResult = { taskId ->
                AppResult.Success(TaskBranch(name = validBranch, headSha = validHead))
            }
        }
        val verifyBranchHead = VerifyBranchHeadUseCase(taskRepository)

        val keyPair = KeyPairGenerator.getInstance("EC").generateKeyPair()
        val fakeKeyManager = object : DeviceKeyManager() {
            override fun getPrivateKey(alias: String) = keyPair.private
        }

        val prefs = FakeSharedPreferences()
        val store = DeviceEnrollmentStore(preferences = prefs)
        store.recordPending("k1", "alias1", "dummyKey", 1700000000L, AuthTier.HARDWARE_CONFIRM.wireValue)
        store.activate("k1", 1700000000L)

        val biometricGate = object : BiometricGate() {
            override suspend fun authorize(
                signature: Signature,
                info: com.inscopelabs.buildengine.core.security.PromptInfoSpec,
                policy: com.inscopelabs.buildengine.core.security.BiometricPolicy,
            ) = AuthorizedSignatureResult.Lockout
        }

        val useCase = ConfirmAndMintCapabilityUseCase(
            capabilityMinter = minter,
            biometricGate = biometricGate,
            verifyBranchHeadUseCase = verifyBranchHead,
            deviceKeyManager = fakeKeyManager,
            enrollmentStore = store,
        )

        val result = useCase(validRequest)
        assertEquals(MintResult.LockedOut, result)
    }

    @Test
    fun `returns KeyInvalidated when private key is null or permanently invalidated`() = runTest {
        val taskRepository = FakeTaskRepository().apply {
            branchResult = { taskId ->
                AppResult.Success(TaskBranch(name = validBranch, headSha = validHead))
            }
        }
        val verifyBranchHead = VerifyBranchHeadUseCase(taskRepository)

        val fakeKeyManager = object : DeviceKeyManager() {
            override fun getPrivateKey(alias: String) = null
        }

        val prefs = FakeSharedPreferences()
        val store = DeviceEnrollmentStore(preferences = prefs)
        store.recordPending("k1", "alias1", "dummyKey", 1700000000L, AuthTier.HARDWARE_CONFIRM.wireValue)
        store.activate("k1", 1700000000L)

        val useCase = ConfirmAndMintCapabilityUseCase(
            capabilityMinter = minter,
            biometricGate = BiometricGate(),
            verifyBranchHeadUseCase = verifyBranchHead,
            deviceKeyManager = fakeKeyManager,
            enrollmentStore = store,
        )

        val result = useCase(validRequest)
        assertEquals(MintResult.KeyInvalidated, result)
    }

    @Test
    fun `refuses with IllegalArgumentException when request op does not match scope op`() = runTest {
        val taskRepository = FakeTaskRepository().apply {
            branchResult = { taskId ->
                AppResult.Success(TaskBranch(name = validBranch, headSha = validHead))
            }
        }
        val verifyBranchHead = VerifyBranchHeadUseCase(taskRepository)
        val biometricGate = BiometricGate(authenticator = { _, _ -> true })

        val mismatchedRequest = validRequest.copy(op = OperationType.RECONCILE)

        val useCase = ConfirmAndMintCapabilityUseCase(
            capabilityMinter = minter,
            biometricGate = biometricGate,
            verifyBranchHeadUseCase = verifyBranchHead,
        )

        try {
            useCase(mismatchedRequest)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("does not match") == true)
        }
    }
}
