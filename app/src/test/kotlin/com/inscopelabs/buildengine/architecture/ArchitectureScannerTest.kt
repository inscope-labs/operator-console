package com.inscopelabs.buildengine.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mandatory negative- and positive-fixture tests for [ArchitectureScanner] (`T3-601` / `PR3-033`).
 *
 * No fixture contains a real secret; token-shaped inputs are assembled at runtime by concatenation.
 */
class ArchitectureScannerTest {

    @Test
    fun `rule A detects concatenated GitHub tokens AWS keys and PEM private key headers`() {
        val fakeClassicToken = "ghp_" + "A".repeat(36)
        val fakeFineGrainedPat = "github_pat_" + "B".repeat(22)
        val fakeAwsKey = "AKIA" + "1234567890ABCDEF"
        val fakePemHeader = "-----BEGIN " + "RSA PRIVATE KEY-----"

        val badSource = """
            package com.inscopelabs.buildengine.sample
            val pat = "$fakeClassicToken"
            val fg = "$fakeFineGrainedPat"
            val aws = "$fakeAwsKey"
            val pem = "$fakePemHeader"
        """.trimIndent()

        val findings = ArchitectureScanner.scanTokenPatterns("BadTokens.kt", badSource)
        assertEquals(4, findings.size)
        assertTrue(findings.any { it.rule == "token_pattern:github_classic_token" })
        assertTrue(findings.any { it.rule == "token_pattern:github_fine_grained_pat" })
        assertTrue(findings.any { it.rule == "token_pattern:aws_access_key_id" })
        assertTrue(findings.any { it.rule == "token_pattern:pem_private_key_header" })
    }

    @Test
    fun `rule A ignores bare prefixes and regex source text as clean`() {
        val cleanSource = """
            package com.inscopelabs.buildengine.util
            val prefixes = listOf("ghp_", "github_pat_", "AKIA")
            val patterns = listOf(
                Regex("gh[pousr]_[A-Za-z0-9]{16,}"),
                Regex("github_pat_[A-Za-z0-9_]{16,}"),
                Regex("AKIA[0-9A-Z]{16}"),
            )
            val docNote = "Never allow a PEM private key in assets."
        """.trimIndent()

        val findings = ArchitectureScanner.scanTokenPatterns("CleanRedactor.kt", cleanSource)
        assertTrue("Expected no false positives on clean fixture: $findings", findings.isEmpty())
    }

    @Test
    fun `rule B detects Runtime exec ProcessBuilder jgit and shell strings`() {
        val badSource = """
            package com.inscopelabs.buildengine.sample
            import org.eclipse.jgit.api.Git
            fun runShell() {
                Runtime.getRuntime().exec("git status")
                ProcessBuilder("/system/bin/sh", "-c", "ls")
                val cmd = "sh -c echo"
            }
        """.trimIndent()

        val findings = ArchitectureScanner.scanShellAndGitExecution("BadShell.kt", badSource)
        assertTrue(findings.any { it.rule == "shell_git:jgit_package" })
        assertTrue(findings.any { it.rule == "shell_git:runtime_get_runtime" })
        assertTrue(findings.any { it.rule == "shell_git:process_exec_call" })
        assertTrue(findings.any { it.rule == "shell_git:process_builder" })
        assertTrue(findings.any { it.rule == "shell_git:system_bin_sh" })
        assertTrue(findings.any { it.rule == "shell_git:sh_minus_c" })
    }

    @Test
    fun `rule B reports zero findings on clean Kotlin code`() {
        val cleanSource = """
            package com.inscopelabs.buildengine.domain.usecase.read
            class SafeUseCase {
                fun describe(): String = "Status check executed on gateway CI."
            }
        """.trimIndent()

        val findings = ArchitectureScanner.scanShellAndGitExecution("SafeUseCase.kt", cleanSource)
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `rule D detects gateway publish bypass outside executor and unguarded inside impl`() {
        val outsideBypass = """
            package com.inscopelabs.buildengine.domain.usecase.write
            class BypassUseCase(private val gateway: GatewayApi) {
                suspend fun run(dto: PublishRequestDto) = gateway.publish(dto)
            }
        """.trimIndent()
        val outsideFindings = ArchitectureScanner.scanJournalBypass(
            "domain/usecase/write/BypassUseCase.kt",
            outsideBypass,
        )
        assertEquals(1, outsideFindings.size)
        assertEquals("journal_bypass:outside_executor", outsideFindings.single().rule)

        val insideUnguarded = """
            package com.inscopelabs.buildengine.data.repository
            class OperationRepositoryImpl(private val gateway: GatewayApi) {
                suspend fun publishDirect(dto: PublishRequestDto) {
                    gateway.publish(dto)
                }
            }
        """.trimIndent()
        val insideFindings = ArchitectureScanner.scanJournalBypass(
            "data/repository/OperationRepositoryImpl.kt",
            insideUnguarded,
        )
        assertEquals(1, insideFindings.size)
        assertEquals("journal_bypass:not_in_executeOperation", insideFindings.single().rule)
    }

    @Test
    fun `rule D allows journalled executeOperation block transport definitions and enrollment calls`() {
        val insideGuarded = """
            package com.inscopelabs.buildengine.data.repository
            class OperationRepositoryImpl(private val gateway: GatewayApi) {
                suspend fun publish(dto: PublishRequestDto) = executeOperation(intent) { key ->
                    gateway.publish(dto.copy(idempotencyKey = key))
                }
            }
        """.trimIndent()
        assertTrue(
            ArchitectureScanner.scanJournalBypass(
                "data/repository/OperationRepositoryImpl.kt",
                insideGuarded,
            ).isEmpty(),
        )

        val enrollmentCalls = """
            package com.inscopelabs.buildengine.domain.usecase.enrollment
            class EnrollDeviceUseCase(private val gatewayApi: GatewayApi) {
                suspend fun enrollNow() {
                    val ch = gatewayApi.enrollChallenge()
                    gatewayApi.enroll(ch)
                    gatewayApi.rotateKey(req)
                    gatewayApi.revokeKey(revokeReq)
                }
            }
        """.trimIndent()
        assertTrue(
            ArchitectureScanner.scanJournalBypass(
                "domain/usecase/enrollment/EnrollDeviceUseCase.kt",
                enrollmentCalls,
            ).isEmpty(),
        )
    }

    @Test
    fun `rule E detects lab package imports and HmacCapabilitySigner in production files`() {
        val badProductionFile = """
            package com.inscopelabs.buildengine.ui.publish
            import com.inscopelabs.buildengine.lab.ScenarioRunner
            class BadScreen(val signer: HmacCapabilitySigner)
        """.trimIndent()

        val findings = ArchitectureScanner.scanLabInProduction(
            "ui/publish/BadScreen.kt",
            badProductionFile,
        )
        assertEquals(2, findings.size)
        assertTrue(findings.any { it.rule == "lab_isolation:lab_package_reference" })
        assertTrue(findings.any { it.rule == "lab_isolation:hmac_signer_reference" })
    }

    @Test
    fun `rule E allows lab package files and clean production code`() {
        val labFile = """
            package com.inscopelabs.buildengine.lab
            import com.inscopelabs.buildengine.capability.signing.HmacCapabilitySigner
            class LabHelper(val signer: HmacCapabilitySigner)
        """.trimIndent()
        assertTrue(ArchitectureScanner.scanLabInProduction("lab/LabHelper.kt", labFile).isEmpty())

        val cleanProd = """
            package com.inscopelabs.buildengine.di
            import com.inscopelabs.buildengine.capability.signing.DeviceKeyCapabilitySigner
            object ProdModule {
                fun signer() = DeviceKeyCapabilitySigner()
            }
        """.trimIndent()
        assertTrue(ArchitectureScanner.scanLabInProduction("di/ProdModule.kt", cleanProd).isEmpty())
    }

    @Test
    fun `rule F logging detects interpolated nonce signature and toWire but allows literal words`() {
        val badLogs = """
            package com.inscopelabs.buildengine.sample
            fun logSecrets(nonce: String, capability: SignedCapability) {
                Logger.d("TAG", "nonce=${'$'}nonce")
                Logger.w("TAG", "wire=${'$'}{capability.toWire()}")
                Logger.i(
                    "TAG",
                    "sig=${'$'}{capability.signatureHex}",
                )
            }
        """.trimIndent()

        val findings = ArchitectureScanner.scanSensitiveLogging("BadLogs.kt", badLogs)
        assertEquals(3, findings.size)

        val cleanLogs = """
            package com.inscopelabs.buildengine.sample
            fun logSafe(idempotencyKey: String, statusCode: Int) {
                Logger.w("TAG", "Refused invalid signature or missing nonce; key=${'$'}idempotencyKey code=${'$'}statusCode")
                Logger.i("TAG", "Operator credential missing")
            }
        """.trimIndent()
        val cleanFindings = ArchitectureScanner.scanSensitiveLogging("CleanLogs.kt", cleanLogs)
        assertTrue("Expected zero false positives for literal words: $cleanFindings", cleanFindings.isEmpty())
    }

    @Test
    fun `rule F persistence detects Entity with signature or nonce and ignores local vars in sibling class`() {
        val badEntity = """
            package com.inscopelabs.buildengine.data.local
            import androidx.room.Entity
            @Entity(tableName = "bad_table")
            data class BadEntity(
                val id: String,
                val signature: String,
            ) {
                var capabilityWire: String = ""
            }
        """.trimIndent()

        val findings = ArchitectureScanner.scanRoomEntities("BadEntity.kt", badEntity)
        assertEquals(2, findings.size)
        assertTrue(findings.any { it.matchText == "signature" })
        assertTrue(findings.any { it.matchText == "capabilityWire" })

        val cleanEntityWithSiblingHelper = """
            package com.inscopelabs.buildengine.data.local
            import androidx.room.Entity
            class HelperCipher {
                fun run() {
                    val secretKey = getKey()
                }
            }
            @Entity(tableName = "good_table")
            data class GoodEntity(
                val idempotencyKey: String,
                val encryptedAuditRef: String?,
            )
        """.trimIndent()

        val cleanFindings = ArchitectureScanner.scanRoomEntities("GoodEntity.kt", cleanEntityWithSiblingHelper)
        assertTrue("Expected zero findings on clean Entity + sibling cipher: $cleanFindings", cleanFindings.isEmpty())
    }

    @Test(expected = AssertionError::class)
    fun `ratchet baseline fails when a new unbaselined violation appears`() {
        val findings = listOf(
            ScanFinding(
                rule = "lab_isolation:lab_package_reference",
                source = "ui/publish/NewScreen.kt",
                line = 5,
                matchText = "import com.inscopelabs.buildengine.lab.Scenario",
                detail = "new violation",
            ),
        )
        ArchitectureScanner.verifyRatchetBaseline(
            guardName = "test",
            findings = findings,
            baseline = emptyList(),
        )
    }

    @Test(expected = AssertionError::class)
    fun `ratchet baseline fails when a baselined exception no longer exists`() {
        val staleBaseline = listOf(
            BaselineException(
                rule = "lab_isolation:lab_package_reference",
                sourceSuffix = "ui/lab/LabScreen.kt",
                matchText = "import com.inscopelabs.buildengine.lab.Scenario",
                justification = "test stale check",
                reference = "T3-601",
            ),
        )
        ArchitectureScanner.verifyRatchetBaseline(
            guardName = "test",
            findings = emptyList(),
            baseline = staleBaseline,
        )
    }
}
