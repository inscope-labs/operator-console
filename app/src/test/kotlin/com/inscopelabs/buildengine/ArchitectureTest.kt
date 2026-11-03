package com.inscopelabs.buildengine

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
}
