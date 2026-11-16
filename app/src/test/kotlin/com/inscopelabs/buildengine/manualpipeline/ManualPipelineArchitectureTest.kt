package com.inscopelabs.buildengine.manualpipeline

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Module-scoped architecture tests for the `manualpipeline` feature module.
 *
 * These extend the console's existing architecture suite (register item PR3-033) rather than
 * creating a parallel one. They encode the invariants the manual pipeline module has to
 * hold to:
 *
 * - the module's `domain` and `ui` layers never reach the host's internals or its `data` layer;
 * - the module never speaks HTTP itself (`core.network` is the only code that does);
 * - the module has no alternative execution channel (no shell, no local Git, no GitHub client);
 * - no capability, signature, nonce or credential is ever persisted.
 */
class ManualPipelineArchitectureTest {

    private val moduleDir: File = listOf(
        File("app/src/main/kotlin/com/inscopelabs/buildengine/manualpipeline"),
        File("src/main/kotlin/com/inscopelabs/buildengine/manualpipeline"),
        File("../app/src/main/kotlin/com/inscopelabs/buildengine/manualpipeline"),
    ).firstOrNull { it.exists() && it.isDirectory }
        ?: error("Could not locate the manualpipeline module source directory")

    private fun moduleFiles(): List<File> =
        moduleDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun relative(file: File): String =
        file.relativeTo(moduleDir).path.replace('\\', '/')

    private fun importsIn(file: File): List<String> =
        file.readLines()
            .filter { it.trimStart().startsWith("import ") }
            .map { it.trim().removePrefix("import ").trim() }

    @Test
    fun `the domain layer does not import host packages`() {
        val violations = moduleDir.resolve("domain")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                importsIn(file)
                    .filter { it.startsWith("com.inscopelabs.buildengine.") }
                    .filter { !it.startsWith("com.inscopelabs.buildengine.manualpipeline.") }
                    .map { "${relative(file)} imports $it" }
            }
            .toList()

        assertTrue(
            "The manualpipeline domain layer must stay independent of the host application:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun `the ui layer does not import host packages`() {
        val violations = moduleDir.resolve("ui")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                importsIn(file)
                    .filter { it.startsWith("com.inscopelabs.buildengine.") }
                    .filter { !it.startsWith("com.inscopelabs.buildengine.manualpipeline.") }
                    .map { "${relative(file)} imports $it" }
            }
            .toList()

        assertTrue(
            "The manualpipeline ui layer must depend on the module's domain layer only:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun `the ui layer does not reach the data layer`() {
        val violations = moduleDir.resolve("ui")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                importsIn(file)
                    .filter { it.startsWith("com.inscopelabs.buildengine.manualpipeline.data") }
                    .map { "${relative(file)} imports $it" }
            }
            .toList()

        assertTrue(
            "The manualpipeline ui layer must not import data implementations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun `the domain layer does not depend on ui or data`() {
        val violations = moduleDir.resolve("domain")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                importsIn(file)
                    .filter {
                        it.startsWith("com.inscopelabs.buildengine.manualpipeline.ui") ||
                            it.startsWith("com.inscopelabs.buildengine.manualpipeline.data")
                    }
                    .map { "${relative(file)} imports $it" }
            }
            .toList()

        assertTrue(
            "The manualpipeline domain layer must not depend on ui or data:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun `the module never speaks HTTP directly`() {
        val forbidden = listOf(
            "io.ktor",
            "okhttp3",
            "java.net.HttpURLConnection",
            "javax.net.ssl.HttpsURLConnection",
        )
        val violations = moduleFiles()
            .flatMap { file ->
                importsIn(file)
                    .filter { import -> forbidden.any { import.startsWith(it) } }
                    .map { "${relative(file)} imports $it" }
            }
            .toList()

        assertTrue(
            "Only core.network may speak HTTP (I-09):\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun `the module has no alternative execution channel`() {
        val patterns = mapOf(
            "runtime_exec" to Regex("""\bRuntime\.getRuntime\b"""),
            "process_exec" to Regex("""\.exec\s*\("""),
            "process_builder" to Regex("""\bProcessBuilder\b"""),
            "jgit" to Regex("""\borg\.eclipse\.jgit\b"""),
            "shell" to Regex("""/system/bin/sh"""),
        )
        val violations = moduleFiles()
            .flatMap { file ->
                val content = file.readText()
                patterns.filter { (_, regex) -> regex.containsMatchIn(content) }
                    .map { (name, regex) -> "${relative(file)} matches $name (${regex.find(content)?.value})" }
            }
            .toList()

        assertTrue(
            "The manualpipeline module must not execute shell commands or local Git (I-09):\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun `the module has no direct GitHub client or credential shape`() {
        val tokenPatterns = listOf(
            Regex("""\bgh[pousr]_[A-Za-z0-9]{36,}"""),
            Regex("""\bgithub_pat_[A-Za-z0-9_]{20,}"""),
            Regex("""-----BEGIN (?:[A-Z0-9 ]+ )?PRIVATE KEY-----"""),
        )
        val violations = moduleFiles()
            .flatMap { file ->
                val content = file.readText()
                tokenPatterns.filter { it.containsMatchIn(content) }
                    .map { "${relative(file)} contains a credential shape" }
            }
            .toList()

        assertTrue(
            "The manualpipeline module must hold no repository credential (ADR 0002):\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun `the module never calls the capability minter directly`() {
        // Capability minting is reachable only through the host's single
        // ConfirmAndMintCapabilityUseCase path (I-01, I-02).
        val violations = moduleFiles()
            .filter { it.readText().contains(".mint(") }
            .map { "${relative(it)} calls .mint(" }
            .toList()

        assertTrue(
            "The manualpipeline module must mint only through MintingPort:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun `no module record declares a capability signature nonce or credential`() {
        val sensitive = Regex("""(?i)(nonce|signature|capability|capabilityWire|privateKey|secret|token|credential|bearer)""")
        val violations = moduleFiles()
            .filter { it.readText().contains("@Entity") }
            .flatMap { file ->
                val content = file.readText()
                val property = Regex(
                    """(?:^|[,(\s;])(?:(?:private|internal|public|protected|override|const|lateinit)\s+)*(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)\b""",
                )
                property.findAll(content)
                    .map { it.groupValues[1] }
                    .filter { sensitive.containsMatchIn(it) }
                    .map { "${relative(file)} declares '$it'" }
                    .toList()
            }
            .toList()

        assertTrue(
            "Module records must never persist capability material (I-04):\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }
}
