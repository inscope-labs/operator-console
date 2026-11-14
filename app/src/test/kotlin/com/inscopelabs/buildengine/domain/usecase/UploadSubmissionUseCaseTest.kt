package com.inscopelabs.buildengine.domain.usecase

import com.inscopelabs.buildengine.capability.CapabilityMinter
import com.inscopelabs.buildengine.capability.DeviceNonceSource
import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.capability.signing.HmacCapabilitySigner
import com.inscopelabs.buildengine.core.crypto.SecureRandomSource
import com.inscopelabs.buildengine.core.network.dto.GatewayAckDto
import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.core.security.BiometricGate
import com.inscopelabs.buildengine.core.time.FixedClock
import com.inscopelabs.buildengine.data.local.IdempotencyKeyGenerator
import com.inscopelabs.buildengine.data.repository.OperationRepositoryImpl
import com.inscopelabs.buildengine.domain.model.OperationOutcome
import com.inscopelabs.buildengine.domain.model.TaskBranch
import com.inscopelabs.buildengine.domain.usecase.common.ConfirmAndMintCapabilityUseCase
import com.inscopelabs.buildengine.domain.usecase.common.VerifyBranchHeadUseCase
import com.inscopelabs.buildengine.domain.usecase.write.ArchivePreparationResult
import com.inscopelabs.buildengine.domain.usecase.write.SubmissionArchivePreparer
import com.inscopelabs.buildengine.domain.usecase.write.UploadResult
import com.inscopelabs.buildengine.domain.usecase.write.UploadSubmissionUseCase
import com.inscopelabs.buildengine.domain.usecase.write.UploadTarget
import com.inscopelabs.buildengine.testutil.FakeGatewayApi
import com.inscopelabs.buildengine.testutil.FakeTaskRepository
import com.inscopelabs.buildengine.testutil.InMemoryOperationJournalDao
import com.inscopelabs.buildengine.testutil.RecordingFakeJournalCipher
import com.inscopelabs.buildengine.testutil.RecordingStage34OperationRepository
import com.inscopelabs.buildengine.util.Logger
import com.inscopelabs.buildengine.util.Redactor
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Stage 3.4 (T3-403 / PR3-022): SubmissionArchivePreparer + UploadSubmissionUseCase + redaction.
 */
class UploadSubmissionUseCaseTest {

    private val clock = FixedClock(1700000000L)
    private val nonceSource = DeviceNonceSource(SecureRandomSource())
    private val minter = CapabilityMinter(
        signer = HmacCapabilitySigner(keyId = "test_kid", secretUtf8 = "test-secret-key-32-bytes-long!!"),
        nonceSource = nonceSource,
        clock = clock,
    )
    private val branch = "task-10008-refactor-parser"
    private val head = "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d"
    private val target = UploadTarget(
        taskId = "10008",
        plan = "atlas",
        order = "order-7731",
        branch = branch,
        headSha = head,
        submissionId = "sub-100",
    )
    private val taskRepository = FakeTaskRepository().apply {
        liveHeadResult = { AppResult.Success(head) }
        branchResult = { AppResult.Success(TaskBranch(name = branch, headSha = head)) }
    }
    private var prompts = 0
    private val biometricGate = BiometricGate { _, _ ->
        prompts++
        true
    }
    private val recordingRepo = RecordingStage34OperationRepository(
        defaultOutcome = OperationOutcome.Succeeded(auditRef = "audit-upload-1", headSha = head),
    )
    private val capturedLogs = mutableListOf<String>()

    @Before
    fun setUp() {
        capturedLogs.clear()
        Logger.logSink = { _, tag, msg, err ->
            capturedLogs.add("$tag: $msg ${err?.message.orEmpty()}")
        }
    }

    @After
    fun tearDown() {
        Logger.logSink = null
    }

    private fun useCase(opRepo: RecordingStage34OperationRepository = recordingRepo) = UploadSubmissionUseCase(
        operationRepository = opRepo,
        confirmAndMintCapability = ConfirmAndMintCapabilityUseCase(
            capabilityMinter = minter,
            biometricGate = biometricGate,
            verifyBranchHeadUseCase = VerifyBranchHeadUseCase(taskRepository),
            clock = clock,
        ),
        nonceSource = nonceSource,
    )

    private fun buildZip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((path, content) in entries) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(content)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    @Test
    fun `valid archive computes SHA-256 over immutable bytes matching base64 decode and keeps original unchanged`() = runTest {
        val originalZip = buildZip("src/Parser.kt" to "fun parse() = 42".toByteArray())
        val originalSnapshot = originalZip.copyOf()

        val result = useCase()(
            target = target,
            openStream = { ByteArrayInputStream(originalZip) },
        )

        assertTrue("Expected Executed, got $result", result is UploadResult.Executed)
        val uploadCall = recordingRepo.uploads.single()
        val decodedBytes = Base64.getDecoder().decode(uploadCall.zipBase64)
        assertArrayEquals(originalSnapshot, decodedBytes)
        assertArrayEquals(originalSnapshot, originalZip)

        val expectedSha = MessageDigest.getInstance("SHA-256")
            .digest(originalSnapshot)
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        assertEquals(expectedSha, uploadCall.zipSha256)
        assertEquals(OperationType.UPLOAD, uploadCall.capability.capability.op)
        assertEquals(1, prompts)
    }

    @Test
    fun `empty, oversized, corrupt, path-traversal, and denied-path archives fail before prompt or send`() = runTest {
        val emptyResult = useCase()(target, openStream = { ByteArrayInputStream(ByteArray(0)) })
        assertTrue(emptyResult is UploadResult.InvalidArchive)

        val oversizedResult = SubmissionArchivePreparer.prepareFromStream(
            openStream = { ByteArrayInputStream(ByteArray(2048)) },
            maxBytes = 1024L,
        )
        assertTrue(oversizedResult is ArchivePreparationResult.Invalid)

        val nonZip = useCase()(target, openStream = { ByteArrayInputStream("not a zip".toByteArray()) })
        assertTrue(nonZip is UploadResult.InvalidArchive)

        val corruptZipBytes = buildZip("a.txt" to "hello".toByteArray()).copyOfRange(0, 12)
        val corruptResult = useCase()(target, openStream = { ByteArrayInputStream(corruptZipBytes) })
        assertTrue(corruptResult is UploadResult.InvalidArchive)

        val badEntries = listOf(
            "../outside.txt",
            "/etc/passwd",
            "\\windows\\system32",
            "C:\\temp\\bad.txt",
            "engine/core.kt",
            "plans/atlas.json",
            ".github/workflows/ci.yml",
        )
        for (badPath in badEntries) {
            val zip = buildZip(badPath to "x".toByteArray())
            val res = useCase()(target, openStream = { ByteArrayInputStream(zip) })
            assertTrue("Expected InvalidArchive for $badPath, got $res", res is UploadResult.InvalidArchive)
        }

        assertEquals(0, prompts)
        assertTrue(recordingRepo.uploads.isEmpty())
    }

    @Test
    fun `journal persistence and logs never contain zipBase64, capability signature, or nonce`() = runTest {
        val dao = InMemoryOperationJournalDao()
        val cipher = RecordingFakeJournalCipher()
        val gateway = FakeGatewayApi().apply {
            branchHeadResult = { head }
            uploadResult = {
                GatewayAckDto(
                    accepted = true,
                    operation = "UPLOAD",
                    branch = target.branch,
                    headSha = head,
                    auditRef = "audit-upload-test",
                )
            }
        }
        val realExecutor = OperationRepositoryImpl(
            gateway = gateway,
            journalDao = dao,
            cipher = cipher,
            keyGenerator = IdempotencyKeyGenerator(SecureRandomSource()),
            clock = clock,
        )
        val uploadUseCase = UploadSubmissionUseCase(
            operationRepository = realExecutor,
            confirmAndMintCapability = ConfirmAndMintCapabilityUseCase(
                capabilityMinter = minter,
                biometricGate = biometricGate,
                verifyBranchHeadUseCase = VerifyBranchHeadUseCase(taskRepository),
                clock = clock,
            ),
            nonceSource = nonceSource,
        )

        val zip = buildZip("submission/solution.kt" to "package s; val ok = true".toByteArray())
        val prepared = (SubmissionArchivePreparer.prepareFromBytes(zip) as ArchivePreparationResult.Ready).archive

        val result = uploadUseCase(target, prepared) as UploadResult.Executed
        assertTrue(result.outcome is OperationOutcome.Succeeded)
        assertEquals(1, gateway.uploadCalls.size)
        val sentDto = gateway.uploadCalls.single()
        assertEquals(prepared.zipBase64, sentDto.zipBase64)
        assertEquals(prepared.zipSha256, sentDto.zipSha256)

        val persisted = dao.getAll().single()
        val allPersistedStrings = listOf(
            persisted.encryptedOperation,
            persisted.encryptedPlan,
            persisted.encryptedTask,
            persisted.encryptedBranch,
            persisted.encryptedExpectedHead,
            persisted.encryptedExtraFields.orEmpty(),
            persisted.encryptedAuditRef.orEmpty(),
            persisted.encryptedLastErrorCode.orEmpty(),
            persisted.encryptedResultHeadSha.orEmpty(),
            persisted.intentFingerprint,
            persisted.idempotencyKey,
        ) + cipher.recordedPlaintexts + capturedLogs

        for (text in allPersistedStrings) {
            assertFalse("zipBase64 leaked into journal/logs!", text.contains(prepared.zipBase64))
            assertFalse("Capability/secret leaked into journal/logs: $text", Redactor.containsForbidden(text))
            assertFalse("Raw nonce leaked!", text.contains(sentDto.capability.substringAfterLast('\n')))
        }
        assertFalse("Prepared archive toString() leaked base64!", prepared.toString().contains(prepared.zipBase64))
    }
}
