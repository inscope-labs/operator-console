package com.inscopelabs.buildengine.manualpipeline.domain

import com.inscopelabs.buildengine.manualpipeline.Fixtures
import com.inscopelabs.buildengine.manualpipeline.domain.error.GatewayRefusal
import com.inscopelabs.buildengine.manualpipeline.domain.model.Environment
import com.inscopelabs.buildengine.manualpipeline.domain.validation.EnvironmentIsolationValidator
import com.inscopelabs.buildengine.manualpipeline.domain.validation.HaltStateValidator
import com.inscopelabs.buildengine.manualpipeline.domain.validation.HeadFreshnessValidator
import com.inscopelabs.buildengine.manualpipeline.domain.validation.SealStateValidator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validator tests for the manual pipeline's precondition checks: seal, halt, branch-head
 * freshness and environment isolation.
 */
class ManualPipelineValidatorsTest {

    // -- SealStateValidator --

    private val sealValidator = SealStateValidator()

    @Test
    fun `a submission immutability refusal is a seal conflict`() {
        val result = sealValidator.check(
            lastRefusal = GatewayRefusal(code = "SUBMISSION_IMMUTABLE", message = "already committed"),
            existingSubmissionId = Fixtures.SUBMISSION_ID,
        )
        assertTrue(result.sealed)
    }

    @Test
    fun `a seal-not-attested refusal is a seal conflict`() {
        val result = sealValidator.check(
            lastRefusal = GatewayRefusal(code = "SEAL_NOT_ATTESTED", message = "no seal for this work order"),
        )
        assertTrue(result.sealed)
    }

    @Test
    fun `an unavailable seal fact is a refusal and never an assumption`() {
        val result = sealValidator.check(
            lastRefusal = GatewayRefusal(code = "SEAL_FACT_UNAVAILABLE", message = "gateway C unreachable"),
        )
        assertTrue(result.sealed)
    }

    @Test
    fun `re-uploading a different archive over an existing one is a seal conflict`() {
        val result = sealValidator.check(
            existingSubmissionId = Fixtures.SUBMISSION_ID,
            alreadyUploadedSha256 = Fixtures.ZIP_SHA256,
            candidateSha256 = "f".repeat(64),
        )
        assertTrue(result.sealed)
    }

    @Test
    fun `re-uploading the identical archive is not a seal conflict`() {
        val result = sealValidator.check(
            existingSubmissionId = Fixtures.SUBMISSION_ID,
            alreadyUploadedSha256 = Fixtures.ZIP_SHA256,
            candidateSha256 = Fixtures.ZIP_SHA256,
        )
        assertFalse(result.sealed)
    }

    @Test
    fun `no refusal and no prior upload means no seal conflict`() {
        assertFalse(sealValidator.check().sealed)
    }

    // -- HaltStateValidator --

    private val haltValidator = HaltStateValidator()

    @Test
    fun `a halted plan locks write actions`() {
        val result = haltValidator.validate(Fixtures.planStatus(halted = true))
        assertTrue(result.halted)
        assertTrue(result.reason.contains("halt", ignoreCase = true))
    }

    @Test
    fun `an active plan does not lock write actions`() {
        assertFalse(haltValidator.validate(Fixtures.planStatus(halted = false)).halted)
    }

    @Test
    fun `an unloaded plan status is not treated as halted`() {
        val result = haltValidator.validate(null)
        assertFalse(result.halted)
        assertTrue(result.reason.contains("not been loaded", ignoreCase = true))
    }

    // -- HeadFreshnessValidator --

    private val headValidator = HeadFreshnessValidator()

    @Test
    fun `a matching live head is fresh`() {
        val result = headValidator.validate(Fixtures.HEAD_SHA, Fixtures.HEAD_SHA)
        assertTrue(result is HeadFreshnessValidator.Result.Fresh)
    }

    @Test
    fun `a moved live head is drift`() {
        val result = headValidator.validate(Fixtures.HEAD_SHA, Fixtures.OTHER_HEAD_SHA)
        assertTrue(result is HeadFreshnessValidator.Result.Drifted)
    }

    @Test
    fun `a short head SHA is an invalid format, never drift`() {
        val result = headValidator.validate("abc123", Fixtures.HEAD_SHA)
        assertTrue(result is HeadFreshnessValidator.Result.InvalidFormat)
    }

    @Test
    fun `an invalid live head is an invalid format`() {
        val result = headValidator.validate(Fixtures.HEAD_SHA, "not-a-sha")
        assertTrue(result is HeadFreshnessValidator.Result.InvalidFormat)
    }

    @Test
    fun `isFullSha accepts only 40 hex characters`() {
        assertTrue(headValidator.isFullSha(Fixtures.HEAD_SHA))
        assertFalse(headValidator.isFullSha("abc"))
        assertFalse(headValidator.isFullSha(null))
    }

    // -- EnvironmentIsolationValidator --

    private val environmentValidator = EnvironmentIsolationValidator()

    @Test
    fun `matching environments are isolated and accepted`() {
        val result = environmentValidator.validate(
            Fixtures.authority(
                expectedEnvironment = Environment.STAGING,
                reportedEnvironment = Environment.STAGING,
            ),
        )
        assertTrue(result.matched)
    }

    @Test
    fun `a staging enrolment cannot be used against production`() {
        val result = environmentValidator.validate(
            Fixtures.authority(
                expectedEnvironment = Environment.STAGING,
                reportedEnvironment = Environment.PRODUCTION,
            ),
        )
        assertFalse(result.matched)
        assertTrue(result.reason.contains("mismatch", ignoreCase = true))
    }

    @Test
    fun `an unprobed environment is never silently accepted`() {
        val result = environmentValidator.validate(
            Fixtures.authority(
                expectedEnvironment = Environment.UNPROBED,
                reportedEnvironment = Environment.UNPROBED,
            ),
        )
        assertFalse(result.matched)
    }
}
