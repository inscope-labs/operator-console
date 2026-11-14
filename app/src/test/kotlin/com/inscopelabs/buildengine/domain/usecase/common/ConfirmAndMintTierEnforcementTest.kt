package com.inscopelabs.buildengine.domain.usecase.common

import com.inscopelabs.buildengine.capability.CapabilityMinter
import com.inscopelabs.buildengine.capability.DeviceNonceSource
import com.inscopelabs.buildengine.capability.model.CapabilityScope
import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.capability.signing.HmacCapabilitySigner
import com.inscopelabs.buildengine.core.crypto.SecureRandomSource
import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.core.security.AuthTier
import com.inscopelabs.buildengine.core.security.BiometricGate
import com.inscopelabs.buildengine.core.time.FixedClock
import com.inscopelabs.buildengine.data.enrollment.DeviceEnrollmentStore
import com.inscopelabs.buildengine.domain.model.OperationRequest
import com.inscopelabs.buildengine.domain.model.TaskBranch
import com.inscopelabs.buildengine.testutil.FakeSharedPreferences
import com.inscopelabs.buildengine.testutil.FakeTaskRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Step 3b of the single mint path: the per-operation confirmation tier is enforced
 * before any confirmation gesture is shown (Stage 3.5, T3-501/T3-502; BD-3.1(a)).
 */
class ConfirmAndMintTierEnforcementTest {

    private val fixedClock = FixedClock(1700000000L)
    private val signer = HmacCapabilitySigner(keyId = "test_kid", secretUtf8 = "test-secret-key-32-bytes-long!!")
    private val minter = CapabilityMinter(signer, DeviceNonceSource(SecureRandomSource()), fixedClock)

    private val validHead = "a".repeat(40)

    private fun request(op: OperationType) = OperationRequest(
        op = op,
        scope = CapabilityScope(op = op, branch = "task-demo", head = validHead, nonce = "0123456789abcdef0123456789abcdef"),
        plan = "p1",
        task = "t1",
        order = "ord-1",
    )

    private fun enrolledStore(tier: AuthTier): DeviceEnrollmentStore {
        val store = DeviceEnrollmentStore(preferences = FakeSharedPreferences())
        store.recordPending("k1", "alias1", "pub", 1700000000L, tier.wireValue)
        store.activate("k1", 1700000000L)
        return store
    }

    private fun verify() = VerifyBranchHeadUseCase(
        FakeTaskRepository().apply {
            branchResult = { AppResult.Success(TaskBranch(name = "task-demo", headSha = validHead)) }
        },
    )

    @Test
    fun `decide with an in_app key is refused when the device can provide hardware confirmation`() = runTest {
        val useCase = ConfirmAndMintCapabilityUseCase(
            capabilityMinter = minter,
            biometricGate = BiometricGate(authenticator = { _, _ -> true }),
            verifyBranchHeadUseCase = verify(),
            enrollmentStore = enrolledStore(AuthTier.IN_APP_CONFIRM),
            clock = fixedClock,
            hardwareConfirmAvailable = { true },
        )
        val result = useCase(request(OperationType.DECIDE))
        assertTrue("Expected TierNotAllowed, got $result", result is MintResult.TierNotAllowed)
        assertTrue((result as MintResult.TierNotAllowed).reason.contains("hardware_confirm"))
    }

    @Test
    fun `decide with an in_app key proceeds on a device without biometric hardware`() = runTest {
        val useCase = ConfirmAndMintCapabilityUseCase(
            capabilityMinter = minter,
            biometricGate = BiometricGate(authenticator = { _, _ -> true }),
            verifyBranchHeadUseCase = verify(),
            enrollmentStore = enrolledStore(AuthTier.IN_APP_CONFIRM),
            clock = fixedClock,
            hardwareConfirmAvailable = { false },
        )
        val result = useCase(request(OperationType.DECIDE).copy(typedHeadConfirmation = validHead.take(7)))
        assertTrue("Expected Minted, got $result", result is MintResult.Minted)
    }

    @Test
    fun `publish is unaffected by the hardware requirement`() = runTest {
        val useCase = ConfirmAndMintCapabilityUseCase(
            capabilityMinter = minter,
            biometricGate = BiometricGate(authenticator = { _, _ -> true }),
            verifyBranchHeadUseCase = verify(),
            enrollmentStore = enrolledStore(AuthTier.IN_APP_CONFIRM),
            clock = fixedClock,
            hardwareConfirmAvailable = { true },
        )
        val result = useCase(request(OperationType.PUBLISH).copy(typedHeadConfirmation = validHead.take(7)))
        assertTrue("Expected Minted, got $result", result is MintResult.Minted)
    }
}
