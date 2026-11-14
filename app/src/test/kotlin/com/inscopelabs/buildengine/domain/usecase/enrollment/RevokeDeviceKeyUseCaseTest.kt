package com.inscopelabs.buildengine.domain.usecase.enrollment

import com.inscopelabs.buildengine.core.network.GatewayHttpException
import com.inscopelabs.buildengine.core.network.dto.BranchHeadDto
import com.inscopelabs.buildengine.core.network.dto.DecideAckDto
import com.inscopelabs.buildengine.core.network.dto.DecideRequestDto
import com.inscopelabs.buildengine.core.network.dto.DoctorReportDto
import com.inscopelabs.buildengine.core.network.dto.EnrollChallengeDto
import com.inscopelabs.buildengine.core.network.dto.EnrollRequestDto
import com.inscopelabs.buildengine.core.network.dto.EnrollResponseDto
import com.inscopelabs.buildengine.core.network.dto.EngineScopeRequestDto
import com.inscopelabs.buildengine.core.network.dto.GatewayAckDto
import com.inscopelabs.buildengine.core.network.dto.OperationLookupDto
import com.inscopelabs.buildengine.core.network.dto.PlanStatusDto
import com.inscopelabs.buildengine.core.network.dto.PlanStatusTransitionAckDto
import com.inscopelabs.buildengine.core.network.dto.PlanStatusTransitionRequestDto
import com.inscopelabs.buildengine.core.network.dto.PlanSummaryDto
import com.inscopelabs.buildengine.core.network.dto.PlanValidationDto
import com.inscopelabs.buildengine.core.network.dto.PublishRequestDto
import com.inscopelabs.buildengine.core.network.dto.ReconcileRequestDto
import com.inscopelabs.buildengine.core.network.dto.RenderCheckDto
import com.inscopelabs.buildengine.core.network.dto.RevokeKeyRequestDto
import com.inscopelabs.buildengine.core.network.dto.RevokeKeyResponseDto
import com.inscopelabs.buildengine.core.network.dto.RotateKeyRequestDto
import com.inscopelabs.buildengine.core.network.dto.SyncRequestDto
import com.inscopelabs.buildengine.core.network.dto.SyncStatusDto
import com.inscopelabs.buildengine.core.network.dto.TaskSummaryDto
import com.inscopelabs.buildengine.core.network.dto.UploadRequestDto
import com.inscopelabs.buildengine.data.enrollment.DeviceEnrollmentStore
import com.inscopelabs.buildengine.data.remote.GatewayApi
import com.inscopelabs.buildengine.testutil.FakeSharedPreferences
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Gateway-side device key revocation (Stage 3.5, T3-503; BD-1.10, contract §3.21).
 * Fail closed: the local record is only retired after the gateway confirms.
 */
class RevokeDeviceKeyUseCaseTest {

    private lateinit var preferences: FakeSharedPreferences
    private lateinit var store: DeviceEnrollmentStore

    private var revokedResponse: RevokeKeyResponseDto = RevokeKeyResponseDto(revoked = true, kid = "k1", revokedAt = 99L)
    private var lastRequest: RevokeKeyRequestDto? = null
    private var throwOnRevoke: Throwable? = null

    private val gatewayApi = object : GatewayApi {
        override suspend fun listPlans(): List<PlanSummaryDto> = TODO()
        override suspend fun getPlan(planId: String): PlanSummaryDto = TODO()
        override suspend fun planStatus(planId: String): PlanStatusDto = TODO()
        override suspend fun transitionPlanStatus(request: PlanStatusTransitionRequestDto): PlanStatusTransitionAckDto = TODO()
        override suspend fun decide(request: DecideRequestDto): DecideAckDto = TODO()
        override suspend fun validatePlan(planId: String): PlanValidationDto = TODO()
        override suspend fun renderCheck(planId: String): RenderCheckDto = TODO()
        override suspend fun nextTasks(): List<TaskSummaryDto> = TODO()
        override suspend fun branchHead(branch: String): BranchHeadDto = TODO()
        override suspend fun publish(request: PublishRequestDto): GatewayAckDto = TODO()
        override suspend fun reconcile(request: ReconcileRequestDto): GatewayAckDto = TODO()
        override suspend fun upload(request: UploadRequestDto): GatewayAckDto = TODO()
        override suspend fun halt(request: EngineScopeRequestDto): GatewayAckDto = TODO()
        override suspend fun resume(request: EngineScopeRequestDto): GatewayAckDto = TODO()
        override suspend fun sync(request: SyncRequestDto): SyncStatusDto = TODO()
        override suspend fun doctor(): DoctorReportDto = TODO()
        override suspend fun enroll(request: EnrollRequestDto): EnrollResponseDto = TODO()
        override suspend fun rotateKey(request: RotateKeyRequestDto): EnrollResponseDto = TODO()
        override suspend fun enrollChallenge(): EnrollChallengeDto = TODO()
        override suspend fun lookupOperation(idempotencyKey: String): OperationLookupDto = TODO()
        override suspend fun revokeKey(request: RevokeKeyRequestDto): RevokeKeyResponseDto {
            lastRequest = request
            throwOnRevoke?.let { throw it }
            return revokedResponse
        }
    }

    private val deletedAliases = mutableListOf<String>()

    private fun useCase(now: Long = 1700000500L) = RevokeDeviceKeyUseCase(
        gatewayApi = gatewayApi,
        enrollmentStore = store,
        deleteLocalKey = { deletedAliases.add(it) },
        nowEpochSeconds = { now },
    )

    @Before
    fun setUp() {
        preferences = FakeSharedPreferences()
        store = DeviceEnrollmentStore(preferences = preferences)
        store.recordPending("k1", "alias-1", "pub-1", 1700000000L, "hardware_confirm")
        store.activate("k1", 1700000000L)
    }

    @Test
    fun `success retires the local record and deletes the local key`() = runTest {
        val result = useCase()(reason = "operator requested revocation")

        assertTrue("Expected Revoked, got $result", result is RevokeDeviceKeyUseCase.Result.Revoked)
        assertEquals("k1", (result as RevokeDeviceKeyUseCase.Result.Revoked).kid)
        assertEquals(99L, result.revokedAt)
        assertEquals(listOf("alias-1"), deletedAliases)
        assertNull(store.activeRecord())
        assertEquals(DeviceEnrollmentStore.Status.RETIRED, store.record("k1")?.status)
        assertEquals(1700000500L, store.record("k1")?.retiredAtEpochSeconds)
    }

    @Test
    fun `the typed confirmation is REVOKE followed by the kid`() = runTest {
        useCase()(reason = "operator requested revocation")
        val request = checkNotNull(lastRequest)
        assertEquals("k1", request.kid)
        assertEquals("operator requested revocation", request.reason)
        assertEquals("REVOKE k1", request.confirmation)
        assertEquals(RevokeDeviceKeyUseCase.REVOCATION_CONFIRMATION_PREFIX + "k1", request.confirmation)
    }

    @Test
    fun `a blank reason is refused before any network call`() = runTest {
        val result = useCase()(reason = "   ")
        assertTrue(result is RevokeDeviceKeyUseCase.Result.GatewayRefused)
        assertNull(lastRequest)
        assertEquals(DeviceEnrollmentStore.Status.ACTIVE, store.record("k1")?.status)
    }

    @Test
    fun `with no active enrolment the result is NotEnrolled`() = runTest {
        store.clear()
        val result = useCase()(reason = "operator requested revocation")
        assertEquals(RevokeDeviceKeyUseCase.Result.NotEnrolled, result)
        assertNull(lastRequest)
    }

    @Test
    fun `an HTTP refusal leaves the local record untouched`() = runTest {
        throwOnRevoke = GatewayHttpException(statusCode = 403, body = "{}", message = "forbidden")
        val result = useCase()(reason = "operator requested revocation")

        assertTrue("Expected GatewayRefused, got $result", result is RevokeDeviceKeyUseCase.Result.GatewayRefused)
        assertTrue((result as RevokeDeviceKeyUseCase.Result.GatewayRefused).detail.contains("403"))
        assertEquals(DeviceEnrollmentStore.Status.ACTIVE, store.record("k1")?.status)
        assertEquals("k1", store.activeKid())
        assertTrue(deletedAliases.isEmpty())
    }

    @Test
    fun `a gateway answer that does not confirm revocation leaves the local record untouched`() = runTest {
        revokedResponse = RevokeKeyResponseDto(revoked = false, kid = "k1", revokedAt = 0L)
        val result = useCase()(reason = "operator requested revocation")
        assertTrue(result is RevokeDeviceKeyUseCase.Result.GatewayRefused)
        assertEquals(DeviceEnrollmentStore.Status.ACTIVE, store.record("k1")?.status)
        assertTrue(deletedAliases.isEmpty())
    }

    @Test
    fun `a gateway answer for a different kid leaves the local record untouched`() = runTest {
        revokedResponse = RevokeKeyResponseDto(revoked = true, kid = "other-kid", revokedAt = 99L)
        val result = useCase()(reason = "operator requested revocation")
        assertTrue(result is RevokeDeviceKeyUseCase.Result.GatewayRefused)
        assertEquals(DeviceEnrollmentStore.Status.ACTIVE, store.record("k1")?.status)
    }

    @Test
    fun `an unreachable gateway leaves the local record untouched`() = runTest {
        throwOnRevoke = IOException("connection reset")
        val result = useCase()(reason = "operator requested revocation")

        assertTrue("Expected Unreachable, got $result", result is RevokeDeviceKeyUseCase.Result.Unreachable)
        assertEquals(DeviceEnrollmentStore.Status.ACTIVE, store.record("k1")?.status)
        assertTrue(deletedAliases.isEmpty())
    }
}
