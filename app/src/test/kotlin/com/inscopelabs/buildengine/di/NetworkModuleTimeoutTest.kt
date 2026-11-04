package com.inscopelabs.buildengine.di

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage 7: timeout budgets are explicit and ordered connect < socket < call.
 */
class NetworkModuleTimeoutTest {

    @Test
    fun `timeout budgets are positive and ordered`() {
        assertTrue(NetworkModule.CONNECT_TIMEOUT_MS > 0)
        assertTrue(NetworkModule.SOCKET_TIMEOUT_MS > NetworkModule.CONNECT_TIMEOUT_MS)
        assertTrue(NetworkModule.CALL_TIMEOUT_MS >= NetworkModule.SOCKET_TIMEOUT_MS)
    }

    @Test
    fun `timeout budgets match documented Stage 7 values`() {
        assertEquals(15_000L, NetworkModule.CONNECT_TIMEOUT_MS)
        assertEquals(30_000L, NetworkModule.SOCKET_TIMEOUT_MS)
        assertEquals(45_000L, NetworkModule.CALL_TIMEOUT_MS)
    }
}
