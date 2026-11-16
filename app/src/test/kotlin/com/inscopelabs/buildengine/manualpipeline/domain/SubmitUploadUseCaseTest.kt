package com.inscopelabs.buildengine.manualpipeline.domain

import com.inscopelabs.buildengine.manualpipeline.FakeAuthorityPort
import com.inscopelabs.buildengine.manualpipeline.FakeBranchHeadPort
import com.inscopelabs.buildengine.manualpipeline.FakeMintingPort
import com.inscopelabs.buildengine.manualpipeline.FakeTaskGatewayPort
import com.inscopelabs.buildengine.manualpipeline.Fixtures
import com.inscopelabs.buildengine.manualpipeline.FixedTestClock
import com.inscopelabs.buildengine.manualpipeline.RecordingOperatorActionStore
import com.inscopelabs.buildengine.manualpipeline.RecordingUploadPort
import com.inscopelabs.buildengine.manualpipeline.RecordingWorkflowRecordStore
import com.inscopelabs.buildengine.manualpipeline.StrictTestRedactor
import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationOutcome
import com.inscopelabs.buildengine.manualpipeline.domain.port.MintOutcome
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.RecordOperatorActionUseCase
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.RecordWorkflowMetadataUseCase
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.ResolveAuthorityUseCase
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.SubmitUploadUseCase
import com.inscopelabs.buildengine.manualpipeline.domain.validation.EnvironmentIsolationValidator
import com.inscopelabs.buildengine.manualpipeline.domain.validation.HaltStateValidator
import com.inscopelabs.buildengine.manualpipeline.domain.validation.HeadFreshnessValidator
import com.inscopelabs.buildengine.manualpipeline.domain.validation.SealStateValidator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Upload tests for the manual pipeline's submission path (MP-008).
 *
 * The delivery unit is a single immutable archive (contract §3.10): one `zipBase64` and one
 * `zipSha256`. The console never re-packs or mutates the bytes it was given.
 */
class SubmitUploadUseCaseTest {

    private val authorityPort = FakeAuthorityPort(Fixtures.authority())
    private val taskGatewayPort = FakeTaskGatewayPort()
    private val branchHeadPort = FakeBranchHeadPort()
    private val mintingPort = FakeMintingPort(MintOutcome.Minted(Fixtures.mintedHandle()))
    private val uploadPort = RecordingUploadPort()
    private val actionStore = RecordingOperatorActionStore()
    private val workflowStore = RecordingWorkflowRecordStore()

    private fun useCase(
        authority: FakeAuthorityPort = this.authorityPort,
        gateway: FakeTaskGatewayPort = this.taskGatewayPort,
        branchHead: FakeBranchHeadPort = this.branchHeadPort,
        minting: FakeMintingPort = this.mintingPort,
        uploads: RecordingUploadPort = this.uploadPort,
    ): SubmitUploadUseCase = SubmitUploadUseCase(
        resolveAuthorityUseCase = ResolveAuthorityUseCase(
            authorityPort = authority,
            environmentIsolationValidator = EnvironmentIsolationValidator(),
        ),
        taskGatewayPort = gateway,
        branchHeadPort = branchHead,
        haltStateValidator = HaltStateValidator(),
        headFreshnessValidator = HeadFreshnessValidator(),
        sealStateValidator = SealStateValidator(),
        mintingPort = minting,
        uploadPort = uploads,
        recordWorkflowMetadataUseCase = RecordWorkflowMetadataUseCase(
            store = workflowStore,
            redactor = StrictTestRedactor(),
            clock = FixedTestClock(),
        ),
        recordOperatorActionUseCase = RecordOperatorActionUseCase(
            store = actionStore,
            redactor = StrictTestRedactor(),
            clock = FixedTestClock(),
        ),
    )

    @Test
    fun `a successful upload sends exactly one archive with the manifest digest`() = runTest {
        val result = useCase()(
            task = Fixtures.task(),
            manifest = Fixtures.manifest(),
            zipBase64 = "UEsDBAo=",
        )

        assertTrue(result is SubmitUploadUseCase.UploadResult.Executed)
        assertEquals(1, uploadPort.dispatches)
        assertEquals("UEsDBAo=", uploadPort.lastZipBase64)
        assertEquals(Fixtures.ZIP_SHA256, uploadPort.lastZipSha256)
    }

    @Test
    fun `an empty archive payload is blocked before minting`() = runTest {
        val result = useCase()(
            task = Fixtures.task(),
            manifest = Fixtures.manifest(),
            zipBase64 = "   ",
        )

        assertTrue(result is SubmitUploadUseCase.UploadResult.Blocked)
        assertEquals("ARCHIVE_EMPTY", (result as SubmitUploadUseCase.UploadResult.Blocked).code)
        assertEquals(0, mintingPort.uploadMints)
        assertEquals(0, uploadPort.dispatches)
    }

    @Test
    fun `a missing work-order id blocks the upload before minting`() = runTest {
        val result = useCase()(
            task = Fixtures.task(orderId = ""),
            manifest = Fixtures.manifest(),
            zipBase64 = "UEsDBAo=",
        )

        assertTrue(result is SubmitUploadUseCase.UploadResult.Blocked)
        assertEquals("ORDER_MISSING", (result as SubmitUploadUseCase.UploadResult.Blocked).code)
        assertEquals(0, mintingPort.uploadMints)
        assertEquals(0, uploadPort.dispatches)
    }

    @Test
    fun `a different archive over an already uploaded one is refused as immutable`() = runTest {
        val result = useCase()(
            task = Fixtures.task(),
            manifest = Fixtures.manifest(digest = Fixtures.digest(zipSha256 = "f".repeat(64))),
            zipBase64 = "UEsDBAo=",
            alreadyUploadedSha256 = Fixtures.ZIP_SHA256,
        )

        assertTrue(result is SubmitUploadUseCase.UploadResult.Blocked)
        assertEquals("SUBMISSION_IMMUTABLE", (result as SubmitUploadUseCase.UploadResult.Blocked).code)
        assertEquals(0, mintingPort.uploadMints)
        assertEquals(0, uploadPort.dispatches)
    }

    @Test
    fun `a halted plan blocks the upload before minting`() = runTest {
        val halted = FakeTaskGatewayPort(status = Fixtures.planStatus(halted = true))
        val result = useCase(gateway = halted)(
            task = Fixtures.task(),
            manifest = Fixtures.manifest(),
            zipBase64 = "UEsDBAo=",
        )

        assertTrue(result is SubmitUploadUseCase.UploadResult.Blocked)
        assertEquals("HALT_ACTIVE", (result as SubmitUploadUseCase.UploadResult.Blocked).code)
        assertEquals(0, mintingPort.uploadMints)
        assertEquals(0, uploadPort.dispatches)
    }

    @Test
    fun `a drifted branch head blocks the upload before minting`() = runTest {
        val drifted = FakeBranchHeadPort(liveHeadSha = Fixtures.OTHER_HEAD_SHA)
        val result = useCase(branchHead = drifted)(
            task = Fixtures.task(),
            manifest = Fixtures.manifest(),
            zipBase64 = "UEsDBAo=",
        )

        assertTrue(result is SubmitUploadUseCase.UploadResult.Blocked)
        assertEquals("HEAD_DRIFT", (result as SubmitUploadUseCase.UploadResult.Blocked).code)
        assertEquals(0, mintingPort.uploadMints)
        assertEquals(0, uploadPort.dispatches)
    }

    @Test
    fun `a declined confirmation sends no archive`() = runTest {
        val declined = FakeMintingPort(MintOutcome.Declined)
        val result = useCase(minting = declined)(
            task = Fixtures.task(),
            manifest = Fixtures.manifest(),
            zipBase64 = "UEsDBAo=",
        )

        assertTrue(result is SubmitUploadUseCase.UploadResult.Blocked)
        assertEquals("DECLINED", (result as SubmitUploadUseCase.UploadResult.Blocked).code)
        assertEquals(0, uploadPort.dispatches)
    }

    @Test
    fun `a successful upload is recorded with its audit reference and digest`() = runTest {
        useCase()(
            task = Fixtures.task(),
            manifest = Fixtures.manifest(),
            zipBase64 = "UEsDBAo=",
        )

        val action = actionStore.recorded.single()
        assertEquals("UPLOAD", action.actionName)
        assertEquals("SUCCEEDED", action.outcomeLabel)
        assertEquals("audit-2", action.auditRef)

        val record = workflowStore.recorded.single()
        assertEquals(Fixtures.SUBMISSION_ID, record.submissionId)
        assertEquals(Fixtures.ZIP_SHA256, record.zipSha256)
    }

    @Test
    fun `an uncertain upload keeps its idempotency key for lookup recovery`() = runTest {
        val uncertain = RecordingUploadPort(
            OperationOutcome.UncertainCompletion(idempotencyKey = "b".repeat(32)),
        )
        val result = useCase(uploads = uncertain)(
            task = Fixtures.task(),
            manifest = Fixtures.manifest(),
            zipBase64 = "UEsDBAo=",
        )

        assertTrue(result is SubmitUploadUseCase.UploadResult.Executed)
        assertEquals("b".repeat(32), actionStore.recorded.single().idempotencyKey)
    }
}
