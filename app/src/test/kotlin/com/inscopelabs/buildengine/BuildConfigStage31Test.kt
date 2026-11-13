package com.inscopelabs.buildengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verifies Stage 3.1 (T3-101, BD-2.4, BD-3.2, BD-3.3) build, manifest, and R8 configuration
 * invariants directly against repository files.
 */
class BuildConfigStage31Test {

    private fun resolveRepoFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("..", relativePath),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Could not locate $relativePath from ${File(".").absolutePath}")
    }

    @Test
    fun `libs versions and app gradle configure Room WorkManager AES-GCM without Play Integrity or SQLCipher`() {
        val toml = resolveRepoFile("gradle/libs.versions.toml").readText()
        val gradle = resolveRepoFile("app/build.gradle.kts").readText()

        assertTrue("Room dependencies missing from libs.versions.toml", toml.contains("androidx-room-runtime"))
        assertTrue("Room KTX missing from libs.versions.toml", toml.contains("androidx-room-ktx"))
        assertTrue("Room KSP compiler missing from libs.versions.toml", toml.contains("androidx-room-compiler"))
        assertTrue("WorkManager missing from libs.versions.toml", toml.contains("androidx-work-runtime-ktx"))
        assertTrue(
            "BD-3.2 Keystore AES-GCM note missing from libs.versions.toml",
            toml.contains("AES-GCM") && toml.contains("AndroidKeyStore"),
        )

        assertTrue("Room runtime missing from app/build.gradle.kts", gradle.contains("libs.androidx.room.runtime"))
        assertTrue("Room ktx missing from app/build.gradle.kts", gradle.contains("libs.androidx.room.ktx"))
        assertTrue("Room ksp missing from app/build.gradle.kts", gradle.contains("ksp(libs.androidx.room.compiler)"))
        assertTrue("Room schemaLocation missing from app/build.gradle.kts", gradle.contains("room.schemaLocation"))
        assertTrue("WorkManager missing from app/build.gradle.kts", gradle.contains("libs.androidx.work.runtime.ktx"))

        val combined = (toml + "\n" + gradle).lowercase()
        assertFalse("BD-2.4 violation: Play Integrity dependency must be removed", combined.contains("play.integrity"))
        assertFalse("BD-2.4 violation: Play Integrity artifact must be removed", combined.contains("com.google.android.play:integrity"))
        assertFalse("BD-3.2 violation: SQLCipher dependency must not be present", combined.contains("sqlcipher"))
    }

    @Test
    fun `manifest preserves two permissions allowBackup false and requires no extra WorkManager entries`() {
        val manifest = resolveRepoFile("app/src/main/AndroidManifest.xml").readText()
        val permissions = Regex("""<uses-permission\s+android:name="([^"]+)"""")
            .findAll(manifest)
            .map { it.groupValues[1] }
            .toList()

        assertEquals(
            listOf(
                "android.permission.INTERNET",
                "android.permission.USE_BIOMETRIC",
            ),
            permissions,
        )
        assertTrue(manifest.contains("""android:allowBackup="false""""))
        assertTrue(manifest.contains("""android:usesCleartextTraffic="false""""))
        assertFalse("Non-expedited WorkManager requires no foreground service declaration", manifest.contains("FOREGROUND_SERVICE"))
    }

    @Test
    fun `proguard rules cover Room WorkManager Hilt EntryPoint and data local serialization`() {
        val proguard = resolveRepoFile("app/proguard-rules.pro").readText()

        assertTrue(proguard.contains("com.inscopelabs.buildengine.data.local.**"))
        assertTrue(proguard.contains("kotlinx.serialization.KSerializer serializer(...)"))
        assertTrue(proguard.contains("androidx.room.RoomDatabase"))
        assertTrue(proguard.contains("com.inscopelabs.buildengine.data.local.OperationJournalState"))
        assertTrue(proguard.contains("androidx.work.ListenableWorker"))
        assertTrue(proguard.contains("OperationRecoveryWorker\$OperationRecoveryEntryPoint"))
    }

    @Test
    fun `exported Room schema v1 exists`() {
        val schema = resolveRepoFile(
            "app/schemas/com.inscopelabs.buildengine.data.local.OperationJournalDatabase/1.json",
        )
        assertTrue("Exported Room schema 1.json must exist", schema.isFile && schema.length() > 0L)
    }
}
