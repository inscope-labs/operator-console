package com.inscopelabs.buildengine.manualpipeline.domain

import com.inscopelabs.buildengine.manualpipeline.domain.error.FailureCategoryMapper
import com.inscopelabs.buildengine.manualpipeline.domain.error.ManualPipelineException
import com.inscopelabs.buildengine.manualpipeline.domain.model.FailureCategory
import java.io.IOException
import java.net.UnknownHostException
import javax.net.ssl.SSLPeerUnverifiedException
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Failure-vocabulary tests for the manual pipeline module.
 *
 * The console's failure vocabulary is fixed (operator-guide.md §4.3): NOT_CONFIGURED,
 * UNREACHABLE, TLS_PINNING, AUTH, STALE, POLICY_REFUSED. A gateway refusal must never be
 * flattened into a generic failure, and an unrecognised failure must fail closed rather
 * than be guessed at (I-08).
 */
class FailureCategoryMapperTest {

    // -- gateway code -> category --

    @Test
    fun `authentication codes map to AUTH`() {
        assertEquals(FailureCategory.AUTH, FailureCategoryMapper.fromGatewayCode("UNKNOWN_KID"))
        assertEquals(FailureCategory.AUTH, FailureCategoryMapper.fromGatewayCode("BAD_SIGNATURE"))
        assertEquals(FailureCategory.AUTH, FailureCategoryMapper.fromGatewayCode("NOT_ENROLLED"))
        assertEquals(FailureCategory.AUTH, FailureCategoryMapper.fromGatewayCode("AUTH_TIER_NOT_ALLOWED"))
    }

    @Test
    fun `stale codes map to STALE`() {
        assertEquals(FailureCategory.STALE, FailureCategoryMapper.fromGatewayCode("HEAD_DRIFT"))
        assertEquals(FailureCategory.STALE, FailureCategoryMapper.fromGatewayCode("HEAD_MOVED"))
        assertEquals(FailureCategory.STALE, FailureCategoryMapper.fromGatewayCode("CAPABILITY_EXPIRED"))
    }

    @Test
    fun `conflict codes map to CONFLICT`() {
        assertEquals(FailureCategory.CONFLICT, FailureCategoryMapper.fromGatewayCode("NONCE_REPLAYED"))
        assertEquals(FailureCategory.CONFLICT, FailureCategoryMapper.fromGatewayCode("IDEMPOTENCY_KEY_REUSED"))
        assertEquals(FailureCategory.CONFLICT, FailureCategoryMapper.fromGatewayCode("RECONCILE_REQUIRED"))
    }

    @Test
    fun `policy and guard codes map to POLICY_REFUSED`() {
        assertEquals(FailureCategory.POLICY_REFUSED, FailureCategoryMapper.fromGatewayCode("HALT_ACTIVE"))
        assertEquals(FailureCategory.POLICY_REFUSED, FailureCategoryMapper.fromGatewayCode("GUARD_FAILED"))
        assertEquals(FailureCategory.POLICY_REFUSED, FailureCategoryMapper.fromGatewayCode("DENIED_PATH"))
    }

    @Test
    fun `seal refusals map to POLICY_REFUSED and never to a generic failure`() {
        // The console only ever sees the seal indirectly: a SET_DONE is refused with
        // SEAL_NOT_ATTESTED (409) or SEAL_FACT_UNAVAILABLE (503). An unavailable seal fact
        // is a refusal, never an assumption (I-08).
        assertEquals(FailureCategory.POLICY_REFUSED, FailureCategoryMapper.fromGatewayCode("SEAL_NOT_ATTESTED"))
        assertEquals(FailureCategory.POLICY_REFUSED, FailureCategoryMapper.fromGatewayCode("SEAL_FACT_UNAVAILABLE"))
    }

    @Test
    fun `an illegal transition maps to POLICY_REFUSED`() {
        assertEquals(FailureCategory.POLICY_REFUSED, FailureCategoryMapper.fromGatewayCode("TRANSITION_NOT_ALLOWED"))
    }

    @Test
    fun `a timeout maps to UNREACHABLE`() {
        assertEquals(FailureCategory.UNREACHABLE, FailureCategoryMapper.fromGatewayCode("TIMEOUT"))
    }

    @Test
    fun `transport codes map to UNREACHABLE and TLS_PINNING`() {
        assertEquals(FailureCategory.UNREACHABLE, FailureCategoryMapper.fromGatewayCode("NETWORK"))
        assertEquals(FailureCategory.TLS_PINNING, FailureCategoryMapper.fromGatewayCode("TLS_PINNING"))
        assertEquals(FailureCategory.NOT_CONFIGURED, FailureCategoryMapper.fromGatewayCode("NOT_CONFIGURED"))
    }

    @Test
    fun `an unrecognised gateway code fails closed to UNKNOWN`() {
        assertEquals(FailureCategory.UNKNOWN, FailureCategoryMapper.fromGatewayCode("SOMETHING_NEW"))
    }

    @Test
    fun `a null code with a not-configured message still classifies as NOT_CONFIGURED`() {
        assertEquals(
            FailureCategory.NOT_CONFIGURED,
            FailureCategoryMapper.fromGatewayCode(null, "the gateway base URL is not configured"),
        )
    }

    @Test
    fun `the message never overrides a known gateway code`() {
        // The gateway's code is the contract; a chatty message must not reclassify it.
        assertEquals(
            FailureCategory.AUTH,
            FailureCategoryMapper.fromGatewayCode("NOT_ENROLLED", "the host is not configured"),
        )
    }

    // -- throwable -> category --

    @Test
    fun `a module exception keeps the category it was raised with`() {
        assertEquals(
            FailureCategory.POLICY_REFUSED,
            FailureCategoryMapper.fromThrowable(ManualPipelineException.Validation("nope")),
        )
        assertEquals(
            FailureCategory.AUTH,
            FailureCategoryMapper.fromThrowable(ManualPipelineException.Auth("bad credential")),
        )
        assertEquals(
            FailureCategory.STALE,
            FailureCategoryMapper.fromThrowable(ManualPipelineException.Stale("head moved")),
        )
    }

    @Test
    fun `a DNS failure maps to UNREACHABLE`() {
        assertEquals(
            FailureCategory.UNREACHABLE,
            FailureCategoryMapper.fromThrowable(UnknownHostException("gateway.invalid")),
        )
    }

    @Test
    fun `an IO failure maps to UNREACHABLE`() {
        assertEquals(
            FailureCategory.UNREACHABLE,
            FailureCategoryMapper.fromThrowable(IOException("connection reset")),
        )
    }

    @Test
    fun `a TLS failure maps to TLS_PINNING`() {
        assertEquals(
            FailureCategory.TLS_PINNING,
            FailureCategoryMapper.fromThrowable(SSLPeerUnverifiedException("no pin matched")),
        )
    }

    @Test
    fun `an unclassifiable failure fails closed to UNKNOWN`() {
        assertEquals(FailureCategory.UNKNOWN, FailureCategoryMapper.fromThrowable(RuntimeException("?")))
        assertEquals(FailureCategory.UNKNOWN, FailureCategoryMapper.fromThrowable(null))
    }
}
