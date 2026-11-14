package com.inscopelabs.buildengine.data.repository

import com.inscopelabs.buildengine.capability.CapabilityMinter
import com.inscopelabs.buildengine.capability.DeviceNonceSource
import com.inscopelabs.buildengine.capability.model.CapabilityScope
import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.capability.signing.HmacCapabilitySigner
import com.inscopelabs.buildengine.core.crypto.SecureRandomSource
import com.inscopelabs.buildengine.core.network.dto.GatewayAckDto
import com.inscopelabs.buildengine.core.network.dto.PlanStatusTransitionAckDto
import com.inscopelabs.buildengine.core.time.FixedClock
import com.inscopelabs.buildengine.data.local.IdempotencyKeyGenerator
import com.inscopelabs.buildengine.domain.model.OperationOutcome
import com.inscopelabs.buildengine.domain.model.OperationRequest
import com.inscopelabs.buildengine.testutil.FakeGatewayApi
import com.inscopelabs.buildengine.testutil.InMemoryOperationJournalDao
import com.inscopelabs.buildengine.testutil.RecordingFakeJournalCipher
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage 3.4 (T3-401..T3-404): verifies [OperationRepositoryImpl] journal behavior for
 * halt/resume/recordedHaltCause, reconcile, upload, and setTaskStatus.
 */
class Stage34OperationRepositoryTest {

    private val clock = FixedClock(1700000000L)
    private val nonceSource = DeviceNonceSource(SecureRandomSource())
    private val minter = CapabilityMinter(
        signer = HmacCapabilitySigner(keyId = "test_kid", secretUtf8 = "test-secret-key-32-bytes-long!!"),
        nonceSource = nonceSource,
        clock = clock,
    )
    private val taskBranch = "task-10008-refactor-parser"
    private val taskHead = "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d"
    private val engineHead = "c".repeat(40)

    private fun newRepo(gateway: FakeGatewayApi = FakeGatewayApi()): Pair<OperationRepositoryImpl, RecordingFakeJournalCipher> {
        val dao = InMemoryOperationJournalDao()
        val cipher = RecordingFakeJournalCipher()
        val repo = OperationRepositoryImpl(
            gateway = gateway,
            journalDao = dao,
            cipher = cipher,
            keyGenerator = IdempotencyKeyGenerator(SecureRandomSource()),
            clock = clock,
        )
        return repo to cipher
    }

    private fun mintFor(
        op: OperationType,
        branch: String,
        head: String,
        task: String = "10008",
    ): Pair<OperationRequest, com.inscopelabs.buildengine.capability.signing.SignedCapability> {
        val scope = CapabilityScope(op = op, branch = branch, head = head, nonce = nonceSource.newNonce())
        val req = OperationRequest(
            op = op,
            scope = scope,
            plan = "atlas",
            task = task,
            order = "order-7731",
        )
        val signed = minter.mint(scope = scope, order = "order-7731", plan = "atlas", task = task)
        return req to signed
    }

    @Test
    fun `halt records cause in encrypted journal, resume clears it, and decrypt failure fails closed`() = runTest {
        val gateway = FakeGatewayApi().apply {
            branchHeadResult = { engineHead }
            haltResult = { GatewayAckDto(accepted = true, operation = "HALT", branch = "engine", headSha = engineHead, auditRef = "audit-h1") }
            resumeResult = { GatewayAckDto(accepted = true, operation = "RESUME", branch = "engine", headSha = engineHead, auditRef = "audit-r1") }
        }
        val (repo, cipher) = newRepo(gateway)
        assertNull(repo.recordedHaltCause())

        val (haltReq, haltCap) = mintFor(OperationType.HALT, branch = "engine", head = engineHead, task = "engine")
        val haltOutcome = repo.halt(haltReq, "Security drill", haltCap)
        assertTrue(haltOutcome is OperationOutcome.Succeeded)
        assertEquals("Security drill", repo.recordedHaltCause())

        // Decrypt failure must fail closed rather than returning a stale/unverified cause.
        cipher.failOnDecrypt = true
        assertNull(repo.recordedHaltCause())
        cipher.failOnDecrypt = false

        val (resumeReq, resumeCap) = mintFor(OperationType.RESUME, branch = "engine", head = engineHead, task = "engine")
        val resumeOutcome = repo.resume(resumeReq, "Security drill", resumeCap)
        assertTrue(resumeOutcome is OperationOutcome.Succeeded)
        assertNull(repo.recordedHaltCause())
    }

    @Test
    fun `reconcile propagates gateway MERGED outcome when accepted and live head matches`() = runTest {
        val gateway = FakeGatewayApi().apply {
            branchHeadResult = { taskHead }
            reconcileResult = {
                GatewayAckDto(
                    accepted = true,
                    operation = "RECONCILE",
                    branch = taskBranch,
                    headSha = taskHead,
                    outcome = "MERGED: pr #42",
                    auditRef = "audit-rec-1",
                )
            }
        }
        val (repo, _) = newRepo(gateway)
        val (req, cap) = mintFor(OperationType.RECONCILE, branch = taskBranch, head = taskHead)

        val outcome = repo.reconcile(req, "sub-42", cap)

        assertEquals(
            OperationOutcome.Succeeded(auditRef = "audit-rec-1", headSha = taskHead, outcome = "MERGED: pr #42"),
            outcome,
        )
    }

    @Test
    fun `setTaskStatus sends transition DTO, verifies live head, and propagates newStatus`() = runTest {
        val gateway = FakeGatewayApi().apply {
            branchHeadResult = { taskHead }
            transitionPlanStatusResult = { req ->
                PlanStatusTransitionAckDto(
                    accepted = true,
                    operation = "SET_DONE",
                    plan = req.plan,
                    task = req.task,
                    newStatus = "completed",
                    headSha = taskHead,
                    auditRef = "audit-st-1",
                )
            }
        }
        val (repo, _) = newRepo(gateway)
        val (req, cap) = mintFor(OperationType.SET_DONE, branch = taskBranch, head = taskHead)

        val outcome = repo.setTaskStatus(req, "completed", cap)

        assertEquals(
            OperationOutcome.Succeeded(auditRef = "audit-st-1", headSha = taskHead, outcome = "completed"),
            outcome,
        )
        assertEquals("completed", gateway.transitionPlanStatusCalls.single().targetStatus)
    }

    @Test
    fun `upload forwards sha256 and base64 and verifies live head`() = runTest {
        val payload = "PK-sample-bytes".toByteArray()
        val sha = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
        val b64 = Base64.getEncoder().encodeToString(payload)
        val gateway = FakeGatewayApi().apply {
            branchHeadResult = { taskHead }
            uploadResult = {
                GatewayAckDto(
                    accepted = true,
                    operation = "UPLOAD",
                    branch = taskBranch,
                    headSha = taskHead,
                    auditRef = "audit-up-1",
                )
            }
        }
        val (repo, _) = newRepo(gateway)
        val (req, cap) = mintFor(OperationType.UPLOAD, branch = taskBranch, head = taskHead)

        val outcome = repo.upload(req, "sub-99", sha, b64, cap)

        assertEquals(OperationOutcome.Succeeded(auditRef = "audit-up-1", headSha = taskHead), outcome)
        val dto = gateway.uploadCalls.single()
        assertEquals(sha, dto.zipSha256)
        assertEquals(b64, dto.zipBase64)
        assertEquals("sub-99", dto.submissionId)
    }
}
