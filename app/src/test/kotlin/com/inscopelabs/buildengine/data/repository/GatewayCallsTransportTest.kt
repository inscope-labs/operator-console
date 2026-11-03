package com.inscopelabs.buildengine.data.repository

import com.inscopelabs.buildengine.core.network.ErrorMapper
import com.inscopelabs.buildengine.core.network.GatewayFailureState
import com.inscopelabs.buildengine.core.network.GatewayNotConfiguredException
import com.inscopelabs.buildengine.core.result.AppResult
import java.net.UnknownHostException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayCallsTransportTest {

    private val mapper = ErrorMapper()

    @Test
    fun notConfiguredBecomesFailureNotThrow() = runBlocking {
        val result = callGateway(mapper) {
            throw GatewayNotConfiguredException("not configured")
        }
        assertTrue(result is AppResult.Failure)
        result as AppResult.Failure
        assertEquals(GatewayFailureState.NOT_CONFIGURED.code, result.code)
    }

    @Test
    fun unknownHostBecomesUnreachableFailure() = runBlocking {
        val result = callGateway(mapper) {
            throw UnknownHostException("gateway.invalid")
        }
        assertTrue(result is AppResult.Failure)
        result as AppResult.Failure
        assertEquals(GatewayFailureState.UNREACHABLE.code, result.code)
    }
}
