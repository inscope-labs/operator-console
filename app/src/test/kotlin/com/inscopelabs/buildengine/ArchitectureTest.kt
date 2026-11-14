package com.inscopelabs.buildengine

import com.inscopelabs.buildengine.architecture.ArchitectureScanner
import com.inscopelabs.buildengine.architecture.BaselineException
import com.inscopelabs.buildengine.architecture.ScanFinding
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArchitectureTest {

    @Test
    fun `mint is called only from ConfirmAndMintCapabilityUseCase and CapabilityMinter tests`() {
        val rootCandidates = listOf(
            File("app/src/main/kotlin"),
            File("src/main/kotlin"),
            File("../app/src/main/kotlin"),
        )
        val mainDir = rootCandidates.firstOrNull { it.exists() && it.isDirectory }
        requireNotNull(mainDir) { "Could not find main source directory in: $rootCandidates" }

        val allowedFiles = setOf(
            "CapabilityMinter.kt",
            "ConfirmAndMintCapabilityUseCase.kt",
        )

        val violations = mutableListOf<String>()

        mainDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                if (file.name !in allowedFiles) {
                    val content = file.readText()
                    if (content.contains(".mint(")) {
                        violations.add("${file.name} calls .mint()")
                    }
                }
            }

        assertTrue(
            "Architectural invariant: .mint() must only appear in ConfirmAndMintCapabilityUseCase.kt and CapabilityMinter.kt. Found violations: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun `A - no GitHub tokens AWS keys or PEM private key headers in main kotlin res or assets`() {
        val findings = mainScanTargets(includeResourcesAndAssets = true)
            .flatMap { (relPath, content) -> ArchitectureScanner.scanTokenPatterns(relPath, content) }

        assertTrue(
            "SECURITY: token or private-key shape detected in main source/resources:\n${formatFindings(findings)}",
            findings.isEmpty(),
        )
    }

    @Test
    fun `B - no shell or local Git execution in main kotlin res or assets`() {
        val findings = mainScanTargets(includeResourcesAndAssets = true)
            .flatMap { (relPath, content) -> ArchitectureScanner.scanShellAndGitExecution(relPath, content) }

        assertTrue(
            "Forbidden shell or Git execution pattern detected:\n${formatFindings(findings)}",
            findings.isEmpty(),
        )
    }

    @Test
    fun `D - no journalled gateway write call bypasses OperationRepositoryImpl executeOperation`() {
        // Note: key-management calls (enroll, rotateKey, revokeKey, enrollChallenge) from the
        // enrollment use cases are documented exclusions and not journalled operations.
        val findings = mainKotlinFiles()
            .flatMap { (relPath, content) -> ArchitectureScanner.scanJournalBypass(relPath, content) }

        assertTrue(
            "Journal bypass detected (privileged write must stay inside OperationRepositoryImpl.executeOperation):\n${formatFindings(findings)}",
            findings.isEmpty(),
        )
    }

    @Test
    fun `E - no lab package imports or HMAC signer references in production packages beyond ratchet baseline`() {
        val findings = mainKotlinFiles()
            .flatMap { (relPath, content) -> ArchitectureScanner.scanLabInProduction(relPath, content) }

        ArchitectureScanner.verifyRatchetBaseline(
            guardName = "Rule E (Lab / HmacCapabilitySigner isolation)",
            findings = findings,
            baseline = RULE_E_BASELINE,
        )
    }

    @Test
    fun `F1 - no Logger call interpolates nonce signature privateKey bearer credential or toWire`() {
        val findings = mainKotlinFiles()
            .flatMap { (relPath, content) -> ArchitectureScanner.scanSensitiveLogging(relPath, content) }

        assertTrue(
            "Sensitive expression interpolated in Logger call:\n${formatFindings(findings)}",
            findings.isEmpty(),
        )
    }

    @Test
    fun `F2 - no Room Entity class declares sensitive secret token or capability property`() {
        val findings = mainKotlinFiles()
            .flatMap { (relPath, content) -> ArchitectureScanner.scanRoomEntities(relPath, content) }

        assertTrue(
            "Sensitive property declared on Room @Entity class:\n${formatFindings(findings)}",
            findings.isEmpty(),
        )
    }

    private fun resolveMainRoot(): File {
        val candidates = listOf(
            File("app/src/main"),
            File("src/main"),
            File("../app/src/main"),
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("Could not find main source root in: $candidates")
    }

    private fun mainKotlinFiles(): List<Pair<String, String>> {
        val mainRoot = resolveMainRoot()
        val kotlinDir = File(mainRoot, "kotlin")
        require(kotlinDir.exists() && kotlinDir.isDirectory) { "Missing kotlin dir: $kotlinDir" }
        return collectReadableFiles(mainRoot, kotlinDir) { it.extension == "kt" }
    }

    private fun mainScanTargets(includeResourcesAndAssets: Boolean): List<Pair<String, String>> {
        val mainRoot = resolveMainRoot()
        val results = mutableListOf<Pair<String, String>>()
        results += mainKotlinFiles()
        if (includeResourcesAndAssets) {
            val resDir = File(mainRoot, "res")
            if (resDir.exists() && resDir.isDirectory) {
                results += collectReadableFiles(mainRoot, resDir) { it.extension in TEXT_EXTENSIONS }
            }
            val assetsDir = File(mainRoot, "assets")
            if (assetsDir.exists() && assetsDir.isDirectory) {
                results += collectReadableFiles(mainRoot, assetsDir) { it.extension in TEXT_EXTENSIONS }
            }
        }
        return results
    }

    private fun collectReadableFiles(
        baseRoot: File,
        dir: File,
        predicate: (File) -> Boolean,
    ): List<Pair<String, String>> = dir.walkTopDown()
        .filter { it.isFile && predicate(it) }
        .sortedBy { it.invariantSeparatorsPath }
        .map { file ->
            file.relativeTo(baseRoot).invariantSeparatorsPath to file.readText()
        }
        .toList()

    private fun formatFindings(findings: List<ScanFinding>): String =
        findings.joinToString("\n") { "  - [${it.rule}] ${it.source}:${it.line} (${it.matchText}) — ${it.detail}" }

    private companion object {
        val TEXT_EXTENSIONS = setOf(
            "kt", "java", "xml", "json", "txt", "md", "properties", "yml", "yaml", "cfg",
        )

        /**
         * Ratchet baseline for Rule E findings present in main sources on PR3-033 authoring
         * (HEAD SHA unverified — no .git in sandbox). Every entry is a finding, not an approval;
         * Stages 5.3 / 5.5 must eliminate or isolate these so this list shrinks to empty.
         */
        val RULE_E_BASELINE: List<BaselineException> = listOf(
            BaselineException(
                rule = "lab_isolation:hmac_signer_reference",
                sourceSuffix = "capability/signing/CapabilitySigner.kt",
                matchText = "HmacCapabilitySigner",
                justification = "present at workspace HEAD (SHA unverified; no .git in sandbox); operator decision pending (KDoc reference)",
                reference = "T3-601 / PR3-033, Stage 5.5",
            ),
            BaselineException(
                rule = "lab_isolation:hmac_signer_reference",
                sourceSuffix = "di/CryptoModule.kt",
                matchText = "HmacCapabilitySigner",
                justification = "present at workspace HEAD (SHA unverified; no .git in sandbox); operator decision pending (KDoc reference)",
                reference = "T3-601 / PR3-033, Stage 5.5",
            ),
            BaselineException(
                rule = "lab_isolation:lab_package_reference",
                sourceSuffix = "ui/lab/LabScreen.kt",
                matchText = "import com.inscopelabs.buildengine.lab.Scenario",
                justification = "present at workspace HEAD (SHA unverified; no .git in sandbox); operator decision pending",
                reference = "T3-601 / PR3-033, Stage 5.3/5.5",
            ),
            BaselineException(
                rule = "lab_isolation:lab_package_reference",
                sourceSuffix = "ui/lab/LabScreen.kt",
                matchText = "import com.inscopelabs.buildengine.lab.ScenarioResult",
                justification = "present at workspace HEAD (SHA unverified; no .git in sandbox); operator decision pending",
                reference = "T3-601 / PR3-033, Stage 5.3/5.5",
            ),
            BaselineException(
                rule = "lab_isolation:lab_package_reference",
                sourceSuffix = "ui/lab/LabViewModel.kt",
                matchText = "import com.inscopelabs.buildengine.lab.ContractCheck",
                justification = "present at workspace HEAD (SHA unverified; no .git in sandbox); operator decision pending",
                reference = "T3-601 / PR3-033, Stage 5.3/5.5",
            ),
            BaselineException(
                rule = "lab_isolation:lab_package_reference",
                sourceSuffix = "ui/lab/LabViewModel.kt",
                matchText = "import com.inscopelabs.buildengine.lab.LabReport",
                justification = "present at workspace HEAD (SHA unverified; no .git in sandbox); operator decision pending",
                reference = "T3-601 / PR3-033, Stage 5.3/5.5",
            ),
            BaselineException(
                rule = "lab_isolation:lab_package_reference",
                sourceSuffix = "ui/lab/LabViewModel.kt",
                matchText = "import com.inscopelabs.buildengine.lab.Scenario",
                justification = "present at workspace HEAD (SHA unverified; no .git in sandbox); operator decision pending",
                reference = "T3-601 / PR3-033, Stage 5.3/5.5",
            ),
            BaselineException(
                rule = "lab_isolation:lab_package_reference",
                sourceSuffix = "ui/lab/LabViewModel.kt",
                matchText = "import com.inscopelabs.buildengine.lab.ScenarioCatalogue",
                justification = "present at workspace HEAD (SHA unverified; no .git in sandbox); operator decision pending",
                reference = "T3-601 / PR3-033, Stage 5.3/5.5",
            ),
            BaselineException(
                rule = "lab_isolation:lab_package_reference",
                sourceSuffix = "ui/lab/LabViewModel.kt",
                matchText = "import com.inscopelabs.buildengine.lab.ScenarioResult",
                justification = "present at workspace HEAD (SHA unverified; no .git in sandbox); operator decision pending",
                reference = "T3-601 / PR3-033, Stage 5.3/5.5",
            ),
            BaselineException(
                rule = "lab_isolation:lab_package_reference",
                sourceSuffix = "ui/lab/LabViewModel.kt",
                matchText = "import com.inscopelabs.buildengine.lab.ScenarioRunner",
                justification = "present at workspace HEAD (SHA unverified; no .git in sandbox); operator decision pending",
                reference = "T3-601 / PR3-033, Stage 5.3/5.5",
            ),
        )
    }
}

