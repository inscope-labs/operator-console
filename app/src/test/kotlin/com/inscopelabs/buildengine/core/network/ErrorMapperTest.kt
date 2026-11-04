package com.inscopelabs.buildengine.core.network

import com.inscopelabs.buildengine.core.result.AppResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage 8 regression gates for transport and gateway refusal mapping.
 *
 * Ensures not-configured / unreachable stay distinct from HTTP refusals, and that
 * the four V5–V8 states (scope, replay, head drift, halt) never collapse together.
 */
class ErrorMapperTest {

    private val mapper = ErrorMapper()

    // -- Transport (Stage 1 / Stage 8) --

    @Test
    fun `mapNotConfigured uses not_configured code`() {
        val failure = mapper.mapNotConfigured()
        assertEquals(GatewayFailureState.NOT_CONFIGURED.code, failure.code)
        assertTrue(failure.message.contains("not configured", ignoreCase = true))
    }

    @Test
    fun `mapUnreachable uses unreachable code`() {
        val failure = mapper.mapUnreachable()
        assertEquals(GatewayFailureState.UNREACHABLE.code, failure.code)
        assertTrue(failure.message.contains("reached", ignoreCase = true))
    }

    @Test
    fun `not configured and unreachable are distinct codes`() {
        assertTrue(mapper.mapNotConfigured().code != mapper.mapUnreachable().code)
    }

    // -- V5–V8 primary refusals --

    @Test
    fun `scope refused maps BRANCH_NOT_TASK`() {
        assertEquals(
            GatewayFailureState.SCOPE_REFUSED,
            mapper.stateFor(ErrorMapper.GATEWAY_BRANCH_NOT_TASK, 403),
        )
    }

    @Test
    fun `replayed maps NONCE_REPLAYED`() {
        assertEquals(
            GatewayFailureState.REPLAYED,
            mapper.stateFor(ErrorMapper.GATEWAY_NONCE_REPLAYED, 409),
        )
    }

    @Test
    fun `head drifted maps HEAD_DRIFT`() {
        assertEquals(
            GatewayFailureState.HEAD_DRIFTED,
            mapper.stateFor(ErrorMapper.GATEWAY_HEAD_DRIFT, 409),
        )
    }

    @Test
    fun `halt active maps HALT_ACTIVE`() {
        assertEquals(
            GatewayFailureState.HALT_ACTIVE,
            mapper.stateFor(ErrorMapper.GATEWAY_HALT_ACTIVE, 423),
        )
    }

    @Test
    fun `replay and head drift are distinct despite shared 409 status`() {
        val replay = mapper.stateFor(ErrorMapper.GATEWAY_NONCE_REPLAYED, 409)
        val drift = mapper.stateFor(ErrorMapper.GATEWAY_HEAD_DRIFT, 409)
        assertEquals(GatewayFailureState.REPLAYED, replay)
        assertEquals(GatewayFailureState.HEAD_DRIFTED, drift)
        assertTrue(replay != drift)
    }

    // -- map() with body --

    @Test
    fun `map prefers gateway code over status`() {
        val body = """{"code":"HEAD_DRIFT","message":"branch moved"}"""
        val failure = mapper.map(409, body)
        assertEquals(GatewayFailureState.HEAD_DRIFTED.code, failure.code)
        assertTrue(failure.message.contains("HEAD_DRIFT"))
        assertTrue(failure.message.contains("branch moved"))
    }

    @Test
    fun `map falls back to status when body is empty`() {
        val failure = mapper.map(401, null)
        assertEquals(GatewayFailureState.AUTHENTICATION.code, failure.code)
    }

    @Test
    fun `map falls back to status when body is not JSON`() {
        val failure = mapper.map(502, "<html>bad gateway</html>")
        assertEquals(GatewayFailureState.GATEWAY_ERROR.code, failure.code)
    }

    @Test
    fun `unrecognised gateway code is unknown not guessed`() {
        assertEquals(
            GatewayFailureState.UNKNOWN,
            mapper.stateFor("SOME_FUTURE_CODE", 400),
        )
    }

    // -- Status-only fallbacks --

    @Test
    fun `status 403 without body is scope refused class`() {
        assertEquals("scope_refused", mapper.mapStatusCode(403))
    }

    @Test
    fun `status 409 without body is conflict class not a specific V-code`() {
        assertEquals("conflict", mapper.mapStatusCode(409))
    }

    @Test
    fun `status 423 without body is halt active`() {
        assertEquals("halt_active", mapper.mapStatusCode(423))
    }

    @Test
    fun `protocol failure is unknown`() {
        val failure = mapper.mapProtocolFailure("unexpected shape")
        assertEquals(GatewayFailureState.UNKNOWN.code, failure.code)
        assertEquals("unexpected shape", failure.message)
    }

    @Test
    fun `environment mismatch stays distinct`() {
        assertEquals(
            GatewayFailureState.ENVIRONMENT_MISMATCH,
            mapper.stateFor(ErrorMapper.GATEWAY_ENVIRONMENT_MISMATCH, 403),
        )
    }
}
