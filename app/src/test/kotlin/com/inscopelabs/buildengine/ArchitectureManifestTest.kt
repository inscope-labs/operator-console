package com.inscopelabs.buildengine

import com.inscopelabs.buildengine.contract.ContractSchemaSupport
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PoC task 36 / MP BLD-04. The app asks Android for exactly two permissions, INTERNET and
 * USE_BIOMETRIC, and does not allow backup or cleartext traffic.
 *
 * Two levels are checked:
 * - the source manifest (`app/src/main/AndroidManifest.xml`), which is what the team writes;
 * - the merged debug manifest, which is what the installed app is actually granted: libraries
 *   add their own `<uses-permission>` entries when manifests merge.
 *
 * The merged manifest is produced by `processDebugManifest`, which `testDebugUnitTest` depends on
 * (see `app/build.gradle.kts`). Any permission a library adds beyond the two must be listed in
 * [MERGED_PERMISSION_BASELINE] with a reason: the ratchet fails on a NEW permission and on a stale
 * baseline entry, so the list cannot rot.
 */
class ArchitectureManifestTest {

    private val appDir: File = listOf(File("."), File("app"), File("../app"))
        .firstOrNull { File(it, "src/main/AndroidManifest.xml").isFile }
        ?: error("Could not locate the app module directory")

    private val permissionRegex = Regex("""<uses-permission(?:-sdk-23)?\s[^>]*?android:name\s*=\s*"([^"]+)"""")
    private val applicationTagRegex = Regex("""<application\b[^>]*>""", RegexOption.DOT_MATCHES_ALL)

    private fun permissionsIn(xml: String): Set<String> =
        permissionRegex.findAll(xml).map { it.groupValues[1] }.toSet()

    private fun attribute(tag: String, name: String): String? =
        Regex("""android:$name\s*=\s*"([^"]*)"""").find(tag)?.groupValues?.get(1)

    private fun locateMergedManifest(): File {
        val intermediates = File(appDir, "build/intermediates")
        val found = intermediates.walkTopDown()
            .filter { it.isFile && it.name == "AndroidManifest.xml" }
            .filter { path ->
                val p = path.invariantSeparatorsPath
                p.contains("/merged_manifest") && p.contains("/debug/")
            }
            .toList()
        // processDebugManifest output (merged_manifests/...) is the final one; fall back to the
        // main-manifest merge (merged_manifest/...) if it is the only one present.
        return found.firstOrNull { it.invariantSeparatorsPath.contains("/merged_manifests/") }
            ?: found.firstOrNull()
            ?: error(
                "No merged debug manifest under $intermediates. Run :app:processDebugManifest first " +
                    "(testDebugUnitTest depends on it in app/build.gradle.kts).",
            )
    }

    @Test
    fun `source manifest requests exactly INTERNET and USE_BIOMETRIC and disables backup and cleartext`() {
        val xml = File(appDir, "src/main/AndroidManifest.xml").readText()
        assertEquals(
            setOf("android.permission.INTERNET", "android.permission.USE_BIOMETRIC"),
            permissionsIn(xml),
        )
        val application = applicationTagRegex.find(xml)?.value ?: error("No <application> tag")
        assertEquals("false", attribute(application, "allowBackup"))
        assertEquals("false", attribute(application, "usesCleartextTraffic"))
    }

    @Test
    fun `merged manifest requests no permission beyond the two plus the recorded library baseline`() {
        val xml = locateMergedManifest().readText()
        val allowed = setOf("android.permission.INTERNET", "android.permission.USE_BIOMETRIC")
        val extra = permissionsIn(xml) - allowed

        ContractSchemaSupport.verifyRatchet(
            checkName = "Merged manifest permissions (BLD-04)",
            actualDiscrepancies = extra.associateWith { "added to the merged manifest by a library" },
            baseline = MERGED_PERMISSION_BASELINE,
        )

        val application = applicationTagRegex.find(xml)?.value ?: error("No <application> tag in merged manifest")
        assertEquals("allowBackup in the merged manifest", "false", attribute(application, "allowBackup"))
        assertEquals(
            "usesCleartextTraffic in the merged manifest",
            "false",
            attribute(application, "usesCleartextTraffic"),
        )
        assertTrue("Merged manifest must still declare INTERNET", permissionsIn(xml).contains("android.permission.INTERNET"))
    }

    private companion object {
        /** Permissions a library adds on merge, each with the reason it is tolerated. */
        val MERGED_PERMISSION_BASELINE: List<ContractSchemaSupport.RatchetEntry> = emptyList()
    }
}
