package com.inscopelabs.buildengine.manualpipeline.domain

import com.inscopelabs.buildengine.manualpipeline.FakeBranchHeadPort
import com.inscopelabs.buildengine.manualpipeline.Fixtures
import com.inscopelabs.buildengine.manualpipeline.FixedTestClock
import com.inscopelabs.buildengine.manualpipeline.RecordingHandoffEvidenceStore
import com.inscopelabs.buildengine.manualpipeline.domain.model.Deliverable
import com.inscopelabs.buildengine.manualpipeline.domain.model.HandoffEvidence
import com.inscopelabs.buildengine.manualpipeline.domain.model.ManualTask
import com.inscopelabs.buildengine.manualpipeline.domain.model.TaskStatus
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.BuildIdempotencyKeyUseCase
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.VerifyHandoffUseCase
import com.inscopelabs.buildengine.manualpipeline.domain.validation.DeliverableValidator
import com.inscopelabs.buildengine.manualpipeline.domain.validation.HeadFreshnessValidator
import com.inscopelabs.buildengine.manualpipeline.util.IdempotencyKey
import com.inscopelabs.buildengine.manualpipeline.util.Sha256Hasher
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Artifact integrity, deliverable validation, idempotency and handoff tests.
 *
 * These cover the module's evidence chain: the digest the console reports is the digest of the
 * exact bytes it uploads, the deliverable check is driven by the work order rather than by
 * hardcoded file types, and the handoff bundle is only assembled once every reference has been
 * read back from Gateway A.
 */
class ArtifactIntegrityAndHandoffTest {

    // -- artifact integrity (MP-006) --

    @Test
    fun `a sha-256 digest is 64 hex characters and is stable`() {
        val digest = Sha256Hasher.sha256Hex("manual pipeline".toByteArray())

        assertEquals(64, digest.length)
        assertTrue(digest.matches(Regex("^[0-9a-f]{64}$")))
        assertEquals(digest, Sha256Hasher.sha256Hex("manual pipeline".toByteArray()))
    }

    @Test
    fun `different bytes produce different digests`() {
        assertNotEquals(
            Sha256Hasher.sha256Hex("a".toByteArray()),
            Sha256Hasher.sha256Hex("b".toByteArray()),
        )
    }

    @Test
    fun `the archive digest is computed from the exact uploaded bytes`() {
        val zipBytes = buildZip(mapOf("submission/report.md" to "hello".toByteArray()))

        val inspection = Sha256Hasher.inspectZipDigests(zipBytes)

        assertEquals(Sha256Hasher.sha256Hex(zipBytes), inspection.zipSha256)
        assertEquals(zipBytes.size.toLong(), inspection.totalSizeBytes)
    }

    @Test
    fun `per-entry digests and sizes are reported for the archive contents`() {
        val zipBytes = buildZip(
            mapOf(
                "submission/report.md" to "hello".toByteArray(),
                "submission/notes.txt" to "world!".toByteArray()),
        )

        val inspection = Sha256Hasher.inspectZipDigests(zipBytes)

        assertEquals(2, inspection.fileDigests.size)
        assertEquals(5L, inspection.fileSizesBytes["submission/report.md"])
        assertEquals(
            Sha256Hasher.sha256Hex("hello".toByteArray()),
            inspection.fileDigests["submission/report.md"],
        )
    }

    // -- deliverable validation (MP-005) --

    private val deliverableValidator = DeliverableValidator()

    @Test
    fun `a complete archive validates against the declared deliverables`() {
        val result = deliverableValidator.validate(
            declared = listOf(Deliverable(relativePath = "submission/report.md")),
            manifest = Fixtures.manifest(),
        )

        assertTrue(result.valid)
        assertTrue(result.missingRequiredPaths.isEmpty())
    }

    @Test
    fun `a missing required deliverable is rejected`() {
        val result = deliverableValidator.validate(
            declared = listOf(Deliverable(relativePath = "submission/report.md")),
            manifest = Fixtures.manifest(
                suppliedPaths = emptyList(),
                missingDeclaredPaths = listOf("submission/report.md"),
            ),
        )

        assertFalse(result.valid)
        assertEquals(listOf("submission/report.md"), result.missingRequiredPaths)
    }

    @Test
    fun `a denied repository path is rejected`() {
        val result = deliverableValidator.validate(
            declared = listOf(Deliverable(relativePath = "submission/report.md")),
            manifest = Fixtures.manifest(suppliedPaths = listOf("engine/secrets.txt")),
        )

        assertFalse(result.valid)
        assertTrue(result.deniedPaths.isNotEmpty())
    }

    @Test
    fun `a path traversal attempt is rejected`() {
        val result = deliverableValidator.validate(
            declared = listOf(Deliverable(relativePath = "submission/report.md")),
            manifest = Fixtures.manifest(suppliedPaths = listOf("../../etc/passwd")),
        )

        assertFalse(result.valid)
        assertTrue(result.deniedPaths.isNotEmpty())
    }

    @Test
    fun `a digest mismatch against the work order is rejected`() {
        val result = deliverableValidator.validate(
            declared = listOf(
                Deliverable(
                    relativePath = "submission/report.md",
                    expectedSha256 = "a".repeat(64),
                ),
            ),
            manifest = Fixtures.manifest(),
        )

        assertFalse(result.valid)
        assertEquals(listOf("submission/report.md"), result.digestMismatches)
    }

    @Test
    fun `no attached archive is not a valid submission`() {
        val result = deliverableValidator.validate(
            declared = listOf(Deliverable(relativePath = "submission/report.md")),
            manifest = null,
        )

        assertFalse(result.valid)
    }

    // -- idempotency keys (ADR 0013) --

    @Test
    fun `an idempotency key is 32 lowercase hex characters`() {
        val key = BuildIdempotencyKeyUseCase()()

        assertEquals(32, key.length)
        assertTrue(key.matches(Regex("^[0-9a-f]{32}$")))
        assertTrue(BuildIdempotencyKeyUseCase().isValid(key))
    }

    @Test
    fun `each idempotency key is distinct`() {
        val keys = (1..64).map { BuildIdempotencyKeyUseCase()() }

        assertEquals(64, keys.toSet().size)
    }

    @Test
    fun `a malformed idempotency key is rejected`() {
        assertFalse(IdempotencyKey.isValid("short"))
        assertFalse(IdempotencyKey.isValid(null))
        assertFalse(IdempotencyKey.isValid("A".repeat(32)))
        assertTrue(IdempotencyKey.isValid("a".repeat(32)))
    }

    @Test
    fun `the util key generator matches the use case contract`() {
        val key = IdempotencyKey.generate()

        assertEquals(32, key.length)
        assertTrue(IdempotencyKey.isValid(key))
    }

    // -- handoff (MP-014) --

    private val handoffStore = RecordingHandoffEvidenceStore()

    private fun handoffUseCase(
        branchHead: FakeBranchHeadPort = FakeBranchHeadPort(),
    ): VerifyHandoffUseCase = VerifyHandoffUseCase(
        branchHeadPort = branchHead,
        handoffEvidenceStore = handoffStore,
        headFreshnessValidator = HeadFreshnessValidator(),
        clock = FixedTestClock(),
    )

    private suspend fun verify(
        useCase: VerifyHandoffUseCase = handoffUseCase(),
        task: ManualTask = Fixtures.task(),
        submissionId: String = Fixtures.SUBMISSION_ID,
        zipSha256: String = Fixtures.ZIP_SHA256,
        uploadAuditRef: String = "audit-upload",
        transitionAuditRef: String = "audit-transition",
        observedStatus: TaskStatus = TaskStatus.COMPLETED,
    ) = useCase(
        task = task,
        submissionId = submissionId,
        zipSha256 = zipSha256,
        uploadAuditRef = uploadAuditRef,
        transitionAuditRef = transitionAuditRef,
        uploadIdempotencyKey = "a".repeat(32),
        transitionIdempotencyKey = "b".repeat(32),
        observedStatus = observedStatus,
    )

    @Test
    fun `a fully evidenced task is ready for handoff`() = runTest {
        val result = verify()

        assertTrue(result is VerifyHandoffUseCase.HandoffVerificationResult.Ready)
        val evidence = (result as VerifyHandoffUseCase.HandoffVerificationResult.Ready).evidence
        assertEquals(HandoffEvidence.READY_MARKER, evidence.readinessState)
        assertTrue(evidence.isReady)
    }

    @Test
    fun `the handoff bundle is persisted with the gateway audit references`() = runTest {
        verify()

        val evidence = handoffStore.saved.single()
        assertEquals("audit-upload", evidence.uploadAuditRef)
        assertEquals("audit-transition", evidence.transitionAuditRef)
        assertEquals(Fixtures.ZIP_SHA256, evidence.zipSha256)
    }

    @Test
    fun `the handoff bundle records the live head read back from the gateway`() = runTest {
        val result = verify()

        val evidence = (result as VerifyHandoffUseCase.HandoffVerificationResult.Ready).evidence
        assertEquals(Fixtures.HEAD_SHA, evidence.verifiedHeadSha)
    }

    @Test
    fun `a task the gateway still reports as in flight is not ready for handoff`() = runTest {
        val result = verify(observedStatus = TaskStatus.IN_PROGRESS)

        assertTrue(result is VerifyHandoffUseCase.HandoffVerificationResult.Incomplete)
        assertTrue(
            (result as VerifyHandoffUseCase.HandoffVerificationResult.Incomplete)
                .missingItems
                .any { it.contains("completed", ignoreCase = true) },
        )
        assertTrue(handoffStore.saved.isEmpty())
    }

    @Test
    fun `a missing audit reference makes the handoff incomplete`() = runTest {        val result = verify(uploadAuditRef = "")

        assertTrue(result is VerifyHandoffUseCase.HandoffVerificationResult.Incomplete)
        assertTrue(
            (result as VerifyHandoffUseCase.HandoffVerificationResult.Incomplete)
                .missingItems
                .contains("upload auditRef"),
        )
        assertTrue(handoffStore.saved.isEmpty())
    }

    @Test
    fun `a malformed archive digest makes the handoff incomplete`() = runTest {
        val result = verify(zipSha256 = "not-a-digest")

        assertTrue(result is VerifyHandoffUseCase.HandoffVerificationResult.Incomplete)
        assertTrue(
            (result as VerifyHandoffUseCase.HandoffVerificationResult.Incomplete)
                .missingItems
                .contains("zipSha256"),
        )
    }

    @Test
    fun `an unavailable live head makes the handoff incomplete`() = runTest {
        val result = verify(useCase = handoffUseCase(branchHead = FakeBranchHeadPort(liveHeadSha = "short")))

        assertTrue(result is VerifyHandoffUseCase.HandoffVerificationResult.Incomplete)
        assertTrue(handoffStore.saved.isEmpty())
    }

    private fun buildZip(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
