package com.inscopelabs.buildengine.data.local

import android.content.Context
import com.inscopelabs.buildengine.data.local.prefs.PreferencesKeys
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The credential refusal policy of ADR 0002.
 *
 * ADR 0002 says the rule is mechanically checkable: *"There is no code path that
 * accepts a GitHub token. No class, no preference key, no request field."* These
 * tests are the mechanical check for the part of that rule this stage owns — the
 * code path and the preference keys.
 *
 * The store itself needs a `Context` and an Android Keystore, so its storage half
 * is an instrumented concern. The *policy* half — the part the ADR actually cares
 * about — is pure, and pure is testable on the JVM.
 */
class OperatorCredentialStoreTest {

    private val store = OperatorCredentialStore(mockk<Context>(relaxed = true))

    // --- The refusal ------------------------------------------------------------

    @Test
    fun `save refuses a GitHub personal access token`() {
        assertRefused("ghp_" + "a".repeat(36))
    }

    @Test
    fun `save refuses a GitHub OAuth token`() {
        assertRefused("gho_" + "b".repeat(36))
    }

    @Test
    fun `save refuses a fine-grained personal access token`() {
        assertRefused("github_pat_" + "c".repeat(22) + "_" + "d".repeat(59))
    }

    @Test
    fun `save refuses an AWS access key id`() {
        assertRefused("AKIA" + "E".repeat(16))
    }

    @Test
    fun `save refuses a repository credential embedded in a longer value`() {
        // The match is a search, not an anchored match: the cost of a false refusal
        // is one confused operator, and the cost of a false acceptance is the
        // crown jewel.
        assertRefused("Bearer ghp_" + "a".repeat(36))
    }

    @Test
    fun `save refuses an empty credential`() {
        // An empty bearer header is worse than no header at all.
        assertRefused("")
    }

    @Test
    fun `save refuses a blank credential`() {
        assertRefused("   ")
    }

    // --- What is not refused ----------------------------------------------------

    @Test
    fun `a gateway-scoped credential is not refused`() {
        assertFalse(OperatorCredentialStore.looksLikeRepositoryCredential("gw_op_7f3c9b1e5d2a7046"))
    }

    @Test
    fun `an opaque credential containing the letters ghp is not refused`() {
        // Short, and not the shape of a token. The shapes are the gate, not the
        // letters.
        assertFalse(OperatorCredentialStore.looksLikeRepositoryCredential("ghp-not-a-token"))
    }

    @Test
    fun `the policy recognises the shapes the build engine's scanner detects`() {
        assertTrue(OperatorCredentialStore.looksLikeRepositoryCredential("ghp_" + "a".repeat(36)))
        assertTrue(
            OperatorCredentialStore.looksLikeRepositoryCredential("github_pat_" + "x".repeat(59))
        )
    }

    // --- The preference keys ----------------------------------------------------

    @Test
    fun `no preference key names a repository credential`() {
        // ADR 0002: "No class, no preference key, no request field." A key for a
        // repository credential would have to exist in this object to be used.
        val forbidden = listOf("github", "token", "pat", "oauth", "password")
        val keys = PreferencesKeys.SECRET_KEYS + PreferencesKeys.PLAIN_KEYS
        for (key in keys) {
            for (needle in forbidden) {
                assertFalse(
                    "preference key '$key' names a repository credential",
                    key.contains(needle, ignoreCase = true),
                )
            }
        }
    }

    @Test
    fun `the operator credential is the only secret preference key`() {
        assertEquals(setOf(PreferencesKeys.OPERATOR_CREDENTIAL), PreferencesKeys.SECRET_KEYS)
    }

    private fun assertRefused(credential: String) {
        val thrown = runCatching { store.save(credential) }.exceptionOrNull()
        assertTrue(
            "expected '$credential' to be refused, got $thrown",
            thrown is IllegalArgumentException,
        )
    }
}
